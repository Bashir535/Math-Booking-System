# Booking System

Milestone 2: A math tutoring appointment system using React, Java 21, Spring Boot, PostgreSQL, and SQL through JDBC.

## Included in this milestone

- Controller → Service → Repository layers and JSON DTOs.
- React frontend for students and tutors.
- Login with BCrypt passwords, server sessions, and role checks.
- Available sessions with tutor, subject, and date filters and pagination.
- Appointment booking, owner cancellation, and appointment history.
- Tutor availability management and appointment viewing.
- Transactions and database constraints to prevent double booking.
- Unit and PostgreSQL integration tests, including two simultaneous booking attempts.

## Run locally

Requirements: JDK 21, Node.js 22.12 or newer, and running Docker Desktop. The Maven wrapper is included.

If `.env` does not exist, copy `.env.example` to `.env` and choose a local `DB_PASSWORD`. Keep an existing `.env` file. Do not commit `.env`.

From the project directory:

```sh
docker compose up -d --wait
set -a
source .env
set +a
```

For the first login setup, choose a sample account password of at least 12 characters. Run these commands in macOS zsh before starting the backend:

```sh
read -s 'BOOKING_DEMO_PASSWORD?Choose a sample account password: '
export BOOKING_DEMO_PASSWORD
```

Start the backend:

```sh
./mvnw spring-boot:run
```

The sample accounts are `alex.student`, `maya.chen`, and `daniel.reyes`. Alex is a student; Maya and Daniel are tutors. All three use the password you chose. On later starts, run `unset BOOKING_DEMO_PASSWORD` before starting the backend to keep their saved passwords.

In a second terminal, from the project directory:

```sh
cd frontend
npm ci
npm run dev
```

Open the URL printed by Vite, usually **http://localhost:5173**. The backend runs on port **8090**. Keep both terminals running.

| Endpoint | Response |
|---|---|
| `GET /api/home` | Tutors and tutoring subjects |
| `GET /api/slots` | Filtered and paginated available sessions |
| `POST /api/auth/login` | Signs in and establishes the authenticated session |
| `POST /api/auth/logout` | Ends the session |
| `GET /api/customer/appointments` | The student's appointments |
| `POST /api/customer/appointments` | Books an appointment |
| `DELETE /api/customer/appointments/{id}` | Cancels the student's own future appointment |
| `GET /api/provider/appointments` | Appointments booked with the tutor |
| `POST /api/provider/slots` | Adds tutor availability |
| `DELETE /api/provider/slots/{id}` | Removes the tutor's unbooked availability |

The React frontend handles login and API requests. Protected endpoints check the account's role, and write requests require a CSRF token from `GET /api/auth/session`.

Run tests from the project directory with Docker running:

```sh
./mvnw clean verify
```

Stop the backend and frontend with Ctrl+C and PostgreSQL with `docker compose stop`. Database data remains in the named Docker volume.

## Configuration

| Variable | Default / requirement |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/booking_system` |
| `DB_USERNAME` | `booking` |
| `DB_PASSWORD` | Required local database password |
| `PORT` | `8090` |
| `DB_PORT` | Compose host port, default `5432`; update `DB_URL` if changed |
| `BOOKING_DEMO_PASSWORD` | Optional password setup for the three sample accounts |

Startup loads `schema.sql` followed by `seed.sql`. A fresh database contains two tutors, three subjects, one sample student, and 28 slots over the next seven days. Repeated startup preserves existing rows and adds missing sample slots without reopening removed slots.

Passwords are stored as BCrypt hashes. Booking uses a transaction and a slot row lock, with a unique index to prevent multiple active appointments for the same slot. Cancellation preserves appointment history.

## Project structure

```text
frontend/
  src/
    main.jsx
    style.css
src/main/java/com/example/bookingsystem/
  config/
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
