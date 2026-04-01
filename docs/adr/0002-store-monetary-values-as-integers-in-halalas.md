# ADR-0002 — Store monetary values as integers in halalas

## Status
Accepted

## Context
The platform processes financial transactions in Saudi Riyal (SAR) across memberships, payments, invoices, PT packages, and refunds. Floating-point types (`float`, `double`) introduce rounding errors that compound across calculations — a well-known problem in financial software. The system generates ZATCA-compliant invoices where VAT rounding precision is critical (fractional halala amounts have caused production bugs in similar systems). An alternative of using `BigDecimal` everywhere was considered but adds complexity in serialization, database storage, and cross-platform consistency (Kotlin backend, TypeScript frontends, Kotlin Multiplatform mobile).

## Decision
All monetary amounts are stored, transmitted, and computed as integers in the smallest currency unit: halalas (1 SAR = 100 halalas). Field names include the unit suffix: `priceHalalas`, `totalHalalas`, `depositHalalas`. Backend uses `Long` (Kotlin/Java), frontends use `number` (TypeScript, validated as integer). Formatting to SAR display (symbol, decimal separator, locale-aware rendering) happens only at the presentation layer.

## Consequences
- Eliminates floating-point rounding errors in all financial calculations, including VAT.
- Simplifies cross-platform consistency — integers behave identically in Kotlin, TypeScript, and SQL.
- The `Halalas` suffix in field names makes the unit explicit, preventing accidental SAR-vs-halala confusion.
- Developers must remember to divide by 100 at the presentation layer — a dedicated `formatCurrency()` utility in each frontend enforces this.
- Any future multi-currency support would require defining the smallest unit per currency, but SAR is the only currency in scope.
- Invoice totals and VAT calculations must be computed server-side to ensure consistency; frontends never compute monetary aggregates.
