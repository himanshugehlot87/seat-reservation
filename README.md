# Seat Reservation System

A backend service for high-concurrency seat reservations, built with Java, Spring Boot, and PostgreSQL.

The system is designed to safely handle concurrent reservation requests for the same seats while preventing double booking, enforcing per-user seat limits, and supporting idempotent reservation requests.

## Tech Stack

- Java 17
- Spring Boot 4.1.1
- Spring Data JPA / Hibernate
- PostgreSQL
- Maven
- Docker & Docker Compose
- Spring Boot Actuator
- Micrometer / Prometheus
- Python for concurrency testing

## Key Features

- Create shows with configurable seats and ticket price
- Reserve one or multiple seats
- PostgreSQL row-level locking for concurrent reservations
- All-or-nothing reservation for multiple seats
- Prevents double booking under concurrent requests
- Per-user reservation limit
- Idempotency using `Idempotency-Key`
- Explicit reservation cancellation
- Authentication using Bearer token
- Proper HTTP error handling
- Health, liveness and readiness endpoints
- Prometheus-compatible metrics
- Structured logging with correlation IDs
- Dockerized application and PostgreSQL database
- Concurrent burst test script

## Concurrency Guarantee

The reservation flow uses PostgreSQL row-level locking with:

`SELECT ... FOR UPDATE`

Requested seats are locked in a deterministic order before their availability is checked and updated.

This ensures that when multiple requests attempt to reserve the same seat concurrently, only one transaction can successfully confirm the seat. Competing transactions receive a `409 Conflict` instead of causing a double booking or a `5xx` error.

## Architecture

The application follows a layered Spring Boot architecture:

```text
Client
  |
  v
Controller
  |
  v
Service
  |
  +--------------------+
  |                    |
  v                    v
Repositories        PostgreSQL
  |
  v
Entities
```

## Project Structure

```text
src/main/java/com/reservation/
├── config/
│   └── CorrelationIdFilter.java
├── controller/
│   ├── ShowController.java
│   └── ReservationController.java
├── dto/
│   ├── CreateShowRequest.java
│   ├── ShowResponse.java
│   ├── SeatResponse.java
│   ├── ReserveSeatsRequest.java
│   ├── ReservationResponse.java
│   └── CancellationResponse.java
├── entity/
│   ├── Show.java
│   ├── Seat.java
│   ├── Reservation.java
│   ├── ReservationSeat.java
│   ├── IdempotencyKey.java
│   ├── SeatStatus.java
│   └── ReservationStatus.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── SeatUnavailableException.java
│   ├── SeatNotFoundException.java
│   ├── ShowNotFoundException.java
│   ├── ReservationNotFoundException.java
│   ├── UnauthorizedException.java
│   ├── UnauthorizedCancellationException.java
│   ├── PerUserLimitExceededException.java
│   └── IdempotencyConflictException.java
├── repository/
│   ├── ShowRepository.java
│   ├── SeatRepository.java
│   ├── ReservationRepository.java
│   ├── ReservationSeatRepository.java
│   └── IdempotencyKeyRepository.java
├── service/
│   ├── ShowService.java
│   ├── ReservationService.java
│   ├── AuthService.java
│   ├── RequestHashService.java
│   └── ReservationMetrics.java
└── SeatReservationApplication.java
```

## Database Design

### Shows

```text
shows
-----
id
name
price_paise
per_user_limit
```

### Seats

```text
seats
-----
id
show_id
seat_number
status
```

A unique constraint is applied to:

```text
(show_id, seat_number)
```

Seat states:

```text
AVAILABLE
CONFIRMED
```

### Reservations

```text
reservations
------------
id
show_id
user_id
amount_paise
status
created_at
```

Reservation states:

```text
CONFIRMED
CANCELLED
```

### Reservation Seats

```text
reservation_seats
-----------------
id
reservation_id
seat_id
```

### Idempotency Keys

```text
idempotency_keys
----------------
id
user_id
show_id
idempotency_key
request_hash
reservation_id
created_at
```

A unique constraint is applied to:

```text
(user_id, show_id, idempotency_key)
```

## Reservation Flow

A reservation request follows this sequence:

```text
1. Validate show
        |
        v
2. Normalize and sort requested seats
        |
        v
3. Acquire user/show advisory transaction lock
        |
        v
4. Check idempotency key
        |
        v
5. Check per-user seat limit
        |
        v
6. Lock requested seat rows using FOR UPDATE
        |
        v
7. Check seat availability
        |
        v
8. Create reservation
        |
        v
9. Mark seats as CONFIRMED
        |
        v
10. Create reservation-seat mappings
        |
        v
11. Store idempotency record
        |
        v
12. Commit transaction
```

All operations are executed inside a database transaction.

If any step fails, the transaction is rolled back.

## Preventing Double Booking

The critical reservation query uses PostgreSQL row-level locking:

```sql
SELECT *
FROM seats
WHERE show_id = :showId
  AND seat_number IN (:seatNumbers)
ORDER BY seat_number
FOR UPDATE;
```

The requested seat rows are locked before checking or changing their status.

For example:

```text
User A                     User B
   |                          |
   | Reserve A1               | Reserve A1
   |                          |
   v                          v
Lock A1                    Waits
   |
   v
A1 -> CONFIRMED
   |
   v
Commit
                              |
                              v
                         Acquires A1 lock
                              |
                              v
                         A1 unavailable
                              |
                              v
                         409 Conflict
```

Therefore, concurrent requests cannot successfully confirm the same seat.

## Deterministic Lock Ordering

Requested seats are normalized, deduplicated, and sorted before acquiring locks.

For example:

```text
Request:
A3, A1, A2

Normalized:
A1, A2, A3
```

This ensures that concurrent multi-seat reservations acquire seat locks in a consistent order and reduces the possibility of deadlocks.

## All-or-Nothing Reservation

The system uses all-or-nothing semantics for multi-seat reservations.

For example, if the request is:

```json
{
  "seats": ["A1", "A2", "A3"]
}
```

and:

```text
A1 -> AVAILABLE
A2 -> AVAILABLE
A3 -> CONFIRMED
```

the complete request fails with:

```text
409 Conflict
```

No partial reservation is created.

The transaction is rolled back.

## Per-User Seat Limit

Each show has a configurable per-user seat limit.

The default value is:

```text
4 seats
```

The system checks:

```text
existing confirmed seats + requested seats
```

and rejects the request if the configured limit would be exceeded.

A PostgreSQL advisory transaction lock is used for the combination:

```text
user + show
```

This prevents concurrent requests from the same user for the same show from bypassing the limit.

The lock is scoped to the user/show combination rather than the entire show, allowing different users to reserve different seats concurrently.

## Idempotency

Reservation requests require an `Idempotency-Key` header.

The key is associated with:

```text
user + show + idempotency key
```

The request is also hashed using SHA-256.

If the same key is submitted again with the same request, the original reservation is returned.

No duplicate reservation is created.

If the same key is reused with a different request, the API returns:

```text
409 Conflict
```

Example:

```json
{
  "error": "Idempotency key was already used with a different request"
}
```

## Authentication

The reservation API obtains the user identity from the `Authorization` header.

Example:

```http
Authorization: Bearer user-123
```

The token value is used as the user identity for this take-home implementation.

The reservation request body does not contain a `user_id`.

This prevents the client from directly specifying another user's identity.

Missing or invalid authentication returns:

```text
401 Unauthorized
```

## API Endpoints

### Create Show

```http
POST /shows
Content-Type: application/json
```

Request:

```json
{
  "name": "friday-night",
  "seats": [
    "A1",
    "A2",
    "A3",
    "A4",
    "A5"
  ],
  "price_paise": 25000
}
```

Response:

```text
201 Created
```

### Get Show

```http
GET /shows/{showId}
```

The response contains show information, total seats, available seats, held seats, confirmed seats, and individual seat status.

The following invariant is maintained:

```text
totalSeats = availableSeats + heldSeats + confirmedSeats
```

The current implementation does not use temporary seat holds, so:

```text
heldSeats = 0
```

### Reserve Seats

```http
POST /shows/{showId}/reserve
Authorization: Bearer user-123
Idempotency-Key: reservation-001
Content-Type: application/json
```

Request:

```json
{
  "seats": [
    "A1",
    "A2"
  ]
}
```

Successful response:

```text
201 Created
```

Example:

```json
{
  "reservation_id": 1,
  "show_id": 1,
  "user_id": "user-123",
  "seats": [
    "A1",
    "A2"
  ],
  "amount_paise": 50000,
  "status": "CONFIRMED"
}
```

### Cancel Reservation

```http
POST /shows/reservations/{reservationId}/cancel
Authorization: Bearer user-123
```

Successful response:

```json
{
  "reservation_id": 1,
  "status": "CANCELLED",
  "message": "Reservation cancelled successfully"
}
```

Cancellation releases the reserved seats back to:

```text
AVAILABLE
```

Only the reservation owner can cancel the reservation.

## Error Handling

| Scenario | HTTP Status |
|---|---:|
| Reservation successful | 201 |
| Show not found | 404 |
| Reservation not found | 404 |
| Seat unavailable | 409 |
| Per-user limit exceeded | 409 |
| Idempotency conflict | 409 |
| Unauthorized cancellation | 403 |
| Missing/invalid authentication | 401 |
| Invalid request | 400 |
| Requested seat does not exist | 400 |

## Health Checks

```http
GET /actuator/health
```

Liveness:

```http
GET /actuator/health/liveness
```

Readiness:

```http
GET /actuator/health/readiness
```

These endpoints can be used by deployment platforms and container orchestration systems to determine application health.

## Metrics

Prometheus metrics are exposed through:

```http
GET /actuator/prometheus
```

Custom reservation metrics include:

```text
reservation_success_total
reservation_conflict_total
reservation_cancelled_total
```

## Structured Logging

The application uses structured JSON-style logging.

Log entries contain:

```text
timestamp
level
correlationId
logger
message
```

Every HTTP request receives an `X-Correlation-ID`.

If the client provides:

```http
X-Correlation-ID: abc-123
```

the same ID is returned in the response.

If no correlation ID is provided, the application generates one.

This allows requests to be traced through application logs.

## Cancellation and Seat Lifecycle

The current implementation uses explicit cancellation instead of temporary seat holds.

Seat lifecycle:

```text
AVAILABLE
    |
    v
CONFIRMED
    |
    v
CANCELLED
    |
    v
AVAILABLE
```

There is no `HELD` database state in the current implementation.

Therefore:

```text
heldSeats = 0
```

## Docker

The project includes:

```text
Dockerfile
docker-compose.yml
```

Docker Compose runs:

```text
PostgreSQL
    |
    v
Spring Boot Application
```

Start the complete application with:

```bash
docker compose up --build
```

Application:

```text
http://localhost:8080
```

PostgreSQL:

```text
localhost:5432
```

## Docker Configuration

The application uses environment variables for database configuration:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Docker Compose provides:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/seat_reservation
SPRING_DATASOURCE_USERNAME: postgres
SPRING_DATASOURCE_PASSWORD: postgres
```

## Local Development

Build the application:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Run locally:

```powershell
.\mvnw.cmd spring-boot:run
```

Or run the complete system using Docker:

```powershell
docker compose up --build
```

When Java source code changes, rebuild the JAR before rebuilding the Docker image:

```powershell
.\mvnw.cmd clean package -DskipTests
docker compose up --build
```

## Concurrency Testing

The project contains a Python burst test under:

```text
scripts/burst_test.py
```

The script sends concurrent reservation requests using Python `ThreadPoolExecutor`.

### Same Seat Test

Test configuration:

```text
100 concurrent requests
Same show
Same seat
Different users
```

Observed result:

```text
Total requests : 100
201 Created    : 1
409 Conflict   : 99
Other responses: 0
```

This demonstrates that only one concurrent request successfully reserved the same seat.

### Per-User Limit Test

A separate concurrency scenario used:

```text
100 concurrent requests
Same user
Same show
Multiple seats
```

Observed result:

```text
201 Created    : 3
409 Conflict   : 97
Other responses: 0
```

The resulting show state contained four confirmed seats for the test user, consistent with the configured per-user limit of four seats.

## Idempotency Testing

The same reservation request was submitted multiple times using the same `Idempotency-Key`.

The repeated request returned the original reservation instead of creating a duplicate reservation.

Reusing the same key with different request data returned:

```text
409 Conflict
```

Example:

```json
{
  "error": "Idempotency key was already used with a different request"
}
```

## Cancellation Testing

A confirmed reservation was cancelled successfully.

Example response:

```json
{
  "reservation_id": 4,
  "status": "CANCELLED",
  "message": "Reservation cancelled successfully"
}
```

After cancellation, the associated seats became available again.

The show-level seat count invariant remained valid:

```text
totalSeats = availableSeats + heldSeats + confirmedSeats
```

## Important Invariants

### No Double Booking

A seat cannot be confirmed for multiple active reservations.

### Unique Seat per Show

A seat number is unique within a show:

```text
(show_id, seat_number)
```

### All-or-Nothing Reservation

A multi-seat reservation either confirms all requested seats or none.

### Per-User Limit

For a given user and show:

```text
confirmed seats <= per_user_limit
```

### Seat Count Consistency

```text
totalSeats =
availableSeats +
heldSeats +
confirmedSeats
```

### Idempotency

The same:

```text
user + show + idempotency key
```

cannot create multiple reservations.

## Design Decisions

### PostgreSQL Row-Level Locking

Database-level locking was chosen instead of Java in-memory locking.

An in-memory lock such as:

```
synchronized
```

only protects requests inside a single JVM instance.

PostgreSQL row-level locking works across multiple application instances connected to the same database.

### Deterministic Lock Ordering

Seat numbers are sorted before acquiring locks.

This reduces the risk of deadlocks when multiple transactions request overlapping sets of seats.

### Advisory Lock for Per-User Limit

The per-user limit is an aggregate constraint.

A PostgreSQL advisory transaction lock for:

```text
user + show
```

serializes concurrent reservations from the same user without serializing all reservations for the show.

### Database Constraints

Database uniqueness constraints provide an additional correctness layer for:

```text
show + seat number
```

and:

```text
user + show + idempotency key
```

### Explicit Cancellation

Temporary holds were not implemented.

The implementation uses explicit cancellation to release confirmed seats.

## Limitations and Future Improvements

Potential production improvements include:

- JWT/OAuth2 authentication
- Flyway or Liquibase database migrations
- Production-grade secret management
- Connection pool tuning
- Rate limiting
- Distributed tracing
- More detailed Prometheus metrics
- Temporary seat holds with expiration
- Automated integration tests for concurrency scenarios
- Dedicated load testing using tools such as k6 or Gatling
- Database backup and recovery strategy
- Production monitoring and alerting

## Running the Application

Build:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Start with Docker:

```powershell
docker compose up --build
```

Verify health:

```text
http://localhost:8080/actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

Create a show:

```http
POST http://localhost:8080/shows
Content-Type: application/json
```

```json
{
  "name": "friday-night",
  "seats": [
    "A1",
    "A2",
    "A3",
    "A4",
    "A5"
  ],
  "price_paise": 25000
}
```

Reserve a seat:

```http
POST http://localhost:8080/shows/1/reserve
Authorization: Bearer user-123
Idempotency-Key: reservation-001
Content-Type: application/json
```

```json
{
  "seats": [
    "A1"
  ]
}
```

Check the show:

```http
GET http://localhost:8080/shows/1
```

Cancel a reservation:

```http
POST http://localhost:8080/shows/reservations/{reservationId}/cancel
Authorization: Bearer user-123
```

## Repository

GitHub:

https://github.com/himanshugehlot87/seat-reservation
