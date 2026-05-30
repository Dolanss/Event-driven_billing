# Operations

## Health Checks

The service exposes:

- `GET /actuator/health`
- `GET /actuator/prometheus`

In production, metrics endpoints should be restricted to trusted networks or protected by the platform ingress.

## Key Metrics

Useful operational signals:

- HTTP request count by status code.
- Webhook processing success and failure counters.
- Queue depth.
- Retry count.
- Dead-letter count.
- Database connection pool saturation.
- Processing latency and queue wait time.

## Failure Scenarios

### Duplicate Event

The provider may retry the same event. The database uniqueness constraint on `event_id` prevents duplicate inserts and the API returns `409 Conflict`.

### Invalid State Transition

An event can arrive in a state that violates the invoice state machine, such as `REFUNDED` before `PAID`. The event is marked as `DEAD_LETTER` because retrying does not change the business rule.

### Retryable Processing Failure

Transient failures are marked as `FAILED` with `next_retry_at`. The scheduler re-queues eligible failed events until the max retry count is reached.

### Local Queue Saturation

The local queue is bounded. If it is full, the event may be persisted but not queued. A production implementation should use a durable broker plus stale event recovery.

## Runbook

1. Check `/actuator/health`.
2. Inspect `webhook_events` by status.
3. Check queue size and retry counters.
4. Review logs by `eventId` and `invoiceId`.
5. Reprocess failed events only after confirming the failure is transient.
6. Treat `DEAD_LETTER` events as business or data-quality cases requiring manual review.
