#!/usr/bin/env bash
# Loads a small, realistic demo dataset so the Analytics page (and screenshots) look like a real
# placement season: 8 drives from fictional companies, a 75-student MCA batch, believable
# CGPAs, stage funnels, a few offers, a few debarred students and a 14-day application curve.
#
# Everything is marked and deterministic, so running it again resets the demo data, and
# --clean removes exactly these rows. Your real drives and applications are never touched.
# The students are fake: nobody can log in as them.
#
# Usage:  ./scripts/seed-demo.sh
#         ./scripts/seed-demo.sh --clean
set -e
cd "$(dirname "$0")/.."
set -a; [ -f .env ] && . ./.env; set +a
PSQL=(docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U "${POSTGRES_USER:-placepulse}" -d drive_db)

# demo drives are the ones created by this fixed id; demo applications use this e-mail domain
CLEAN_SQL="
delete from applications where student_email like '%@demo.placepulse.test';
delete from drives where created_by = md5('demo-tpr')::uuid;
"

refresh_cache() {
  # the rows were written straight into Postgres, so tell the analytics cache to forget its copy
  docker compose exec -T redis redis-cli DEL placepulse:drive:analytics:v1 >/dev/null 2>&1 || true
}

if [ "$1" = "--clean" ]; then
  "${PSQL[@]}" -c "$CLEAN_SQL"
  refresh_cache
  echo "Removed the demo data."
  exit 0
fi

"${PSQL[@]}" <<SQL
$CLEAN_SQL

-- repeatable pseudo-random number in [0,1) from a text key
create function pg_temp.frac(text) returns float8 language sql immutable as
\$\$ select (abs(hashtext(\$1)::bigint) % 10000) / 10000.0 \$\$;

create temp table demo_drive (
  i int, company text, role text, descr text, loc text, lpa numeric, min_cgpa numeric,
  days_ago int, deadline_off int, phase text, closed boolean, apply_p float8);

insert into demo_drive values
 (1, 'Northwind Labs',        'Software Engineer I',        'Product engineering team building developer tooling. Round 1 online test, two technical interviews, one HR round.', 'Bengaluru', 12.0, 7.5, 13, -6, 'done',  true,  0.80),
 (2, 'Finverse Technologies', 'Backend Engineer',           'Payments backend in Java and Kafka. Online assessment followed by system design and a managerial round.',           'Gurugram',  14.5, 7.5, 11, -4, 'done',  true,  0.70),
 (3, 'Brightcart',            'Full Stack Developer',       'E-commerce platform on React and Node.js. Take-home task, then a technical and a culture interview.',               'Remote',     8.5, 7.0, 10, -3, 'done',  true,  0.85),
 (4, 'Quanta Systems',        'Associate Software Engineer','Enterprise software services. Aptitude test, group discussion and technical interview.',                              'Pune',       6.5, 6.5,  8,  3, 'mid',   false, 0.90),
 (5, 'Helix Cloud',           'Cloud and DevOps Engineer',  'Infrastructure team working with AWS, Terraform and Kubernetes. Online test and two interviews.',                    'Hyderabad', 10.0, 7.0,  6,  5, 'mid',   false, 0.75),
 (6, 'Orbit Payments',        'Software Engineer (SDE-1)',  'Core payments team. Coding rounds, a design round and a bar-raiser. Strong DSA expected.',                           'Bengaluru', 18.0, 8.0,  5,  4, 'late',  false, 0.65),
 (7, 'Lumen Analytics',       'Data Engineer',              'Pipelines and warehousing with Python, Spark and SQL. Online test, then technical interviews.',                      'Noida',      9.0, 7.0,  3,  9, 'early', false, 0.80),
 (8, 'Pixelforge Games',      'Software Developer',         'Gameplay tools and backend services in C# and Go. Portfolio review and two interviews.',                             'Remote',     7.5, 6.5,  1, 12, 'early', false, 0.70);

create temp table demo_student (s int, name text, email text, cgpa numeric);
insert into demo_student
select s,
       f.n || ' ' || l.n,
       lower(f.n) || '.' || lower(l.n) || '@demo.placepulse.test',
       round((6.5 + 2.9 * (pg_temp.frac('c1-' || s) + pg_temp.frac('c2-' || s)) / 2)::numeric, 2)
from generate_series(1, 75) s
join (select row_number() over () - 1 as k, n from unnest(array['Aarav','Ananya','Vivaan','Diya','Aditya','Ishita','Arjun','Kavya','Rohan','Meera','Karan','Sneha','Rahul','Priya','Vikram']) as n) f
  on f.k = (s - 1) % 15
join (select row_number() over () - 1 as k, n from unnest(array['Sharma','Verma','Gupta','Singh','Yadav','Mishra','Patel','Kumar','Tiwari','Pandey','Srivastava','Agarwal','Chauhan','Dubey','Saxena']) as n) l
  on l.k = ((s - 1) / 15 + 2 * ((s - 1) % 15)) % 15;

insert into drives (id, company, role_title, description, location, package_lpa, min_cgpa,
                    eligible_batches, deadline, status, created_by, created_at)
select md5('demo-drive-' || i)::uuid, company, role, descr, loc, lpa, min_cgpa, '2027',
       current_date + deadline_off, case when closed then 'CLOSED' else 'OPEN' end,
       md5('demo-tpr')::uuid, now() - days_ago * interval '1 day'
from demo_drive;

insert into applications (id, drive_id, student_id, student_name, student_email, cgpa, course, batch,
                          stage, applied_at, updated_at)
with cand as (
  select d.i, d.phase, d.days_ago, s.s, s.name, s.email, s.cgpa,
         s.cgpa + pg_temp.frac('n' || d.i || '-' || s.s) * 1.2 as score
  from demo_drive d
  join demo_student s on s.cgpa >= d.min_cgpa
  where pg_temp.frac('p' || d.i || '-' || s.s) < d.apply_p
), ranked as (
  select *, percent_rank() over (partition by i order by score desc) as pr from cand
), staged as (
  select *,
    case phase
      when 'done'  then case when pr < .15 then 'OFFER' else 'REJECTED' end
      when 'late'  then case when pr < .05 then 'OFFER' when pr < .20 then 'INTERVIEW' when pr < .35 then 'TEST'
                             when pr < .60 then 'SHORTLISTED' when pr < .75 then 'REJECTED' else 'APPLIED' end
      when 'mid'   then case when pr < .05 then 'INTERVIEW' when pr < .15 then 'TEST' when pr < .45 then 'SHORTLISTED'
                             when pr < .55 then 'REJECTED' else 'APPLIED' end
      else              case when pr < .20 then 'SHORTLISTED' else 'APPLIED' end
    end as stage,
    -- most applications arrive in the first days after a drive opens, then it tails off
    least(now() - interval '5 minutes',
          now() - days_ago * interval '1 day'
          + interval '1 day' * (power(pg_temp.frac('t' || i || '-' || s), 2) * least(days_ago, 4))
          + interval '1 hour' * (pg_temp.frac('h' || i || '-' || s) * 8)) as at
  from ranked
)
select md5('demo-app-' || i || '-' || s)::uuid, md5('demo-drive-' || i)::uuid, md5('demo-student-' || s)::uuid,
       name, email, cgpa, 'MCA', '2027', stage, at, at
from staged;

-- three students edited their profile mid-season, so their in-progress applications were debarred
update applications
   set debarred_from = stage, stage = 'DEBARRED', updated_at = now() - interval '1 day'
 where student_id in (md5('demo-student-12')::uuid, md5('demo-student-37')::uuid, md5('demo-student-58')::uuid)
   and stage in ('APPLIED','SHORTLISTED','TEST','INTERVIEW')
   and drive_id in (select id from drives where status = 'OPEN');

select count(*) as applications,
       count(*) filter (where stage = 'OFFER') as offers,
       count(distinct student_id) as students_applied,
       count(distinct student_id) filter (where stage = 'OFFER') as students_placed,
       count(*) filter (where stage = 'DEBARRED') as debarred
from applications where student_email like '%@demo.placepulse.test';
SQL

refresh_cache
echo "Demo data loaded. Open the Analytics page. Remove it later with: ./scripts/seed-demo.sh --clean"
