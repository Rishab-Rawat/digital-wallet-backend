# Isolation lab (run against local Postgres)

The application transfer path uses **READ COMMITTED plus deterministic row locks**. It locks both accounts with `SELECT ... ORDER BY id FOR UPDATE`, re-reads the source balance while holding the lock, then inserts transfer and ledger lines and updates balances before commit. This is appropriate for this invariant because all writers to an account must acquire the same row lock. `SERIALIZABLE` can detect broader anomalies but introduces serialization failures that callers must retry and can reduce throughput.

## Two-session experiments

Use two `psql` sessions connected to the same database. Seed a wallet and note the ID.

### Read Committed

Session A:
```sql
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT balance_paise FROM accounts WHERE id = 1;
-- pause here
SELECT balance_paise FROM accounts WHERE id = 1;
COMMIT;
```
Session B, while A is paused:
```sql
UPDATE accounts SET balance_paise = balance_paise + 100 WHERE id = 1;
COMMIT;
```
Expected: A's second SELECT sees B's committed value because each statement gets a fresh snapshot.

### Repeatable Read

Repeat with `BEGIN ISOLATION LEVEL REPEATABLE READ;`. Expected: A sees the original value in both SELECTs (snapshot isolation).

### Serializable write skew / conflict

Session A and B each begin `SERIALIZABLE`, read a shared invariant, then update different rows based on that read and commit. PostgreSQL may abort one with SQLSTATE `40001` (`could not serialize access due to read/write dependencies`). Retry the entire transaction when using this level.

### Repeatable Read with a join

Use `BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT a.balance_paise,u.name FROM accounts a JOIN users u ON u.id=a.user_id WHERE a.id=1;` in A, update the joined row in B and commit, then repeat A's join. Expected: A retains its snapshot for both tables.

## Broken version vs fixed version

The unsafe pattern is `SELECT balance`, application check, then `UPDATE` without locking. Two requests can both observe the same funds and overdraw or overwrite assumptions. The fixed path locks source and destination rows in ascending ID order and validates funds after acquiring the lock. A production load test should issue 100 concurrent ₹10 requests from ₹500, assert exactly 50 succeed, zero negative balances and equal total debit/credit, then repeat 20 times. This repository includes the transaction implementation but the 20-run stress acceptance test still needs to be executed in a Docker-capable CI environment before claiming it passes.
