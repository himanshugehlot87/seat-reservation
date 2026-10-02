# WRITEUP — Seat Reservation at Scale

## 1. Problem

The system reserves assigned seats for a show under concurrent requests.

The most important correctness requirement is that the same seat must never be confirmed for two different reservations, even when many requests arrive at the same time.

The implementation uses PostgreSQL as the consistency boundary and relies on database transactions and row-level locking rather than an in-memory lock.

---

## 2. Concurrency Problem

A naive implementation could do the following:

1. Read a seat.
2. Check whether it is available.
3. Mark it as confirmed.
4. Commit.

Under concurrency, two requests can both read the same seat as available before either request updates it.

For example:

```text
Request A: SELECT seat A1 -> AVAILABLE
Request B: SELECT seat A1 -> AVAILABLE

Request A: UPDATE A1 -> CONFIRMED
Request B: UPDATE A1 -> CONFIRMED
```

This creates a potential double-booking race.

Therefore, availability checking and seat state modification must happen inside one database transaction while the seat row is locked.

---

## 3. Seat Locking Strategy

For reservation requests, the application locks all requested seats using PostgreSQL:

```sql
SELECT *
FROM seats
WHERE show_id = :showId
  AND seat_number IN (:seatNumbers)
ORDER BY seat_number
FOR UPDATE;
```

`FOR UPDATE` locks the selected database rows for the duration of the transaction.

While one transaction owns the lock:

- another transaction cannot acquire the same row for update;
- the second transaction waits;
- after the first transaction commits, the second transaction continues;
- it then sees the updated seat status and returns `409 Conflict` if the seat is no longer available.

This makes the database transaction the source of truth for seat ownership.

---

## 4. Deterministic Lock Ordering

Multiple seats can be requested in a single reservation.

The requested seat numbers are normalized and sorted before locking:

```text
A3, A1, A2
```

becomes:

```text
A1, A2, A3
```

The database query also uses:

```sql
ORDER BY seat_number
```

This provides deterministic lock ordering.

The purpose is to reduce the possibility of inconsistent lock acquisition between concurrent multi-seat requests.

---

## 5. All-or-Nothing Reservation

The reservation API uses an all-or-nothing policy.

For a request such as:

```json
{
  "seats": ["A1", "A2", "A3"]
}
```

all requested seats must be available.

If even one requested seat:

- does not exist, or
- is already confirmed,

the reservation fails.

No subset of the requested seats is reserved.

Because the operation is transactional, any database changes made before the failure are rolled back.

---

## 6. Reservation Transaction Flow

The reservation operation follows this general sequence:

```text
BEGIN TRANSACTION

Normalize requested seats
        |
Acquire user/show advisory lock
        |
Check idempotency key
        |
Check per-user confirmed-seat limit
        |
Lock requested seat rows
        |
Validate seat existence
        |
Validate seat availability
        |
Create reservation
        |
Mark seats CONFIRMED
        |
Create reservation-seat records
        |
Save idempotency record
        |
COMMIT
```

If any step fails, the transaction is rolled back.

The idempotency check happens before the per-user limit check so that a retry of an already completed request can return the original reservation even if the user's current seat count has subsequently reached the limit.

---

## 7. Per-User Seat Limit

The default per-user limit is four seats per show.

The system must prevent concurrent requests from bypassing this limit.

For example, without synchronization:

```text
Request A -> user has 3 seats
Request B -> user has 3 seats

A checks limit -> allowed
B checks limit -> allowed

Both reserve another seat
```

The user could incorrectly end up with five seats.

To prevent this, the application uses a PostgreSQL transaction-level advisory lock:

```sql
SELECT pg_advisory_xact_lock(
    hashtext(:userId),
    CAST(:showId AS INTEGER)
);
```

This serializes reservation operations for the same user and show while allowing unrelated users to continue concurrently.

After acquiring the advisory lock, the application counts the user's confirmed seats and enforces the limit.

The advisory lock is automatically released when the transaction ends.

---

## 8. Idempotency

The reservation endpoint requires an `Idempotency-Key`.

The database contains a unique constraint on:

```text
user_id + show_id + idempotency_key
```

The request body is also hashed.

Two cases are supported.

### Same key + same request

The previously created reservation is returned.

No second reservation is created.

### Same key + different request

The request is rejected with:

```text
409 Conflict
```

This prevents accidental reuse of an idempotency key for a different reservation.

The idempotency record is stored in the same transaction as the reservation so that the reservation and its idempotency state remain consistent.

---

## 9. Authentication / User Identity

The reservation request does not accept `user_id` from the JSON body.

Instead, the current implementation extracts the identity from:

```http
Authorization: Bearer user-123
```

The token value is treated as the user identifier for this assignment.

This keeps the reservation ownership tied to the authenticated request rather than to user-controlled request data.

A production implementation would replace this simplified mechanism with a real JWT/OIDC authentication layer.

---

## 10. Cancellation

Cancellation is implemented as an explicit operation.

Endpoint:

```http
POST /shows/reservations/{reservationId}/cancel
```

The cancellation operation:

1. Locks the reservation.
2. Verifies that the authenticated user owns it.
3. Returns the existing state if it is already cancelled.
4. Locks the associated seat rows.
5. Changes the seats back to `AVAILABLE`.
6. Changes the reservation status to `CANCELLED`.
7. Commits the transaction.

The reservation itself is retained as a historical record rather than deleted.

This preserves the reservation lifecycle and makes cancellation auditable.

---

## 11. Why Cancellation Also Uses Locks

Cancellation modifies both:

```text
reservation
```

and:

```text
seat
```

Without locking, a reservation and cancellation could potentially modify the same seat concurrently.

The implementation therefore locks the reservation and then locks the associated seat rows before changing their state.

The seat IDs are locked in deterministic database order.

This keeps cancellation consistent with concurrent reservation operations.

---

## 12. Error Handling

The API maps expected business conflicts to appropriate HTTP responses.

Examples:

| Condition | Response |
|---|---:|
| Seat already confirmed | `409 Conflict` |
| Per-user limit exceeded | `409 Conflict` |
| Idempotency key reused with different request | `409 Conflict` |
| Show does not exist | `404 Not Found` |
| Reservation does not exist | `404 Not Found` |
| User attempts unauthorized cancellation | `403 Forbidden` |
| Missing/invalid authentication | `401 Unauthorized` |
| Invalid request body | `400 Bad Request` |
| Requested seat does not exist | `400 Bad Request` |

The goal is to ensure that expected contention and business conflicts do not become server errors.

---

## 13. Concurrency Test

A burst test was used to send 100 concurrent requests for the same seat.

The test result was:

```text
Total requests : 100
201 Created    : 1
409 Conflict   : 99
Other responses: 0
```

This demonstrates that under the tested concurrency:

- exactly one request confirmed the seat;
- competing requests received `409 Conflict`;
- no unexpected HTTP responses occurred.

The test was executed against the Dockerized application.

---

## 14. Per-User Concurrency Test

A second concurrency test used the same authenticated user while multiple requests attempted reservations for seats in the same show.

The tested show ultimately contained four confirmed seats and one available seat.

This is consistent with the configured per-user limit of four seats.

The important property is that concurrent requests from the same user/show are serialized by the PostgreSQL advisory transaction lock before the limit is evaluated.

---

## 15. Idempotency Test

The same idempotency key was submitted more than once with the same request.

The API returned the original reservation rather than creating another reservation.

The same key was then submitted with a different seat request.

The API returned:

```json
{
  "error": "Idempotency key was already used with a different request"
}
```

with HTTP `409 Conflict`.

---

## 16. Cancellation Test

A confirmed reservation was cancelled successfully.

The API returned:

```json
{
  "reservation_id": 4,
  "status": "CANCELLED",
  "message": "Reservation cancelled successfully"
}
```

After cancellation, the cancelled reservation's seat became available again.

The show state maintained the invariant:

```text
total seats = available seats + held seats + confirmed seats
```

The current implementation does not use a temporary `HELD` state, so `heldSeats` is always `0`.

---

## 17. Observability

The application provides:

### Health

```text
/actuator/health
```

### Liveness

```text
/actuator/health/liveness
```

### Readiness

```text
/actuator/health/readiness
```

### Prometheus metrics

```text
/actuator/prometheus
```

Custom counters include:

```text
reservation_success_total
reservation_conflict_total
reservation_cancelled_total
```

The application also generates a correlation ID for every request.

The correlation ID is returned in:

```text
X-Correlation-ID
```

and included in structured application logs.

---

## 18. Database as the Consistency Boundary

The design deliberately avoids relying on Java synchronized blocks or local JVM locks.

A JVM-level lock would not provide correctness when multiple application instances are running.

For example:

```text
Application Instance 1
        |
        +---- JVM lock

Application Instance 2
        |
        +---- different JVM lock
```

Both instances could still access the same seat.

PostgreSQL row locks and transactions work across application instances because the database is shared.

This makes the locking mechanism compatible with horizontal scaling.

---

## 19. Scalability Trade-offs

The current design prioritizes correctness and simplicity.

The main contention point is a highly popular seat or show.

For example, if thousands of requests target the same seat:

```text
Request 1 -> locks A1
Request 2 -> waits
Request 3 -> waits
Request 4 -> waits
...
```

Only one transaction can change that seat at a time.

This is intentional because the seat represents a single inventory item that cannot be sold twice.

The design avoids locking the entire show, so requests for unrelated seats can continue concurrently.

The per-user advisory lock is narrower: it serializes requests only for the same user and show.

---

## 20. Expected Bottlenecks

Potential bottlenecks at higher scale include:

- database connection pool size;
- PostgreSQL CPU and I/O;
- contention on very popular seats;
- contention for the same user's reservations;
- large shows with many seats;
- high request rates against the same show.

The database remains the authoritative source for inventory correctness.

Application instances can be scaled horizontally, but the database must be sized appropriately.

---

## 21. Future Improvements

If the system needed to support significantly larger traffic, possible improvements would include:

- connection-pool tuning;
- database indexing based on production query patterns;
- load testing with a realistic traffic distribution;
- separating read-heavy show-state queries from reservation writes where appropriate;
- introducing a dedicated inventory service if the domain required it;
- stronger authentication using JWT/OIDC;
- distributed observability and tracing;
- deployment across multiple application instances;
- database scaling/read replicas for appropriate read workloads.

These changes should only be introduced when required by measured workload characteristics. The current implementation intentionally keeps the architecture small and focused on correctness.

---

## 22. Key Design Decisions

### PostgreSQL row locking

Chosen because seat ownership is transactional inventory and must be correct under concurrency.

### Advisory transaction lock

Chosen for the per-user limit because it serializes only the relevant user/show combination.

### All-or-nothing reservation

Chosen to provide predictable behavior for multi-seat requests.

### Explicit cancellation

Chosen instead of automatic hold expiration to keep the reservation lifecycle simpler for the assignment.

### Database idempotency constraint

Chosen so idempotency remains correct even when multiple application instances are running.

### No Redis/Kafka/Kubernetes

These technologies are not necessary for the core correctness requirements of this assignment and would add operational complexity.

---

## 23. Summary

The implementation uses PostgreSQL transactions, row-level locking, deterministic lock ordering, advisory locks, unique idempotency constraints, and explicit state transitions to provide concurrency-safe seat reservations.

The most important invariant is:

```text
A confirmed seat belongs to at most one active reservation.
```

The concurrency test demonstrated:

```text
100 concurrent requests
1 successful reservation
99 conflicts
0 unexpected responses
```

The design keeps the critical consistency logic inside PostgreSQL transactions, allowing the application layer to remain stateless and horizontally scalable while the database protects the shared seat inventory.
