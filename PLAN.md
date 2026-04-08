# Plan 24 — Online Payments (Moyasar)

## Status
Ready for implementation

## Branch
`feature/plan-24-moyasar`

## Goal
Enable members to pay for new memberships and renewals from web-arena using mada, Visa/Mastercard, or Apple Pay via Moyasar. Completes the self-service loop (Plan 22 self-registration + Plan 33 lapse recovery). Staff see a read-only online payment history tab on each member profile in web-pulse.

## Context
- `Membership` entity already exists with `status = pending_payment` (set by Plan 22 self-registration)
- `MemberStatus.LAPSED` exists from Plan 33 — lapsed members can also pay online to renew
- `ClubPortalSettings.onlinePaymentEnabled` column already exists (created in web-arena plan) but was never wired to a real payment flow
- `Payment` + `Invoice` entities already exist — Moyasar success creates records in both
- ZATCA invoice generation already triggered by `Payment` creation — no changes needed there
- Next Flyway migration: **V22**

---

## Scope — what this plan covers

- [ ] Flyway V22 — `online_payment_transactions` table
- [ ] `OnlinePaymentTransaction` entity
- [ ] `MoyasarClient` — HTTP client wrapping Moyasar REST API (create payment, fetch payment)
- [ ] `OnlinePaymentService` — initiate, handle webhook, verify signature, activate membership
- [ ] Webhook endpoint: `POST /api/v1/webhooks/moyasar` (public — no JWT, HMAC-verified)
- [ ] 3 arena endpoints: initiate payment, get payment status, get member's transaction history
- [ ] New audit actions: `ONLINE_PAYMENT_INITIATED`, `ONLINE_PAYMENT_SUCCEEDED`, `ONLINE_PAYMENT_FAILED`
- [ ] New permission: `online-payment:read` (Owner, Branch Manager)
- [ ] web-pulse: "Online Payments" tab on member profile — read-only transaction list
- [ ] web-arena: "Pay Now" button on `/membership` (shown only when `onlinePaymentEnabled = true` and membership is `pending_payment` or member is lapsed)
- [ ] web-arena: payment callback handler (`/payment-callback`) — reads Moyasar redirect params, redirects to `/membership`
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Refunds via Moyasar API
- Saving card / tokenisation for repeat payments
- Staff-initiated online payment links (pay-by-link)
- Subscription auto-renewal via Moyasar
- Payment method management UI

---

## Decisions already made

- **Redirect flow**: backend creates Moyasar payment intent → returns `hostedUrl` to frontend → frontend redirects to Moyasar → Moyasar calls our webhook on completion → Moyasar also redirects member's browser to our callback URL
- **Payment methods**: mada, creditcard (Visa/Mastercard), applepay — all supported by Moyasar hosted page; no code differentiation needed, Moyasar presents the method selector
- **Webhook verification**: HMAC-SHA256 of the raw request body using `MOYASAR_WEBHOOK_SECRET` env var. If signature mismatch → 401 and do nothing.
- **Idempotency**: `online_payment_transactions.moyasar_id` has a UNIQUE constraint. If webhook fires twice for the same payment → second call is a no-op (already `PAID`).
- **Membership activation on success**: `Membership.status → active`, `Membership.startDate = today`, `Membership.endDate = today + plan.durationDays`. If member was `LAPSED`, `Member.status → ACTIVE`.
- **Payment + Invoice**: a `Payment` record and an `Invoice` record are created on successful webhook — same as cash payment flow. ZATCA invoice generation fires automatically (existing behaviour).
- **Failed/expired payments**: `OnlinePaymentTransaction.status = FAILED`. Membership stays `pending_payment`. Member retries by initiating a new payment.
- **`onlinePaymentEnabled = false`**: Pay Now button is hidden in web-arena. No backend enforcement needed (the button simply doesn't render).
- **Moyasar callback URL**: `{WEB_ARENA_BASE_URL}/payment-callback?paymentId={moyasarId}` — passed to Moyasar at payment creation. Backend `MOYASAR_CALLBACK_URL_BASE` env var holds the arena base URL.
- **Flyway V22**
- **No cancel-unpaid scheduler** in this plan — membership stays `pending_payment` indefinitely.

---

## Entity design

### OnlinePaymentTransaction

```kotlin
@Entity
@Table(name = "online_payment_transactions")
class OnlinePaymentTransaction(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    // The Moyasar payment id returned from POST /v1/payments
    @Column(name = "moyasar_id", nullable = false, unique = true, updatable = false)
    val moyasarId: String,

    @Column(name = "membership_id", nullable = false, updatable = false)
    val membershipId: Long,

    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,

    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,

    // Amount in halalas (1 SAR = 100 halalas)
    @Column(name = "amount_halalas", nullable = false, updatable = false)
    val amountHalalas: Long,

    // INITIATED | PAID | FAILED | CANCELLED
    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    // mada | creditcard | applepay — populated from webhook payload
    @Column(name = "payment_method", length = 20)
    var paymentMethod: String? = null,

    @Column(name = "moyasar_hosted_url", length = 500, updatable = false)
    val moyasarHostedUrl: String,

    @Column(name = "callback_received_at")
    var callbackReceivedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
```

No `deletedAt` — payment records are immutable audit trail. Status transitions only.

---

## Flyway V22

```sql
-- V22__online_payment_transactions.sql

CREATE TABLE online_payment_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    moyasar_id          VARCHAR(100) NOT NULL UNIQUE,
    membership_id       BIGINT NOT NULL REFERENCES memberships(id),
    member_id           BIGINT NOT NULL REFERENCES members(id),
    club_id             BIGINT NOT NULL REFERENCES clubs(id),
    amount_halalas      BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    payment_method      VARCHAR(20),
    moyasar_hosted_url  VARCHAR(500) NOT NULL,
    callback_received_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_member_id    ON online_payment_transactions(member_id);
CREATE INDEX idx_otp_membership_id ON online_payment_transactions(membership_id);
CREATE INDEX idx_otp_moyasar_id   ON online_payment_transactions(moyasar_id);
CREATE INDEX idx_otp_club_status  ON online_payment_transactions(club_id, status, created_at DESC);
```

**Index rationale:**
- `idx_otp_member_id` — member profile Online Payments tab query
- `idx_otp_membership_id` — link to membership for activation
- `idx_otp_moyasar_id` — webhook lookup (most critical — called on every payment event)
- `idx_otp_club_status` — web-pulse per-club transaction list with status filter

---

## Moyasar API client

### MoyasarClient (Spring `@Component`)

```kotlin
@Component
class MoyasarClient(
    @Value("\${moyasar.api-key}") private val apiKey: String,
    private val restTemplate: RestTemplate
) {
    // POST https://api.moyasar.com/v1/payments
    fun createPayment(request: MoyasarCreatePaymentRequest): MoyasarPaymentResponse

    // GET https://api.moyasar.com/v1/payments/{id}
    fun fetchPayment(moyasarId: String): MoyasarPaymentResponse
}
```

**MoyasarCreatePaymentRequest** (fields sent to Moyasar):
```json
{
  "amount": 15000,
  "currency": "SAR",
  "description": "Basic Monthly Membership — Elixir Gym",
  "publishable_api_key": "pk_...",
  "callback_url": "https://arena.liyaqa.sa/payment-callback?paymentId=moyasar_id",
  "source": { "type": "creditcard" },
  "metadata": {
    "membershipId": "internal-uuid",
    "memberId": "internal-uuid",
    "clubId": "internal-uuid"
  }
}
```

**MoyasarPaymentResponse** (key fields used):
- `id` — Moyasar payment id
- `status` — `initiated`, `paid`, `failed`, `cancelled`
- `source.type` — payment method used
- `url` — hosted payment page URL

Environment variables required:
```
MOYASAR_API_KEY=sk_live_...         # Secret key for server-to-server calls
MOYASAR_PUBLISHABLE_KEY=pk_live_... # Publishable key embedded in payment request
MOYASAR_WEBHOOK_SECRET=whsec_...    # For HMAC-SHA256 webhook signature verification
MOYASAR_CALLBACK_URL_BASE=https://arena.liyaqa.sa  # Arena base URL for redirect
```

---

## Business rules — enforce in service layer

1. **Feature flag check**: before initiating, verify `ClubPortalSettings.onlinePaymentEnabled = true` for the member's club. If not → `403 Forbidden`, `errorCode: ONLINE_PAYMENT_DISABLED`, message: "Online payments are not enabled for this club."
2. **Membership payability check**: membership must be in `pending_payment` state OR member status must be `LAPSED`. Any other state → `409 Conflict`, `errorCode: MEMBERSHIP_NOT_PAYABLE`, message: "This membership is not awaiting payment."
3. **Amount from plan**: payment amount is always taken from `MembershipPlan.priceHalalas` — never from request body. Frontend passes `membershipPublicId` only.
4. **Duplicate transaction guard**: if an `INITIATED` transaction already exists for this `membershipId` → return the existing `moyasarHostedUrl` instead of creating a new Moyasar payment (idempotent initiate). This prevents double-charging if the member clicks "Pay Now" twice.
5. **Webhook signature**: compute `HMAC-SHA256(rawBody, MOYASAR_WEBHOOK_SECRET)`. If the computed value does not match the `Moyasar-Signature` header → `401 Unauthorized`, do not process.
6. **Idempotent webhook**: if `status` is already `PAID` when webhook fires → return `200 OK` immediately, do nothing.
7. **Activation on success**: when webhook `status = paid`:
   - Set `OnlinePaymentTransaction.status = PAID`, `paymentMethod`, `callbackReceivedAt`
   - Set `Membership.status = active`, `startDate = today`, `endDate = today + plan.durationDays`
   - If `Member.status = LAPSED` → set to `ACTIVE`
   - Create `Payment` record (same fields as cash payment)
   - Create `Invoice` record (ZATCA generation fires automatically)
   - Log `ONLINE_PAYMENT_SUCCEEDED` audit action
8. **Failed webhook**: when webhook `status = failed` or `cancelled`:
   - Set `OnlinePaymentTransaction.status = FAILED`
   - Membership stays `pending_payment`
   - Log `ONLINE_PAYMENT_FAILED`
9. **Club scoping**: member can only pay for memberships belonging to their own club (`clubId` from JWT claim).

---

## API endpoints

| Method | Path | Auth | Permission | Description |
|--------|------|------|------------|-------------|
| `POST` | `/api/v1/arena/payments/initiate` | Member JWT | (authenticated member) | Initiate a Moyasar payment for a membership |
| `GET` | `/api/v1/arena/payments/{moyasarId}/status` | Member JWT | (authenticated member) | Poll payment status after callback redirect |
| `GET` | `/api/v1/arena/payments/history` | Member JWT | (authenticated member) | Member's own online payment history |
| `POST` | `/api/v1/webhooks/moyasar` | None (HMAC) | — | Moyasar webhook — public endpoint, HMAC-verified |
| `GET` | `/api/v1/pulse/members/{memberId}/online-payments` | Club staff JWT | `online-payment:read` | Staff view of member's online payment history |

---

## Request / Response shapes

### POST /arena/payments/initiate — request
```json
{
  "membershipPublicId": "uuid"
}
```

### POST /arena/payments/initiate — response `201 Created`
```json
{
  "transactionId": "uuid",
  "hostedUrl": "https://payment.moyasar.com/...",
  "amountSar": "150.00",
  "planName": "Basic Monthly"
}
```

### GET /arena/payments/{moyasarId}/status — response
```json
{
  "moyasarId": "pay_abc123",
  "status": "PAID",
  "paymentMethod": "mada",
  "amountSar": "150.00",
  "paidAt": "2026-04-08T09:00:00Z"
}
```

### GET /arena/payments/history — response
```json
{
  "transactions": [
    {
      "transactionId": "uuid",
      "moyasarId": "pay_abc123",
      "planName": "Basic Monthly",
      "amountSar": "150.00",
      "status": "PAID",
      "paymentMethod": "mada",
      "createdAt": "2026-04-08T08:55:00Z"
    }
  ]
}
```

### POST /webhooks/moyasar — request body (Moyasar format)
```json
{
  "id": "pay_abc123",
  "type": "payment_paid",
  "data": {
    "id": "pay_abc123",
    "status": "paid",
    "amount": 15000,
    "currency": "SAR",
    "source": { "type": "mada" },
    "metadata": { "membershipId": "...", "memberId": "...", "clubId": "..." }
  }
}
```
Response: `200 OK` (always — Moyasar retries on non-200)

### GET /pulse/members/{memberId}/online-payments — response
```json
{
  "transactions": [
    {
      "transactionId": "uuid",
      "moyasarId": "pay_abc123",
      "planName": "Basic Monthly",
      "amountSar": "150.00",
      "status": "PAID",
      "paymentMethod": "mada",
      "createdAt": "2026-04-08T08:55:00Z"
    }
  ]
}
```

---

## Repository queries

All must use `nativeQuery = true`:

```kotlin
// Find by Moyasar ID — used in webhook handler
@Query(value = """
    SELECT * FROM online_payment_transactions
    WHERE moyasar_id = :moyasarId
""", nativeQuery = true)
fun findByMoyasarId(moyasarId: String): OnlinePaymentTransaction?

// Find INITIATED transactions for a membership (idempotent initiate check)
@Query(value = """
    SELECT * FROM online_payment_transactions
    WHERE membership_id = :membershipId
      AND status = 'INITIATED'
    LIMIT 1
""", nativeQuery = true)
fun findInitiatedByMembership(membershipId: Long): OnlinePaymentTransaction?

// Member's own transaction history
@Query(value = """
    SELECT t.*, mp.name_en AS plan_name_en, mp.name_ar AS plan_name_ar
    FROM online_payment_transactions t
    JOIN memberships m ON m.id = t.membership_id
    JOIN membership_plans mp ON mp.id = m.plan_id
    WHERE t.member_id = :memberId
    ORDER BY t.created_at DESC
""", nativeQuery = true)
fun findByMemberId(memberId: Long): List<TransactionHistoryProjection>

// Staff view — member's transaction history scoped to club
@Query(value = """
    SELECT t.*, mp.name_en AS plan_name_en, mp.name_ar AS plan_name_ar
    FROM online_payment_transactions t
    JOIN memberships m ON m.id = t.membership_id
    JOIN membership_plans mp ON mp.id = m.plan_id
    WHERE t.member_id = :memberId
      AND t.club_id = :clubId
    ORDER BY t.created_at DESC
""", nativeQuery = true)
fun findByMemberIdAndClubId(memberId: Long, clubId: Long): List<TransactionHistoryProjection>
```

Interface projection:

```kotlin
interface TransactionHistoryProjection {
    val publicId: UUID
    val moyasarId: String
    val planNameEn: String?
    val planNameAr: String
    val amountHalalas: Long
    val status: String
    val paymentMethod: String?
    val createdAt: Instant
}
```

---

## Webhook signature verification

```kotlin
@Component
class MoyasarWebhookVerifier(
    @Value("\${moyasar.webhook-secret}") private val secret: String
) {
    fun verify(rawBody: ByteArray, signatureHeader: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val computed = mac.doFinal(rawBody).joinToString("") { "%02x".format(it) }
        return computed == signatureHeader
    }
}
```

The webhook controller reads the raw body as `ByteArray` via `@RequestBody body: ByteArray` to ensure the signature is computed over the exact bytes received (not re-serialised JSON).

---

## Frontend additions

### web-arena — `/membership` (modify existing route)

Add a "Pay Now" section when:
- `onlinePaymentEnabled = true` (from `GET /arena/portal-settings`)
- AND (`membership.status == 'pending_payment'` OR `member.status == 'LAPSED'`)

```
┌───────────────────────────────────────┐
│  Basic Monthly — 150 SAR              │
│  Status: Awaiting Payment             │
│                                       │
│  [ Pay Now with Moyasar  →  ]         │
│  mada  •  Visa/MC  •  Apple Pay       │
└───────────────────────────────────────┘
```

- "Pay Now" button calls `POST /arena/payments/initiate`
- On 201: `window.location.href = response.hostedUrl` (full redirect to Moyasar)
- On 403 ONLINE_PAYMENT_DISABLED: show inline message "Contact reception to complete payment"
- On 409 MEMBERSHIP_NOT_PAYABLE: hide button silently (should not normally reach this state)

### web-arena — `/payment-callback` (new route)

Moyasar redirects here after payment attempt: `/payment-callback?id={moyasarId}&status={paid|failed}`

- On mount: read `id` query param → call `GET /arena/payments/{moyasarId}/status`
- If status is `PAID`: show brief "Payment successful! Activating your membership..." then `navigate('/membership')`
- If status is `failed` or `cancelled`: show "Payment was not completed. You can try again from your membership page." with a link back to `/membership`
- Loading state shown while polling

**New i18n strings** (`ar.json` + `en.json`):
```
payment.pay_now
payment.methods_hint           // "mada · Visa/MC · Apple Pay"
payment.awaiting_payment
payment.disabled_message       // "Contact reception to complete payment"
payment.callback_success
payment.callback_activating
payment.callback_failed
payment.callback_retry_link
payment.history_title
payment.method.mada
payment.method.creditcard
payment.method.applepay
payment.status.initiated
payment.status.paid
payment.status.failed
payment.status.cancelled
```

### web-pulse — member profile Online Payments tab

New tab on the existing member profile page (alongside Membership, Payments, Notes, etc.):

```
┌──────────────────────────────────────────────────────┐
│  Online Payments                                      │
├────────────┬──────────┬──────────┬────────┬──────────┤
│  Date      │  Plan    │  Amount  │  Method│  Status  │
├────────────┼──────────┼──────────┼────────┼──────────┤
│  Apr 8     │  Basic   │  150 SAR │  mada  │  ✅ Paid │
│  Mar 1     │  Basic   │  150 SAR │  Visa  │  ✅ Paid │
│  Feb 28    │  Basic   │  150 SAR │  —     │  ❌ Failed│
└────────────┴──────────┴──────────┴────────┴──────────┘
```

- Status badge: green "Paid", red "Failed", grey "Initiated", grey "Cancelled"
- Read-only — no actions
- Visible to Owner and Branch Manager (gates on `online-payment:read`)

**New i18n strings** (`ar.json` + `en.json`):
```
member.online_payments_tab
member.online_payments_empty
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/payment/online/entity/OnlinePaymentTransaction.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/repository/OnlinePaymentTransactionRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/repository/TransactionHistoryProjection.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/client/MoyasarClient.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/client/MoyasarCreatePaymentRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/client/MoyasarPaymentResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/service/OnlinePaymentService.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/service/MoyasarWebhookVerifier.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/dto/InitiatePaymentRequest.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/dto/InitiatePaymentResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/dto/PaymentStatusResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/dto/TransactionHistoryResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/controller/OnlinePaymentArenaController.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/controller/MoyasarWebhookController.kt`
- `backend/src/main/kotlin/com/liyaqa/payment/online/controller/OnlinePaymentPulseController.kt`
- `backend/src/main/resources/db/migration/V22__online_payment_transactions.sql`
- `backend/src/test/kotlin/com/liyaqa/payment/online/service/OnlinePaymentServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/payment/online/service/MoyasarWebhookVerifierTest.kt`
- `backend/src/test/kotlin/com/liyaqa/payment/online/controller/OnlinePaymentControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-arena/src/routes/payment-callback/index.tsx`
- `apps/web-arena/src/api/payments.ts`
- `apps/web-arena/src/tests/payment-callback.test.tsx`
- `apps/web-pulse/src/components/member/OnlinePaymentsTab.tsx`
- `apps/web-pulse/src/api/onlinePayments.ts`
- `apps/web-pulse/src/tests/online-payments-tab.test.tsx`

### Files to modify

- `backend/.../audit/model/AuditAction.kt` — add `ONLINE_PAYMENT_INITIATED`, `ONLINE_PAYMENT_SUCCEEDED`, `ONLINE_PAYMENT_FAILED`
- `backend/.../permission/PermissionConstants.kt` — add `ONLINE_PAYMENT_READ = "online-payment:read"`
- `backend/DevDataLoader.kt` — seed `online-payment:read` to Owner + Branch Manager
- `backend/src/main/resources/application.yml` — add `moyasar.api-key`, `moyasar.publishable-key`, `moyasar.webhook-secret`, `moyasar.callback-url-base` (values from env vars)
- `backend/src/main/kotlin/.../security/SecurityConfig.kt` — permit `POST /api/v1/webhooks/moyasar` without JWT
- `apps/web-arena/src/routes/membership/index.tsx` — add Pay Now section
- `apps/web-arena/src/locales/ar.json` + `en.json` — add payment i18n strings
- `apps/web-pulse/src/routes/members/$memberId/index.tsx` (or profile component) — add Online Payments tab
- `apps/web-pulse/src/locales/ar.json` + `en.json` — add payment i18n strings

---

## Implementation order

### Step 1 — Flyway V22 + entity + repository
- Write `V22__online_payment_transactions.sql`
- Write `OnlinePaymentTransaction.kt`
- Write `OnlinePaymentTransactionRepository.kt` with 4 native queries
- Write `TransactionHistoryProjection.kt`
- Verify: `./gradlew flywayMigrate`

### Step 2 — Permissions + audit actions
- Add `ONLINE_PAYMENT_READ` to `PermissionConstants.kt`
- Add `ONLINE_PAYMENT_INITIATED`, `ONLINE_PAYMENT_SUCCEEDED`, `ONLINE_PAYMENT_FAILED` to `AuditAction.kt`
- Seed in `DevDataLoader`
- Add `moyasar.*` config to `application.yml`
- Permit webhook endpoint in `SecurityConfig`
- Verify: `./gradlew compileKotlin`

### Step 3 — MoyasarClient
- Implement `MoyasarClient` with `createPayment()` and `fetchPayment()`
- `MoyasarCreatePaymentRequest` + `MoyasarPaymentResponse` DTOs
- Use `RestTemplate` with basic auth header (`Authorization: Basic base64(apiKey:)`)
- In tests: mock `MoyasarClient` — do NOT make real HTTP calls in tests
- Verify: `./gradlew compileKotlin`

### Step 4 — MoyasarWebhookVerifier
- Implement `verify(rawBody, signatureHeader)` using HMAC-SHA256
- Unit test in `MoyasarWebhookVerifierTest`: valid signature passes, tampered body fails, wrong secret fails

### Step 5 — OnlinePaymentService
Implement:
- `initiatePayment(memberPublicId, membershipPublicId, clubId)` — all 9 business rules enforced; calls `MoyasarClient.createPayment()`; saves `OnlinePaymentTransaction` with `status = INITIATED`; logs `ONLINE_PAYMENT_INITIATED`
- `handleWebhook(rawBody, signature, payload)` — verifies signature (401 on fail); idempotency check; dispatches to `handlePaid()` or `handleFailed()`
- `handlePaid(transaction, payload)` — activates membership, creates Payment + Invoice, sets member status if lapsed, logs `ONLINE_PAYMENT_SUCCEEDED`
- `handleFailed(transaction)` — sets status FAILED, logs `ONLINE_PAYMENT_FAILED`
- `getStatus(moyasarId, memberId)` — fetches transaction, validates ownership (member can only see their own)
- `getMemberHistory(memberId)` — queries by memberId
- `getMemberHistoryForStaff(memberId, clubId)` — queries by memberId + clubId
- Verify: unit tests in `OnlinePaymentServiceTest`

### Step 6 — Controllers
- `OnlinePaymentArenaController` — 3 arena endpoints
- `MoyasarWebhookController` — `POST /api/v1/webhooks/moyasar`, reads raw body as `ByteArray`
- `OnlinePaymentPulseController` — 1 pulse endpoint
- All controllers with `@Operation`
- Verify: `./gradlew compileKotlin`

### Step 7 — Frontend: web-arena
- Modify `/membership` route: add Pay Now section (conditional on `onlinePaymentEnabled` + membership status)
- New `/payment-callback` route: reads `id` + `status` params, calls status endpoint, redirects to `/membership` on success
- `payments.ts` API functions
- Add i18n strings
- Verify: `npm run typecheck`

### Step 8 — Frontend: web-pulse
- Add "Online Payments" tab to member profile
- `OnlinePaymentsTab.tsx` component with transaction table, status badges
- `onlinePayments.ts` API function
- Add i18n strings
- Verify: `npm run typecheck`

### Step 9 — Tests

**Unit: `OnlinePaymentServiceTest`**
- `initiatePayment returns hostedUrl for pending_payment membership`
- `initiatePayment returns existing INITIATED transaction when duplicate requested`
- `initiatePayment throws 403 when onlinePaymentEnabled is false`
- `initiatePayment throws 409 MEMBERSHIP_NOT_PAYABLE for active membership`
- `initiatePayment works for LAPSED member`
- `handleWebhook activates membership on paid status`
- `handleWebhook sets member status to ACTIVE when member was LAPSED`
- `handleWebhook creates Payment and Invoice records on success`
- `handleWebhook is idempotent when transaction already PAID`
- `handleWebhook sets status FAILED on failed payment`
- `handleWebhook throws 401 on invalid signature`

**Unit: `MoyasarWebhookVerifierTest`**
- `verify returns true for correct signature`
- `verify returns false for tampered body`
- `verify returns false for wrong secret`

**Integration: `OnlinePaymentControllerIntegrationTest`**
- `POST /arena/payments/initiate returns 201 with hostedUrl`
- `POST /arena/payments/initiate returns 403 when feature disabled`
- `POST /arena/payments/initiate returns 409 for non-payable membership`
- `POST /arena/payments/initiate returns 201 for LAPSED member`
- `GET /arena/payments/{id}/status returns INITIATED status`
- `GET /arena/payments/history returns member's transactions`
- `POST /webhooks/moyasar returns 200 and activates membership on paid`
- `POST /webhooks/moyasar returns 200 and fails gracefully on failed payment`
- `POST /webhooks/moyasar returns 401 on invalid signature`
- `POST /webhooks/moyasar is idempotent on duplicate paid event`
- `GET /pulse/members/{id}/online-payments returns transactions`
- `GET /pulse/members/{id}/online-payments returns 403 without online-payment:read`

**Frontend: `payment-callback.test.tsx` (arena)**
- renders loading state while polling status
- redirects to /membership on PAID status
- renders failure message on failed status

**Frontend: `online-payments-tab.test.tsx` (pulse)**
- renders transaction list with correct status badges
- renders empty state when no transactions

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Manager | Others |
|---|---|---|---|
| `online-payment:read` | ✅ (seeded) | ✅ (seeded) | — |

---

## Definition of Done

- [ ] Flyway V22 runs cleanly: `online_payment_transactions` table with 4 indexes
- [ ] `OnlinePaymentTransaction` entity has no `deletedAt` — status transitions only
- [ ] `online-payment:read` seeded to Owner + Branch Manager
- [ ] 3 audit actions wired into service
- [ ] `MoyasarClient` wraps Moyasar REST API with Basic auth header
- [ ] Webhook endpoint is permitted without JWT in `SecurityConfig`
- [ ] Webhook signature verification rejects tampered requests with 401
- [ ] Webhook is idempotent — second call for already-PAID transaction is a no-op
- [ ] `initiatePayment` is idempotent — returns existing `hostedUrl` for duplicate requests
- [ ] `onlinePaymentEnabled = false` returns 403 ONLINE_PAYMENT_DISABLED
- [ ] Payment activation: Membership → active, startDate + endDate set, LAPSED member → ACTIVE
- [ ] `Payment` + `Invoice` records created on successful webhook (ZATCA fires automatically)
- [ ] All 4 repository queries use `nativeQuery = true`
- [ ] 5 endpoints live: 3 arena + 1 webhook + 1 pulse, all with `@Operation`
- [ ] web-arena: Pay Now button visible only when `onlinePaymentEnabled = true` AND membership is payable
- [ ] web-arena: `/payment-callback` handles success + failure redirects
- [ ] web-pulse: Online Payments tab renders on member profile
- [ ] All i18n strings added in Arabic and English (web-arena + web-pulse)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-arena or web-pulse
- [ ] `PROJECT-STATE.md` updated: Plan 24 complete, test counts, V22 noted
- [ ] `PLAN-24-moyasar.md` deleted before merging

When all items are checked, confirm: **"Plan 24 — Online Payments (Moyasar) complete. X backend tests, Y frontend tests."**

