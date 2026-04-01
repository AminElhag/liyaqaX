# ADR-0013 — Arabic as the default language with English as secondary

## Status
Accepted

## Context
The platform operates primarily in Saudi Arabia, serving fitness clubs whose staff and members predominantly communicate in Arabic. The system is bilingual by requirement — both Arabic and English must be fully supported across all applications. A decision was needed on which language is the default (the fallback when no preference is set) and how bilingual content is structured. The four user-facing apps serve different audiences: Nexus is used by the internal team (default English, as the team works in English), while Pulse, Coach, and Arena serve Saudi-based club staff, trainers, and members (default Arabic). Arabic requires right-to-left (RTL) layout, Hijri calendar display alongside Gregorian, and locale-aware number and currency formatting (ر.س for SAR in Arabic, "SAR" in English). All localized string fields in the data model use a suffix pattern (`nameAr` / `nameEn`) and API responses always include both values — the client selects based on the user's locale preference.

## Decision
Arabic is the default language for all customer-facing applications (Pulse, Coach, Arena, Arena Mobile). English is the default for the internal platform dashboard (Nexus). Both languages are always fully supported — every user-facing string uses i18n keys, never hardcoded text. API responses include both language values for all localized fields. When the locale is Arabic, the root element sets `dir="rtl"` and all layout uses logical CSS properties (`start`/`end`, not `left`/`right`). Hijri calendar is displayed alongside Gregorian on all date pickers and date displays in Pulse, Coach, Arena, and Arena Mobile.

## Consequences
- The primary user base (Saudi club staff and members) sees their native language by default — no configuration needed on first use.
- RTL layout is a first-class requirement, not an afterthought — all frontends use logical CSS properties and Compose Multiplatform uses `Arrangement.Start/End`, preventing layout bugs when switching languages.
- Hijri calendar support is required on all date displays, adding complexity to date pickers and date formatting utilities across all client apps.
- The internal team (Nexus) defaults to English, matching their working language, while still supporting Arabic for completeness.
- All localized data fields are stored in pairs (`nameAr`/`nameEn`, `descriptionAr`/`descriptionEn`) — both values are always required when creating or updating content, increasing data entry effort but ensuring complete bilingual coverage.
- No user-facing strings are hardcoded anywhere — every string goes through the i18n system (`react-i18next` on web, string resources on mobile), enforced by code review.
- Number and currency formatting must use `Intl.NumberFormat` (web) or platform formatters (mobile) with the active locale — manual formatting is prohibited to avoid inconsistencies.
