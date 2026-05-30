# Wallet Transfer Service — Design Document

## 1. Overview

A REST service that supports wallet-to-wallet transfers with guarantees around idempotency,
concurrency safety, double-entry ledger consistency, and safe state transitions.

**Tech stack:** Java 17, Spring Boot 3.x, PostgreSQL, Flyway, JUnit 5 + Testcontainers

---

## 2. API Contract

### POST /transfers

Creates a new wallet-to-wallet transfer.

**Request:**

```json
{
  "idempotencyKey": "abc123",
  "fromWalletId": "uuid",
  "toWalletId": "uuid",
  "amount": 100
}
```

**Responses:**

| Status | Condition |
|--------|-----------|
| `201 Created` | Transfer successfully processed |
| `200 OK` | Idempotent replay — returns original result |
| `400 Bad Request` | Validation failure (missing fields, amount <= 0, same wallet) |
| `404 Not Found` | Source or destination wallet not found |
| `422 Unprocessable Entity` | Insufficient balance |
| `409 Conflict` | Concurrent modification detected (retry) |

**Response body:**

```json
{
  "transferId": "uuid",
  "fromWalletId": "uuid",
  "toWalletId": "uuid",
  "amount": 100,
  "status": "PROCESSED",
  "createdAt": "2025-01-01T00:00:00Z"
}
```

### GET /wallets/{walletId} (optional)

Returns wallet details including current balance.

### GET /wallets/{walletId}/transactions (optional)

Returns ledger entries for a wallet.

---

## 3. Database Schema

All monetary amounts are stored as `BIGINT` (minor currency units, e.g., paise/cents)
to avoid floating-point precision issues.

### 3.1 wallets

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `balance` | `BIGINT` | `NOT NULL DEFAULT 0, CHECK (balance >= 0)` |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` (for optimistic locking fallback) |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

The `CHECK (balance >= 0)` constraint is a database-level safety net that prevents
negative balances even if application logic has a bug.

### 3.2 transfers

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `from_wallet_id` | `UUID` | `NOT NULL REFERENCES wallets(id)` |
| `to_wallet_id` | `UUID` | `NOT NULL REFERENCES wallets(id)` |
| `amount` | `BIGINT` | `NOT NULL, CHECK (amount > 0)` |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'PENDING'` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_transfers_from_wallet` on `from_wallet_id`
- `idx_transfers_to_wallet` on `to_wallet_id`

**Constraint:**
- `CHECK (from_wallet_id <> to_wallet_id)` — prevents self-transfers

### 3.3 ledger_entries

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `wallet_id` | `UUID` | `NOT NULL REFERENCES wallets(id)` |
| `transfer_id` | `UUID` | `NOT NULL REFERENCES transfers(id)` |
| `entry_type` | `VARCHAR(10)` | `NOT NULL, CHECK (entry_type IN ('DEBIT', 'CREDIT'))` |
| `amount` | `BIGINT` | `NOT NULL, CHECK (amount > 0)` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_ledger_wallet` on `wallet_id`
- `idx_ledger_transfer` on `transfer_id`

**Invariant:** Every transfer produces exactly two entries — one DEBIT and one CREDIT
of equal amounts. The sum of all CREDIT amounts minus DEBIT amounts across the entire
ledger must always be zero.

### 3.4 idempotency_records

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | `UUID` | `PRIMARY KEY` |
| `idempotency_key` | `VARCHAR(255)` | `NOT NULL, UNIQUE` |
| `transfer_id` | `UUID` | `NOT NULL REFERENCES transfers(id)` |
| `response_code` | `INTEGER` | `NOT NULL` |
| `response_body` | `TEXT` | `NOT NULL` |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` |

**Purpose:** When a duplicate request arrives with the same `idempotencyKey`, the system
returns the cached `response_code` and `response_body` without re-executing any logic.

---

## 4. Idempotency Strategy

### Mechanism

1. On every incoming `POST /transfers`, the service first queries `idempotency_records`
   by `idempotency_key`.
2. If a record exists, it returns the stored response immediately — no side effects.
3. If no record exists, the transfer executes within a transaction.
4. At the end of the transaction, the idempotency record is inserted with the response.
5. The `UNIQUE` constraint on `idempotency_key` prevents race conditions where two
   identical requests arrive simultaneously — only one insert succeeds.

### Properties

- **Durable:** Stored in PostgreSQL, survives process restarts.
- **Replay-safe:** Returns exact original response, not just "already exists".
- **No duplicate side effects:** Ledger entries, balance changes, and state transitions
  happen only once per idempotency key.

### Edge case: partial failure

If the transaction fails after creating the transfer but before committing, no
idempotency record is stored (it's in the same transaction). The client can safely
retry with the same key, and the transfer will execute fresh.

---

## 5. Concurrency Strategy

### Problem

Two concurrent requests could attempt to debit the same wallet simultaneously,
leading to a read-then-write race and potential double spending.

### Approach: Pessimistic Locking (SELECT ... FOR UPDATE)

1. Within the transfer transaction, both wallets are locked using
   `SELECT ... FOR UPDATE`.
2. **Wallets are locked in a deterministic order** (by UUID natural ordering) to
   prevent deadlocks. If transfer A locks wallet_1 then wallet_2, and transfer B
   also needs both, B will lock them in the same order and simply wait.
3. Once locked, the balance is checked. If insufficient, the transfer is marked `FAILED`.
4. Otherwise, balances are updated atomically within the transaction.

### Why pessimistic over optimistic?

- Financial operations need **strong consistency** — we cannot afford optimistic
  lock retries that might confuse error handling.
- `SELECT FOR UPDATE` is simple, well-understood, and PostgreSQL handles it efficiently.
- The lock scope is narrow (two rows in `wallets`) and short-lived (single transaction).

### Additional database safeguard

The `CHECK (balance >= 0)` constraint on `wallets.balance` acts as a final safety net.
Even if locking logic has a bug, PostgreSQL will reject the update.

---

## 6. Transfer State Machine

```
     ┌─────────┐
     │ PENDING │
     └────┬────┘
          │
    ┌─────┴─────┐
    ▼           ▼
┌──────────┐ ┌────────┐
│ PROCESSED│ │ FAILED │
└──────────┘ └────────┘
```

### Allowed transitions

| From | To | Trigger |
|------|----|---------|
| `PENDING` | `PROCESSED` | Transfer executed successfully |
| `PENDING` | `FAILED` | Insufficient balance or other business rule violation |

### Rules

- `PROCESSED` and `FAILED` are terminal states — no further transitions allowed.
- State transitions are validated in the domain model before persistence.
- The idempotency layer ensures a completed transfer is never re-executed.

---

## 7. Transfer Execution Flow

```
Client → POST /transfers {idempotencyKey, fromWalletId, toWalletId, amount}
  │
  ▼
[Controller] Validate request body
  │
  ▼
[Service] Check idempotency_records for idempotencyKey
  │
  ├── Found → return cached response (HTTP 200)
  │
  └── Not found → BEGIN TRANSACTION
        │
        ▼
      Lock wallets (ordered by ID, SELECT FOR UPDATE)
        │
        ▼
      Validate: both wallets exist, source balance >= amount
        │
        ├── Insufficient → create transfer FAILED, COMMIT, return 422
        │
        └── Sufficient →
              Create transfer (PENDING)
              Debit source wallet (balance -= amount)
              Credit dest wallet (balance += amount)
              Create DEBIT ledger entry
              Create CREDIT ledger entry
              Transition transfer → PROCESSED
              Store idempotency record
              COMMIT
              Return 201
```

---

## 8. SOLID Principles and Design Patterns

The codebase is designed around SOLID principles to keep it maintainable, testable,
and extensible.

### Single Responsibility Principle (SRP)

Each class has one reason to change:

| Class | Responsibility |
|-------|----------------|
| `TransferController` | HTTP request/response mapping only |
| `TransferService` | Transfer orchestration and business rules |
| `LedgerService` | Ledger entry creation and invariant enforcement |
| `IdempotencyService` | Idempotency check and record storage |
| `WalletRepository` | Wallet persistence and locking queries |
| `GlobalExceptionHandler` | Error-to-HTTP-response mapping |

No class mixes HTTP concerns with business logic or persistence logic.

### Open/Closed Principle (OCP)

- **Transfer status transitions** are defined as an enum with an allowed-transitions
  map. Adding a new state (e.g., `REVERSED`) means adding an entry to the map — no
  modification to existing transition logic.
- **Exception handling** uses `@RestControllerAdvice` — new exception types are handled
  by adding a new `@ExceptionHandler` method, without touching existing ones.
- **Validation** uses Jakarta Bean Validation annotations (`@NotNull`, `@Positive`,
  custom validators). New rules are added declaratively.

### Liskov Substitution Principle (LSP)

- Repository interfaces (e.g., `WalletRepository extends JpaRepository`) are used
  everywhere. Any conforming implementation can be substituted — the service layer
  never depends on concrete repository classes.
- In tests, mock implementations replace real repositories without breaking contracts.

### Interface Segregation Principle (ISP)

- Service interfaces expose only what their consumers need:
  - `TransferService` exposes `executeTransfer()` — the controller doesn't see
    internal methods like locking or ledger creation.
  - Repository interfaces inherit from Spring Data's focused interfaces
    (`JpaRepository`) rather than a monolithic DAO.

### Dependency Inversion Principle (DIP)

- The service layer depends on **repository interfaces**, not implementations.
- The controller depends on a **service interface**, not the concrete service class.
- Spring IoC wires concrete implementations at runtime.
- This enables isolated unit testing with mocks at every boundary.

### Design Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | Wallet locking order (deterministic by UUID) | Encapsulates the deadlock-prevention algorithm |
| **Template Method** | `@Transactional` on service methods | Spring manages the transaction lifecycle; service defines the steps |
| **Repository** | Spring Data JPA repositories | Abstracts persistence, keeps domain clean |
| **DTO / Request-Response** | `TransferRequest`, `TransferResponse` | Decouples API contract from domain entities — entities can evolve independently |
| **Domain Model** | `Transfer.transitionTo(status)` | State machine logic lives in the entity, not scattered across services |
| **Factory Method** | `Transfer.create(...)`, `LedgerEntry.debit(...)` / `LedgerEntry.credit(...)` | Encapsulates entity construction rules and invariants |
| **Global Exception Handler** | `@RestControllerAdvice` | Centralizes error mapping, keeps controllers thin |

### Package Structure

```
com.wallet
├── controller/          # Thin REST handlers (SRP: HTTP only)
│   ├── TransferController
│   └── dto/             # Request/Response DTOs (ISP: API-specific shapes)
│       ├── TransferRequest
│       └── TransferResponse
├── service/             # Business logic interfaces + implementations (DIP)
│   ├── TransferService           (interface)
│   ├── TransferServiceImpl       (orchestration)
│   ├── LedgerService             (interface)
│   ├── LedgerServiceImpl         (ledger creation)
│   ├── IdempotencyService        (interface)
│   └── IdempotencyServiceImpl    (dedup logic)
├── repository/          # Spring Data JPA interfaces (LSP, DIP)
│   ├── WalletRepository
│   ├── TransferRepository
│   ├── LedgerEntryRepository
│   └── IdempotencyRecordRepository
├── domain/              # Entities, enums, domain validation (SRP, OCP)
│   ├── Wallet
│   ├── Transfer
│   ├── TransferStatus
│   ├── LedgerEntry
│   ├── LedgerEntryType
│   └── IdempotencyRecord
├── exception/           # Custom exceptions (OCP: extend, don't modify)
│   ├── InsufficientBalanceException
│   ├── WalletNotFoundException
│   ├── InvalidTransferException
│   └── GlobalExceptionHandler
└── config/              # Spring configuration
    └── AppConfig
```

---

## 9. Architecture Layers

```
┌────────────────────────────────────┐
│  Controller (Handler) Layer        │  Request validation, HTTP mapping
├────────────────────────────────────┤
│  Service Layer                     │  Business logic, orchestration,
│                                    │  idempotency, transaction mgmt
├────────────────────────────────────┤
│  Repository Layer                  │  JPA repositories, DB queries,
│                                    │  locking queries
├────────────────────────────────────┤
│  Domain Layer                      │  Entities, enums, state machine,
│                                    │  validation rules
└────────────────────────────────────┘
```

**Dependency rule:** Each layer depends only on the layer below it. Controllers never
access repositories directly. Services never construct HTTP responses. Domain entities
have zero Spring dependencies — they are pure Java objects with business logic.

**Principle:** Handlers are thin — they validate input and delegate to the service.
Business logic never leaks into controllers or repositories.

---

## 10. Testing Strategy

### Unit Tests
- **Domain model:** State transition validation (valid and invalid)
- **Service layer:** Mocked repositories — happy path, insufficient balance,
  wallet not found, idempotent replay

### Integration Tests (Testcontainers + PostgreSQL)
- **End-to-end transfer:** POST creates transfer, 2 ledger entries, correct balances
- **Idempotency:** Same key returns same response, no duplicate entries
- **Insufficient balance:** Returns 422, transfer in FAILED state
- **Concurrency:** Multiple threads debit same wallet — verify no double spend,
  balances remain consistent
- **Ledger invariant:** Sum of all debits equals sum of all credits

### Test philosophy
- Focus on **behavior**, not implementation details.
- Red → Blue → Green (write failing test → implement → refactor).
- Concurrency tests use `ExecutorService` with multiple threads.

---

## 11. Assumptions and Tradeoffs

1. **Amounts as BIGINT:** All amounts are in minor currency units (e.g., paise).
   The API accepts integer amounts only. This avoids floating-point issues entirely.

2. **Pre-seeded wallets:** Wallets are assumed to exist (seeded via migration or
   separate process). The assignment focuses on transfers, not wallet creation.
   We may add a simple wallet creation endpoint for convenience.

3. **No authentication/authorization:** Out of scope for this assignment.

4. **Single-region, single-instance:** The concurrency strategy uses database-level
   locks, which work correctly for a single PostgreSQL instance. Distributed
   deployments would need additional coordination.

5. **Pessimistic locking chosen over optimistic:** Simpler mental model for
   financial operations, at the cost of slightly reduced throughput under
   extreme contention. Acceptable for this use case.

6. **Idempotency key scoped globally:** Not per-user. In production, you'd
   typically scope it per API key or user ID.
