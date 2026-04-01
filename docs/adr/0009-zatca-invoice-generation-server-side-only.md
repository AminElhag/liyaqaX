# ADR-0009 — ZATCA invoice generation is server-side only

## Status
Accepted

## Context
The platform operates in Saudi Arabia and must comply with ZATCA (Zakat, Tax and Customs Authority) e-invoicing regulations. ZATCA-compliant invoices require cryptographic signing, specific XML formatting, QR code embedding, and communication with ZATCA's API for clearance or reporting. These operations involve secrets (ZATCA certificate and private key), precise VAT calculations in halalas, and regulatory validation rules that change with ZATCA phases. Performing any of these steps on the client side would expose cryptographic material to the browser or mobile app, create inconsistency across five clients, and make compliance auditing nearly impossible. The web-pulse CLAUDE.md explicitly states that ZATCA-compliant invoices are generated server-side and downloaded as PDF, with the frontend only triggering generation and polling for the download URL. Invoice totals are never computed on the frontend.

## Decision
All ZATCA invoice generation, signing, and submission is performed exclusively on the backend. Clients trigger invoice generation via an API call and poll for or receive the resulting PDF download URL. No invoice computation, XML construction, or cryptographic operation occurs on any client. The ZATCA certificate and environment configuration are managed as server-side secrets.

## Consequences
- ZATCA cryptographic material (certificate, private key) never leaves the server — eliminating the risk of client-side exposure.
- Invoice logic is implemented once, ensuring all clients (Pulse, Arena, mobile) produce identical, compliant invoices.
- Regulatory changes to ZATCA rules require updating only the backend — no client redeployment needed for compliance updates.
- VAT calculations are performed server-side in halalas with integer arithmetic, guaranteeing precision and auditability.
- Clients cannot generate invoices offline — invoice creation requires a network connection to the backend, which in turn may need to communicate with ZATCA's API.
- Invoice generation may be slow (ZATCA API latency, PDF rendering) — clients must implement an async pattern (trigger + poll) rather than expecting a synchronous response.
