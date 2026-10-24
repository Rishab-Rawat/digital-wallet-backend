# Five-minute demo recording script

**0:00–0:30 — Startup.** Show `docker compose up --build`, the healthy Postgres/API containers, and `/actuator/health`.

**0:30–1:20 — Wallet and transfer.** List Asha and Rahul demo wallets, perform a small peer transfer with a new `Idempotency-Key`, then show both balances and statement entries. Point out the debit/credit pair.

**1:20–1:55 — Retry safety.** Send the exact same request with the same key. Show the same transfer ID and no duplicate ledger entries. (Record only after the idempotency concurrency test is passing.)

**1:55–2:40 — Fraud hold.** Send an amount over the configured large-transfer threshold. Show status `HELD` and unchanged balances, then release it via `/api/admin/transfers/{id}/review` and show posted entries.

**2:40–3:35 — Reconciliation.** Call `/api/admin/reconciliation/run`, show the run status and problem endpoint. In a disposable local DB only, demonstrate a controlled corruption and the corresponding mismatch report; do not mutate a shared database.

**3:35–4:25 — Statement Q&A.** Ask a supported question with explicit dates and show the answer plus source transfer IDs. Demonstrate an unsupported prompt is rejected.

**4:25–5:00 — Design summary.** Show `docs/schema.md`, `docs/isolation.md`, Flyway migration, and tests. Explicitly state remaining gaps listed in README.
