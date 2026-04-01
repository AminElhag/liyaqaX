# ADR-0007 — RFC 7807 Problem Details for all API error responses

## Status
Accepted

## Context
The platform has five client applications (four web apps and one mobile app) consuming a single backend API. Each client needs to parse, display, and react to error responses consistently. Without a standard error format, each client would need custom parsing logic for different error shapes, and developers would inevitably invent ad hoc error structures per endpoint. The frontends display error details in toasts and alerts using the error's `detail` field, and the mobile app maps HTTP status codes to typed domain errors. A standard, well-documented format ensures all clients can implement a single error-handling path. RFC 7807 (Problem Details for HTTP APIs) is an IETF standard designed for exactly this purpose, providing a machine-readable and human-readable structure with extensibility for domain-specific fields.

## Decision
All API error responses use the RFC 7807 Problem Details format with the fields: `type` (URI identifying the error category), `title` (short human-readable summary), `status` (HTTP status code), `detail` (specific explanation for this occurrence), and `instance` (the request path). All uncaught exceptions are routed through a single global exception handler to guarantee the response shape is consistent everywhere. Stack traces, internal class names, and database messages are never exposed in production error responses.

## Consequences
- Every client implements one error parser — reducing frontend error-handling code and ensuring consistent user-facing error messages across all apps.
- The `detail` field provides actionable, localized error messages that frontends display directly in toasts and alerts without transformation.
- HTTP status codes are mapped correctly and consistently: 400 for validation, 401 for unauthenticated, 403 for unauthorized, 404 for not found, 409 for conflicts, 422 for business rule violations, 500 for unexpected errors.
- The `type` URI enables clients to programmatically distinguish error categories (e.g., react differently to a validation error vs. a business rule violation).
- All developers must route errors through the global handler — throwing raw exceptions that bypass it produces non-conforming responses and breaks clients.
- The standard format adds a small amount of boilerplate to error handling, but the consistency benefit far outweighs the cost.
