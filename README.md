# Booking System

Milestone 3: A math tutoring appointment system using React, Java 21, Spring Boot, PostgreSQL, and SQL, with notification integration, monitoring, Docker, and continuous integration.

## Included in this milestone

- Controller → Service → Repository layers and JSON DTOs.
- React frontend, login, role checks, and student and tutor booking workflows.
- Mock booking and cancellation notifications sent through an HTTP Gateway.
- Notifications queued with the appointment change and sent after the transaction commits.
- Notification timeouts, retries, duplicate protection, and recovery after failure.
- INFO, WARN, and ERROR logs for bookings, notifications, and database failures.
- Database health checks and booking and notification metrics.
- Docker setup for the frontend, backend, and PostgreSQL.
- GitHub Actions workflow that tests and builds the project on pushes and pull requests.
- 35 unit and integration tests, including concurrent booking and notification failure scenarios.

## Run locally

Requirements: Running Docker Desktop. Docker builds React and Java 21 inside containers, so local Java and Node installations are not needed to run the app. Local backend tests require JDK 21; the Maven wrapper is included.

From the main project directory, copy `.env.example` to `.env` if `.env` does not already exist. Set `DB_PASSWORD` and `NOTIFICATION_API_KEY`. Keep the existing database password if you already have a database volume. Do not commit `.env`.

For the first login setup on a fresh database, choose a sample account password of at least 12 characters. Run these commands in macOS zsh before starting the app:

```sh
read -s 'BOOKING_DEMO_PASSWORD?Choose a sample account password: '
export BOOKING_DEMO_PASSWORD
```

Start the app and database:

```sh
docker compose up -d --build --wait
```

Open **http://localhost:8090**. React and Spring Boot run together in the app container. No separate frontend terminal is needed. If port 8090 is occupied, set `APP_PORT=18090` in `.env` and open **http://localhost:18090** instead.

The sample accounts are `alex.student`, `maya.chen`, and `daniel.reyes`. Alex is a student; Maya and Daniel are tutors. All three use the password you chose. Existing passwords are preserved when `BOOKING_DEMO_PASSWORD` is not set.

After the first password setup, remove the initializer from the app container:

```sh
unset BOOKING_DEMO_PASSWORD
docker compose up -d --force-recreate --wait app
```

On later starts, use `docker compose up -d --build --wait` without setting the sample password again.

| Endpoint | Response |
|---|---|
| `GET /api/home` | Tutors and tutoring subjects |
| `GET /api/slots` | Filtered and paginated available sessions |
| `GET /api/auth/session` | Current account and CSRF token |
| `POST /api/auth/login` | Signs in and establishes the authenticated session |
| `POST /api/auth/logout` | Ends the session |
| `GET /api/customer/appointments` | The student's appointments |
| `POST /api/customer/appointments` | Books an appointment and queues a confirmation |
| `DELETE /api/customer/appointments/{id}` | Cancels the student's own future appointment and queues a confirmation |
| `GET /api/provider/appointments` | Appointments booked with the tutor |
| `POST /api/provider/slots` | Adds tutor availability |
| `DELETE /api/provider/slots/{id}` | Removes the tutor's unbooked availability |
| `POST /internal/notifications` | Records a mock notification; requires `X-Notification-Key` |
| `GET /health` | Database connectivity: HTTP 200 when UP, 503 when DOWN |
| `GET /metrics` | Created and cancelled booking totals, pending and delivered notifications |

The React frontend handles login and API requests. Protected endpoints check the account's role, and API write requests require a CSRF token from `GET /api/auth/session`. The internal notification endpoint uses its separate shared key.

View health, metrics, and logs:

```sh
curl http://localhost:8090/health
curl http://localhost:8090/metrics
docker compose logs --tail=50 app
```

Use your configured `APP_PORT` in these URLs if you changed it. Notifications are simulated; no real email or SMS is sent. To test an outage, set `NOTIFICATION_STUB_AVAILABLE=false` in `.env` and recreate the app with `docker compose up -d --force-recreate --wait app`. Booking still succeeds while notifications remain pending. Restore the setting to `true` and recreate the app to resume delivery.

Run tests from the project directory with JDK 21 selected and Docker running:

```sh
./mvnw clean verify
```

GitHub Actions runs these tests, builds React, builds the Docker image, and saves test results on every push and pull request. It does not deploy a public website.

Stop the app and database with `docker compose stop`. Data remains in the named Docker volume.

## Configuration

| Variable | Default / requirement |
|---|---|
| `DB_URL` | Direct Java runs: `jdbc:postgresql://localhost:5432/booking_system`; Compose sets the internal database address |
| `DB_USERNAME` | `booking` |
| `DB_PASSWORD` | Required local database password |
| `PORT` | Direct Java server port, default `8090`; the Docker app uses `8090` internally |
| `APP_PORT` | Docker app host port, default `8090` |
| `DB_PORT` | PostgreSQL host port, default `5432`; change if occupied. Direct Java runs also need a matching `DB_URL` |
| `BOOKING_DEMO_PASSWORD` | Optional password setup for the three sample accounts |
| `NOTIFICATION_API_KEY` | Shared key for the mock notification endpoint; choose a value in `.env` |
| `NOTIFICATION_STUB_AVAILABLE` | `true`; set to `false` to simulate notification failure |
| `NOTIFICATION_STUB_DELAY_MS` | `0`; set to `2000` to simulate a slow response |
| `NOTIFICATION_URL` | Direct Java runs: defaults to `/internal/notifications` on the local server; can point to another service |
| `NOTIFICATION_TIMEOUT_MS` | Direct Java runs: `1000`; allowed range is 50 to 5000 ms |

Compose forwards the shared key and stub settings from `.env`. Custom notification URLs and timeouts require adding those variables to the app service's environment in `docker-compose.yml`.

Startup loads `schema.sql` followed by `seed.sql`. A fresh database contains two tutors, three subjects, one sample student, and 28 slots over the next seven days. Repeated startup preserves existing rows and adds missing sample slots without reopening removed slots.

Passwords are stored as BCrypt hashes. Booking uses a transaction and a slot row lock, with a unique index to prevent multiple active appointments for the same slot. Cancellation preserves appointment history.

The notification outbox and mock receipts are stored in PostgreSQL. The worker checks every two seconds, sends outside the database transaction, and tries up to three times per delivery round. Failed notifications become eligible again after 30 seconds. A 60 second lease allows recovery after a worker crash. Every retry uses the same notification ID to avoid duplicate receipts.

## Project structure

```text
.github/
  workflows/
    ci.yml
Dockerfile
.dockerignore
docker-compose.yml
frontend/
  src/
    main.jsx
    style.css
src/main/java/com/example/bookingsystem/
  config/
  controller/
    MockNotificationController.java
    SystemController.java
  service/
  repository/
  dto/
  notification/
    NotificationRequest.java
    NotificationQueue.java
    NotificationRepository.java
    NotificationGateway.java
    NotificationDispatcher.java
    NotificationWorker.java
src/main/resources/
  application.properties
  schema.sql
  seed.sql
src/test/java/com/example/bookingsystem/
  BookingServiceTests.java
  BookingSystemApplicationTests.java
  BookingWorkflowTests.java
  NotificationIntegrationTests.java
  SystemControllerTests.java
```
