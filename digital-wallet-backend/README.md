# Digital Wallet Backend (Java + Spring Boot)

A learning/reference implementation of a ledger-backed INR wallet. Built with Java 21, Spring Boot, PostgreSQL 16, JDBC and Flyway. The transfer path uses explicit SQL and row locks so the money movement is visible and auditable.

## Run

Prerequisites: Docker Desktop / Docker Engine with Compose.

```bash
docker compose up --build
```

API is available at `http://localhost:8080/api`; health check: `http://localhost:8080/actuator/health`. Compose seeds demo users and wallets for local exploration. Demo credentials are not authentication; do not expose this setup publicly.

Stop with `Ctrl+C`; reset the local database with `docker compose down -v`.

## Example flow

Demo users: `asha@example.test` (user id 1 in a fresh seeded DB) and `rahul@example.test` (id 2); wallet IDs are generated and should be discovered from `GET /api/users/{userId}/wallets`.

```bash
curl http://localhost:8080/api/users/1/wallets
# First, discover the actual wallet IDs in the response, then replace <ASHA_WALLET_ID> and <RAHUL_WALLET_ID> below.
curl -X POST http://localhost:8080/api/users/1/top-ups \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-topup-001' \
  -d '{"walletId":<ASHA_WALLET_ID>,"amountPaise":100000,"note":"demo funding"}'
curl -X POST http://localhost:8080/api/users/1/transfers \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-transfer-001' \
  -d '{"fromWalletId":<ASHA_WALLET_ID>,"toWalletId":<RAHUL_WALLET_ID>,"amountPaise":1000,"note":"demo"}'
curl -X POST http://localhost:8080/api/admin/reconciliation/run
```

All amounts are integer paise. ₹10 is `1000` paise.

## API contract

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/users` | Create user |
| POST | `/api/wallets` | Create wallet |
| GET | `/api/users/{userId}/wallets` | List user's wallets |
| GET | `/api/users/{userId}/wallets/{walletId}/balance` | Read balance |
| POST | `/api/users/{userId}/top-ups` | Top up from system cash-in account |
| POST | `/api/users/{userId}/withdrawals` | Withdraw to system cash-out account |
| POST | `/api/users/{userId}/transfers` | Peer transfer |
| GET | `/api/users/{userId}/wallets/{walletId}/statement?page=0&size=50` | Newest-first statement |
| POST | `/api/users/{userId}/statement/ask` | Statement Q&A |
| POST | `/api/admin/reconciliation/run` | Run reconciliation now |
| GET | `/api/admin/reconciliation/runs/{runId}/problems` | List recorded mismatches |
| POST | `/api/admin/transfers/{transferId}/review` | Release/reject held transfer |

Every POST money-movement request requires `Idempotency-Key`. Missing keys return 400; reusing a key with a different body returns 422. Errors share `{ "code": "...", "message": "...", "request_id": "..." }`.

## Design decisions

- **Double entry:** a completed transfer creates a debit and a matching credit, then updates stored balances in the same transaction. Held transfers do not post ledger entries or change balances until released.
- **Balance strategy:** store balances for fast reads; ledger entries remain the accounting source of truth and nightly reconciliation detects divergence.
- **Account table:** wallet and system accounts share `accounts` to keep ledger relationships and posting logic consistent. Only wallet accounts have `user_id`; system accounts can go negative.
- **Identifiers:** email and phone are user-supplied alternate keys, not primary keys.
- **Isolation:** READ COMMITTED plus `SELECT ... FOR UPDATE` on both account rows, ordered by ID. Lock ordering reduces deadlock risk. A high-throughput production system should still implement bounded retries for deadlocks and test actual contention.
- **Ledger immutability:** PostgreSQL trigger rejects ledger UPDATE/DELETE; fix errors with a reversal transfer.
- **Idempotency:** unique `(user_id, key)`, SHA-256 request hash and saved JSON response. In a production implementation, exercise concurrent duplicate requests under PostgreSQL and refine transaction/savepoint handling for unique-key races.
- **Fraud:** configurable `risk_rules`; large transfers are held and velocity is flagged. Held transfers post no entries until admin release. This is an illustrative ruleset, not a complete fraud system.
- **LLM boundary:** Q&A currently uses a deterministic mock parser and parameterized SQL, never LLM-generated SQL. A provider interface and JSON schema validation should be added before wiring a real LLM.
- **Reconciliation:** scheduled at 02:00 by default and manually triggerable. It records problems and does not auto-correct data.

## Verification

```bash
mvn test
```

Integration tests use Testcontainers and require Docker. Build the container with `docker compose up --build` to verify Flyway migrations and startup. Check SQL query plans at scale with `EXPLAIN (ANALYZE, BUFFERS)` using seeded data.

## Known gaps / not yet verified

This is a substantial starter implementation, not a certified payment system. Before claiming full assignment acceptance, address and verify:

1. **Security:** authentication/authorization is not implemented. `userId` path parameters are not proof of identity; add Spring Security and enforce caller ownership/admin roles before deployment.
2. **Idempotency concurrency:** the unique-key replay path needs dedicated concurrent integration tests and transaction-safe conflict handling; verify identical responses and exactly one transfer for 10 simultaneous retries.
3. **Concurrency acceptance:** the 100-request × 20-run stampede test is documented but not included as an executable stress test yet.
4. **Reconciliation semantics:** checks cover global totals, per-transfer balance/missing entries, stored balance, overdrafts and missing entries. The exact “three corruptions and nothing else” acceptance test against 10,000 seeded transfers and million-row EXPLAIN plan has not been run here.
5. **Reversals and fee transfer:** schema supports reversal references and fee system account, but dedicated reversal and fee endpoints/workflows are not implemented.
6. **Q&A:** parser is a deterministic mock, not an external LLM. Natural-language “last month” handling and all ten numeric expected-answer fixtures need implementation/testing. The counterparty query currently matches user-owned account names and should be improved for transfers to system accounts and richer identity matching.
7. **Seed scale:** Compose seeds two users and two wallets for a demo, not the required 1,000 users, 2,000 wallets and 100,000 transfers. Add a repeatable bulk seed script for the assignment acceptance run.
8. **Fraud:** risk thresholds are illustrative. Add an administrative rule-management endpoint, latency measurements and realistic indexes/benchmarks.
9. **Transfer status audit:** transitions made by implemented paths are logged, but a database trigger or centralized transition service would be stronger protection against future code paths bypassing audit.
10. **Demo video:** no video is included; use `docs/demo-script.md` to record one locally.

Do not use this project with real money or production user data without completing these controls and tests.
