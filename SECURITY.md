# Security Policy

## Supported Use

This project is a webhook processor intended for local execution and technical review. It demonstrates authentication, input validation, idempotency and operational boundaries, but it is not configured as a complete production payment gateway.

## Current Security Controls

- Stateless HTTP Basic authentication for application endpoints.
- Bean Validation on incoming webhook payloads.
- PostgreSQL constraints for core data integrity.
- Non-root container user.
- Secrets are provided through environment variables.

## Production Hardening Checklist

- Replace Basic Auth with signed webhook verification, such as HMAC with timestamp validation.
- Enforce TLS-only traffic at the ingress layer.
- Rotate webhook secrets regularly.
- Redact sensitive payload fields from logs and audit data.
- Restrict Actuator and Prometheus endpoints to trusted networks.
- Use least-privilege database credentials.
- Add rate limiting for external webhook endpoints.
- Add replay protection using provider event id and timestamp windows.

## Reporting

Open an issue with the security concern and reproduction steps. Do not include real secrets, tokens or customer data in issues.
