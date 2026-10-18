#!/usr/bin/env bash
# Fills drive_db with fake drives and applications so the analytics page and the benchmark have
# something to chew on. Every fake row is marked, so --clean removes exactly those and nothing else.
#
# Usage:  ./scripts/seed-analytics.sh [applications] [drives] [students]
#         ./scripts/seed-analytics.sh --clean
# Default: 100000 applications over 200 drives and 2000 students.
set -e
cd "$(dirname "$0")/.."
set -a; [ -f .env ] && . ./.env; set +a
PSQL=(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U "${POSTGRES_USER:-placepulse}" -d drive_db)

if [ "$1" = "--clean" ]; then
  "${PSQL[@]}" <<'SQL'
delete from applications where student_email like '%@seed.placepulse.test';
delete from drives where description = 'seed-data';
SQL
  echo "Removed all seeded rows."
  exit 0
fi

APPS=${1:-100000}; DRIVES=${2:-200}; STUDENTS=${3:-2000}
echo "Seeding $DRIVES drives and up to $APPS applications from $STUDENTS students..."

"${PSQL[@]}" <<SQL
insert into drives (id, company, role_title, description, location, package_lpa, min_cgpa, deadline, status, created_by, created_at)
select md5('seed-drive-' || i)::uuid,
       'Seed Co ' || i, 'SDE', 'seed-data', 'Remote',
       round((4 + random() * 26)::numeric, 1), 6.0,
       current_date + 30, 'OPEN', md5('seed-tpr')::uuid, now()
from generate_series(1, $DRIVES) i
on conflict do nothing;

insert into applications (id, drive_id, student_id, student_name, student_email, cgpa, course, batch, stage, applied_at, updated_at)
select gen_random_uuid(),
       md5('seed-drive-' || d)::uuid,
       md5('seed-student-' || s)::uuid,
       'Seed Student ' || s, 'student' || s || '@seed.placepulse.test',
       round((6 + random() * 4)::numeric, 2), 'MCA', '2027',
       case when r < .40 then 'APPLIED' when r < .65 then 'SHORTLISTED' when r < .75 then 'TEST'
            when r < .82 then 'INTERVIEW' when r < .90 then 'OFFER' when r < .97 then 'REJECTED' else 'DEBARRED' end,
       now() - random() * interval '14 days', now()
from (select 1 + floor(random() * $DRIVES)::int as d, 1 + floor(random() * $STUDENTS)::int as s, random() as r
      from generate_series(1, $APPS)) t
on conflict do nothing;

select count(*) as seeded_applications from applications where student_email like '%@seed.placepulse.test';
SQL
echo "Done. Remove it later with: ./scripts/seed-analytics.sh --clean"
