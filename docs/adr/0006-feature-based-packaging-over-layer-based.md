# ADR-0006 — Feature-based packaging over layer-based packaging

## Status
Accepted

## Context
The backend codebase needs a packaging strategy that scales as the number of bounded contexts grows (membership, billing, invoicing, scheduling, leads, PT, GX, tenant management, etc.). Layer-based packaging (`controller/`, `service/`, `repository/`) groups code by technical role, scattering a single feature across many packages and making it difficult to understand, modify, or extract a feature in isolation. Feature-based packaging groups all layers of a feature together, improving cohesion and enabling the team to reason about one business domain at a time. The backend also applies Clean/Hexagonal Architecture for complex bounded contexts, where domain logic must be free of framework annotations — feature-based packaging aligns naturally with this approach by keeping ports, adapters, and domain models co-located within their feature boundary.

## Decision
Use feature-based packaging. Each feature owns its controller, service, repository, domain model, and DTOs together in one package. Clean/Hexagonal Architecture is applied within each feature for complex bounded contexts: domain logic is free of framework dependencies, with ports (interfaces) and adapters (infrastructure implementations) defined separately within the feature package.

## Consequences
- High cohesion: all code related to a business capability lives in one place, making features easier to understand, review, and modify.
- Supports independent evolution — changes to the billing feature do not touch the membership package.
- Aligns with Clean/Hexagonal Architecture: each feature package naturally contains its own domain, ports, and adapters.
- New developers can onboard to a single feature without understanding the entire codebase.
- Shared utilities and cross-cutting concerns (tenant filtering, error handling, auth) must live in a clearly separated common package to avoid duplication across features.
- Requires discipline to avoid cross-feature imports at the domain layer — features communicate through well-defined interfaces or the service layer, not by reaching into each other's internals.
