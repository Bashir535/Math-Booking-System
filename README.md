# Booking System

Milestone 1: a math tutoring appointment system skeleton using Java 21, Spring Boot, PostgreSQL, and SQL through JDBC.

## Included in this milestone

- Controller → Service → Repository layers and JSON DTOs.
- Five required tables: users, providers, services, availability_slots, appointments.
- Startup `schema.sql` and `seed.sql`, including the database double-booking guard.
- Two database-backed read endpoints: home catalog and available slots.
- PostgreSQL integration tests and local database setup.

There is no implemented frontend or login/booking workflow in this milestone. React is the planned frontend for later work. 

## Run locally

Requirements: JDK 21 and running Docker Desktop. The Maven wrapper is included. 

If `.env` does not exist, copy `.env.example` to `.env` and choose a local `DB_PASSWORD`. Do not commit `.env`.

From the project directory:

```sh
docker compose up -d --wait
set -a
source .env
set +a
./mvnw spring-boot:run
```


View the JSON responses in a browser, Postman, or curl:

```sh
curl http://localhost:8090/api/home
curl http://localhost:8090/api/slots
```

| Endpoint | Response |
|---|---|
| `GET /api/home` | Project title, time zone, tutors, and tutoring services |
| `GET /api/slots` | List of future, available tutoring slots |

Both endpoints read PostgreSQL through the application layers and return explicit DTOs. No filtering or pagination is implemented yet. There is no HTML page at `/`.

Stop Spring Boot with Ctrl+C and PostgreSQL with `docker compose stop`. Database data remains in the named Docker volume.

## Configuration

| Variable | Default / requirement |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/booking_system` |
| `DB_USERNAME` | `booking` |
| `DB_PASSWORD` | Required local database password |
| `PORT` | `8090` |
| `DB_PORT` | Compose host port, default `5432`; update `DB_URL` if changed |


Startup loads `schema.sql` followed by `seed.sql`. A database contains two tutors, three subjects, one sample student, and 28 slots over the next seven days. Repeated startup preserves existing rows and adds missing sample slots for the coming week. Schema changes will require migrations later, `CREATE TABLE IF NOT EXISTS` does not alter existing tables.

Sample accounts contain hashes of random passwords where plaintext is not retained. These are database fixtures, not usable login accounts. Authentication is future work.


## Project structure

```text
src/main/java/com/example/bookingsystem/
  controller/
  service/
  repository/
  dto/
src/main/resources/
  application.properties
  schema.sql
  seed.sql
src/test/java/com/example/bookingsystem/
```

