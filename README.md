# Wallet Transfer Service

A wallet-to-wallet money transfer service built with **Java 21**, **Spring Boot 3.4**, and **PostgreSQL 16**. Supports idempotent transfers, concurrent safety via pessimistic locking, and a double-entry ledger.

## Tech Stack

| Component        | Technology                          |
|------------------|-------------------------------------|
| Language         | Java 21                             |
| Framework        | Spring Boot 3.4.1                   |
| Database         | PostgreSQL 16                       |
| ORM              | Spring Data JPA / Hibernate         |
| Migrations       | Flyway                              |
| Build            | Maven                               |
| Testing          | JUnit 5, Mockito, MockMvc           |
| Containerisation | Docker Compose                      |

## Architecture

```
Controller (HTTP) → Service (Business Logic) → Repository (JPA) → Domain (Entities)
```

- **Layered architecture** — each layer depends only on the layer below
- **SOLID principles** throughout — see [`docs/design.md`](./docs/design.md)
- **Double-entry ledger** — every transfer creates a DEBIT and CREDIT entry
- **State machine** — transfers move `PENDING → PROCESSED` or `PENDING → FAILED`
- **Idempotency** — duplicate requests return the cached response
- **Deadlock prevention** — wallets locked in deterministic UUID order

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

## Setup & Run

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Build the project

```bash
mvn clean compile
```

### 3. Run the application

```bash
mvn spring-boot:run
```

The service starts at `http://localhost:8080`.

### 4. Stop PostgreSQL

```bash
docker compose down
```

## API

### POST /transfers

Create a wallet-to-wallet transfer.

**Request:**
```json
{
  "idempotencyKey": "unique-key-123",
  "fromWalletId": "11111111-1111-1111-1111-111111111111",
  "toWalletId": "22222222-2222-2222-2222-222222222222",
  "amount": 1000
}
```

**Response (201 Created):**
```json
{
  "transferId": "a1b2c3d4-...",
  "fromWalletId": "11111111-1111-1111-1111-111111111111",
  "toWalletId": "22222222-2222-2222-2222-222222222222",
  "amount": 1000,
  "status": "PROCESSED",
  "createdAt": "2026-06-01T17:00:00Z"
}
```

**Idempotent replay** returns `200 OK` with the same response.

**Error responses:**
| Status | Condition                  |
|--------|----------------------------|
| 400    | Invalid input / same wallet |
| 404    | Wallet not found            |
| 422    | Insufficient balance        |

## Testing

### Run all tests

```bash
# Ensure PostgreSQL is running
docker compose up -d

# Run tests
mvn test
```

### Test breakdown

| Suite                        | Count | Type        | Covers                                            |
|------------------------------|-------|-------------|---------------------------------------------------|
| Domain tests                 | 30    | Unit        | Wallet, Transfer, LedgerEntry, TransferStatus     |
| Service tests                | 8     | Unit        | TransferService with mocked repos                 |
| API integration tests        | 8     | Integration | End-to-end HTTP through real PostgreSQL            |
| Concurrency tests            | 3     | Integration | Double-spend prevention, ledger balance, idempotency under concurrency |
| Context load test            | 1     | Integration | Spring context boots successfully                 |

**Total: 50 tests**

## Seed Data

The migration `V2__seed_test_wallets.sql` creates three wallets:

| Wallet ID                              | Initial Balance |
|----------------------------------------|-----------------|
| `11111111-1111-1111-1111-111111111111` | 10,000          |
| `22222222-2222-2222-2222-222222222222` | 5,000           |
| `33333333-3333-3333-3333-333333333333` | 0               |

## Project Structure

```
src/main/java/com/wallet/
├── controller/          # REST endpoints
│   └── dto/             # Request/Response records
├── service/             # Business logic interfaces + implementations
├── repository/          # Spring Data JPA interfaces
├── domain/              # Entities, enums, state machine
├── exception/           # Custom exceptions + global error handler
└── config/              # Spring configuration

src/main/resources/
├── application.yml      # App configuration
└── db/migration/        # Flyway SQL migrations

src/test/java/com/wallet/
├── domain/              # Unit tests for domain models
├── service/             # Unit tests for services (mocked)
└── integration/         # Integration + concurrency tests
```

## Design Document

See [`docs/design.md`](./docs/design.md) for detailed documentation on:
- Database schema and rationale
- Idempotency strategy
- Concurrency and locking strategy
- State machine design
- SOLID principles applied
- Testing strategy
