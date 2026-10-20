// Mixed-workload load test for PlacePulse.
//
// What it does
//   setup:     logs in as a TPR, creates DRIVES drives, then creates STUDENTS real students
//              (register -> complete profile -> register for placements).
//   students:  every virtual user is one student. On its first iteration it applies to every
//              drive (this is the write path: DB insert, Kafka event, cache invalidation,
//              notification). After that it browses: drive list, own applications, notifications.
//   tpr:       2 virtual users keep reading the analytics page and applicant lists.
//
// Run it with scripts/loadtest.sh (uses Docker, so k6 need not be installed).

import http from 'k6/http'
import { check, sleep } from 'k6'

const AUTH = __ENV.AUTH_URL || 'http://localhost:8081'
const DRIVE = __ENV.DRIVE_URL || 'http://localhost:8082'
const NOTIFY = __ENV.NOTIFY_URL || 'http://localhost:8083'

const STUDENTS = parseInt(__ENV.STUDENTS || '50', 10)
const DRIVES = parseInt(__ENV.DRIVES || '10', 10)
const RAMP = parseInt(__ENV.RAMP || '20', 10) // seconds to reach full load
const HOLD = parseInt(__ENV.HOLD || '60', 10) // seconds at full load
const PASSWORD = 'LoadTest#1234'

export const options = {
  setupTimeout: '300s',
  scenarios: {
    students: {
      executor: 'ramping-vus',
      exec: 'student',
      startVUs: 0,
      stages: [
        { duration: `${RAMP}s`, target: STUDENTS },
        { duration: `${HOLD}s`, target: STUDENTS },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
    tpr: {
      executor: 'constant-vus',
      exec: 'tpr',
      vus: 2,
      duration: `${RAMP + HOLD}s`,
    },
  },
  thresholds: {
    // fail the run (non-zero exit) when the system is not healthy under load
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{kind:read}': ['p(95)<500'],
    'http_req_duration{kind:apply}': ['p(95)<1000'],
  },
}

const json = { headers: { 'Content-Type': 'application/json' } }
const bearer = (t) => ({ headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } })

function must(res, status, what) {
  if (res.status !== status) {
    throw new Error(`setup failed: ${what} returned ${res.status}: ${String(res.body).slice(0, 200)}`)
  }
  return res
}

export function setup() {
  const email = __ENV.TPR_EMAIL
  const password = __ENV.TPR_PASSWORD
  if (!email || !password) throw new Error('Set TPR_EMAIL and TPR_PASSWORD')

  const login = must(http.post(`${AUTH}/api/auth/login`, JSON.stringify({ email, password }), json), 200, 'TPR login')
  const tprToken = login.json('token')
  if (login.json('user.role') !== 'TPR') throw new Error('That account is not a TPR')

  const runId = Date.now().toString(36)
  const deadline = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10)

  const driveIds = []
  for (let i = 1; i <= DRIVES; i++) {
    const body = {
      company: `k6 Load Test ${runId} #${i}`,
      roleTitle: 'Load test role',
      location: 'Remote',
      packageLpa: 10,
      minCgpa: 0,
      deadline,
      eligibleBatches: [],
    }
    driveIds.push(must(http.post(`${DRIVE}/api/drives`, JSON.stringify(body), bearer(tprToken)), 201, 'create drive').json('id'))
  }

  const profile = JSON.stringify({
    phone: '9876543210',
    course: 'MCA',
    batch: '2027',
    cgpa: 8.5,
    tenthPercent: 85,
    twelfthPercent: 85,
    resumeUrl: 'https://example.com/resume.pdf',
    acknowledgeDebar: false,
  })

  // k6 numbers virtual users across ALL scenarios, and the 2 TPR users take ids too, so create
  // 2 spare students: then every student VU gets its own student, with no modulo and no sharing
  const students = []
  for (let i = 1; i <= STUDENTS + 2; i++) {
    const reg = must(
      http.post(
        `${AUTH}/api/auth/register`,
        JSON.stringify({ email: `k6-${runId}-${i}@loadtest.placepulse.test`, password: PASSWORD, fullName: `K6 Student ${i}` }),
        json,
      ),
      201,
      'register student',
    )
    const token = reg.json('token')
    must(http.put(`${AUTH}/api/users/me/profile`, profile, bearer(token)), 200, 'save profile')
    must(http.post(`${AUTH}/api/users/me/placement-registration`, null, bearer(token)), 200, 'register for placements')
    students.push(token)
  }

  console.log(`setup done: ${driveIds.length} drives, ${students.length} students (${STUDENTS} active), run id ${runId}`)
  return { tprToken, driveIds, students, runId }
}

// one student per virtual user; module-level state is per VU
let applied = false
let failuresLogged = 0

export function student(data) {
  const token = data.students[__VU - 1]
  if (!token) throw new Error(`VU ${__VU} has no student (increase the spare students in setup)`)
  const auth = bearer(token)

  if (!applied) {
    applied = true
    for (const id of data.driveIds) {
      const res = http.post(`${DRIVE}/api/drives/${id}/apply`, null, { ...auth, tags: { kind: 'apply', name: 'apply' } })
      const ok = check(res, { 'apply 201': (r) => r.status === 201 })
      // say why, for the first few failures only, so the log stays readable
      if (!ok && failuresLogged++ < 5) console.error(`apply failed: HTTP ${res.status} ${String(res.body).slice(0, 160)}`)
    }
  }

  const drives = http.get(`${DRIVE}/api/drives`, { ...auth, tags: { kind: 'read', name: 'list drives' } })
  check(drives, { 'drives 200': (r) => r.status === 200 })

  const mine = http.get(`${DRIVE}/api/applications/mine`, { ...auth, tags: { kind: 'read', name: 'my applications' } })
  check(mine, { 'applications 200': (r) => r.status === 200 })

  const notes = http.get(`${NOTIFY}/api/notifications`, { ...auth, tags: { kind: 'read', name: 'notifications' } })
  check(notes, { 'notifications 200': (r) => r.status === 200 })

  sleep(0.3 + Math.random() * 0.7) // a person pauses between clicks
}

export function tpr(data) {
  const auth = bearer(data.tprToken)

  const a = http.get(`${DRIVE}/api/analytics`, { ...auth, tags: { kind: 'read', name: 'analytics' } })
  check(a, { 'analytics 200': (r) => r.status === 200 })

  const id = data.driveIds[Math.floor(Math.random() * data.driveIds.length)]
  const apps = http.get(`${DRIVE}/api/drives/${id}/applications`, { ...auth, tags: { kind: 'read', name: 'applicants' } })
  check(apps, { 'applicants 200': (r) => r.status === 200 })

  sleep(0.5 + Math.random())
}
