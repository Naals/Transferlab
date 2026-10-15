# TransferLab

A Java backend portfolio project exploring reliable internal transfers
and balanced financial records using fictional funds.

## Stack

- Java 21
- Spring Boot 3.5.16
- Maven
- JUnit 5 and AssertJ
- GitHub Actions

## Day 1 implementation

- Exact KZT money values expressed in minor units.
- Immutable customer-account snapshots.
- Overdraft and inactive-account checks.
- Balanced, immutable journal transactions.
- Transfer planning with source ownership validation.
- Domain tests and an HTTP health test.

The planner calculates a transfer. It does not persist or execute one.

## Modules

- `shared.domain`: money values.
- `accounts.domain`: customer-account rules.
- `ledger.domain`: journal entries and balanced transactions.
- `transfers.domain`: transfer calculation.

Domain classes have no Spring or persistence dependencies.

## Run

Requirements: JDK 21 and Maven 3.6.3 or newer.

```bash
mvn --batch-mode --no-transfer-progress verify
mvn spring-boot:run
```

In another terminal:

```bash
curl http://127.0.0.1:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Financial conventions

100 minor units represent 1 KZT.

Customer balances are platform liabilities:
a debit decreases a customer balance; a credit increases it.

Each journal transaction must have equal debit and credit totals.
Original journal entries will be preserved when reversals are added.

## Architecture decision

Start with one deployable application and PostgreSQL.

The future posting service will commit the transfer, journal entries,
balance updates, and idempotency result in one database transaction.

Accounts will be locked in a consistent order before balance validation.
Immutable Java objects alone do not provide concurrency safety.

Kafka and the transactional outbox will follow after the transfer
transaction boundary has integration coverage.

## Current limitations

No persistence, authentication, transfer HTTP API, database locking,
durable idempotency, reversals, or reconciliation is implemented yet.

Actor identity must eventually come from authenticated security context.

The business transfer limit is still to be defined. Current arithmetic
rejects values that overflow a Java long.

## Next step

PostgreSQL schema, Flyway migrations, transactional posting,
and integration tests for concurrent debits and rollback.