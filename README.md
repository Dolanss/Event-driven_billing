# Billing Webhook Processor

Event-driven billing webhook processor built with **Java 17 + Spring Boot 3**. Handles payment events (PAID, FAILED, REFUNDED, CHARGEBACK) with idempotency, async processing, invoice state machine, retry logic, audit logging and Prometheus metrics.

## Architecture

```
POST /webhooks/payment
       │
       ▼
WebhookIngestionService          ← idempotency check (event_id UNIQUE in DB)
       │  persists WebhookEvent
       ▼
  EventQueue (BlockingQueue)     ← simulates RabbitMQ exchange
       │
       ▼
EventQueueConsumer (virtual thread)
       │
       ▼
WebhookProcessingService         ← @Transactional
       ├── InvoiceService        ← state machine + upsert invoice
       │       └── AuditService  ← same transaction (MANDATORY propagation)
       └── on failure → exponential backoff (10s / 20s / 40s)

RetrySchedulerService            ← @Scheduled every 10s, re-queues FAILED events
```

## Features

| Feature | Details |
|---|---|
| **Idempotency** | `event_id` unique constraint — `HTTP 409` on duplicate |
| **Async processing** | `LinkedBlockingQueue<1000>` + virtual-thread consumer (drop-in for RabbitMQ) |
| **Invoice state machine** | `PENDING → PAID / FAILED / REFUNDED / CHARGEBACK` — terminal states are immutable |
| **Retry logic** | Exponential backoff: 10s → 20s → 40s, max 3 attempts → `DEAD_LETTER` |
| **Audit log** | Every status transition recorded atomically in the same transaction |
| **Security** | HTTP Basic Auth via Spring Security (simulate provider secret) |
| **Migrations** | Flyway with 3 versioned SQL scripts |
| **Metrics** | Prometheus counters per event type/result + queue size gauge |
| **Observability** | Spring Actuator: `/health`, `/metrics`, `/prometheus` |

## Tech Stack

- **Java 17** — virtual threads for the queue consumer
- **Spring Boot 3.2** — Web, Data JPA, Security, Actuator, Validation
- **PostgreSQL 16** — primary store
- **Flyway** — schema migrations
- **Micrometer + Prometheus** — metrics
- **Testcontainers** — integration tests against a real Postgres instance
- **Awaitility** — async assertion helper in tests
- **Docker Compose** — local environment

## Project Structure

```
src/main/java/com/billing/webhook/
├── config/          # Async, Security, Jackson, Metrics
├── controller/      # WebhookController, InvoiceController
├── domain/          # Invoice, WebhookEvent, AuditLog (entities + enums)
├── dto/             # PaymentEventRequest, WebhookResponse, ErrorResponse
├── exception/       # Domain exceptions + GlobalExceptionHandler
├── queue/           # EventQueue, EventQueueConsumer, WebhookEventMessage
├── repository/      # Spring Data JPA repositories
└── service/         # WebhookIngestionService, WebhookProcessingService,
                     # InvoiceService, AuditService, RetrySchedulerService

src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_invoices.sql
    ├── V2__create_webhook_events.sql
    └── V3__create_audit_logs.sql
```

## Running Locally

### With Docker Compose

```bash
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

### Without Docker (requires a local Postgres)

```bash
# Set environment variables
export DB_HOST=localhost
export DB_NAME=webhookdb
export DB_USER=webhookuser
export DB_PASSWORD=webhookpass

mvn spring-boot:run
```

## API

### Authentication

All endpoints use HTTP Basic Auth. Default credentials (override via env vars):

| Variable | Default |
|---|---|
| `WEBHOOK_USERNAME` | `stripe-provider` |
| `WEBHOOK_PASSWORD` | `super-secret-key` |

### Endpoints

#### `POST /webhooks/payment`
Receives a payment event.

**Request body:**
```json
{
  "eventId":    "evt_001",
  "invoiceId":  "INV-2024-001",
  "eventType":  "PAID",
  "amount":     149.99,
  "currency":   "BRL",
  "customerId": "cust-abc",
  "metadata":   null
}
```

**Event types:** `PAID` | `FAILED` | `REFUNDED` | `CHARGEBACK`

**Responses:**

| Status | Meaning |
|---|---|
| `202 Accepted` | Event queued for async processing |
| `409 Conflict` | Duplicate `eventId` |
| `400 Bad Request` | Validation error |
| `401 Unauthorized` | Missing or invalid credentials |

#### `GET /webhooks/payment/{invoiceId}/audit`
Returns the full status transition history for an invoice, ordered by time.

#### `GET /invoices/{externalId}`
Returns the current state of an invoice.

#### `GET /actuator/health`
Public. Returns application and DB health.

#### `GET /actuator/prometheus`
Public. Prometheus-format metrics.

### Example — full payment flow

```bash
BASE="http://localhost:8080"
AUTH="-u stripe-provider:super-secret-key"

# 1. Receive payment
curl $AUTH -s -X POST $BASE/webhooks/payment \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-1","invoiceId":"INV-001","eventType":"PAID","amount":99.90,"currency":"BRL","customerId":"cust-1"}'

# 2. Refund
curl $AUTH -s -X POST $BASE/webhooks/payment \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-2","invoiceId":"INV-001","eventType":"REFUNDED","amount":99.90,"currency":"BRL","customerId":"cust-1"}'

# 3. Check audit log
curl $AUTH -s $BASE/webhooks/payment/INV-001/audit | jq .

# 4. Duplicate — should return 409
curl $AUTH -s -X POST $BASE/webhooks/payment \
  -H "Content-Type: application/json" \
  -d '{"eventId":"evt-1","invoiceId":"INV-001","eventType":"PAID","amount":99.90,"currency":"BRL","customerId":"cust-1"}'
```

## Invoice State Machine

```
              PAID ──────────► REFUNDED
             /    \               (terminal)
PENDING ────/      └──────────► CHARGEBACK
            \                    (terminal)
             └──────────────► FAILED
                                 │
                                 └──► PAID  (retry payment)
```

Attempting an invalid transition returns `HTTP 422 Unprocessable Entity`.

## Running Tests

Requires Docker (Testcontainers spins up a real Postgres container).

```bash
mvn test
```

Tests cover:
- Happy path: event accepted, invoice updated, audit recorded
- Idempotency: duplicate `eventId` returns `409`
- Unauthenticated request returns `401`
- Invoice state machine: multi-step transitions + audit trail
- Audit log endpoint
- Bean Validation: missing required fields
- Actuator health is public
- Unit tests: state machine transitions with Mockito

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | Postgres host |
| `DB_PORT` | `5432` | Postgres port |
| `DB_NAME` | `webhookdb` | Database name |
| `DB_USER` | `webhookuser` | Database user |
| `DB_PASSWORD` | `webhookpass` | Database password |
| `WEBHOOK_USERNAME` | `stripe-provider` | Basic auth username |
| `WEBHOOK_PASSWORD` | `super-secret-key` | Basic auth password |
| `SERVER_PORT` | `8080` | HTTP port |

## License

MIT
