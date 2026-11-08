# Green Basket 2.0

Grocery inventory + POS, upgraded from the Swing desktop app into a three-tier system. The original desktop app is
kept in `desktop/` and still runs on its own CSV files.

```
desktop/    Swing app (unchanged, Java 17+)           – the original
backend/    Spring Boot 3.3 REST API, PostgreSQL, JWT – Phases 1, 2 and 4
frontend/   React 18 + TypeScript + Tailwind (Vite)   – Phase 3
docker-compose.yml   PostgreSQL 16 for local development
```

| Phase | Status |
|---|---|
| 1 PostgreSQL, normalised schema, FKs/constraints, transactions, CSV migration | written |
| 2 Spring Boot, JPA, product/inventory/sales/purchasing APIs, validation, ProblemDetail errors, Swagger | written |
| 3 React + TypeScript + Tailwind admin, POS, reports | written |
| 4 JWT, roles, rotating refresh tokens, password reset, lockout | written |
| 5 barcode, loyalty, refunds, multi-branch, e-mail, batches | not started |
| 6 Python forecasting (Pandas / scikit-learn / FastAPI) | not started |
| 7 Docker for the app, GitHub Actions, deployment, monitoring | not started (only the dev database is containerised) |

> **Honest status:** this code was written in an environment that could not reach Maven Central, npm or a PostgreSQL
> server, so the Spring and React parts have **not been compiled or run**. What was checked: plain `javac` on all
> backend sources (no unresolved references between project classes; only third-party symbols are missing) and a
> TypeScript pass. Expect a few small compile or runtime fixes on first run – send the error text back and they are quick.

## Run it

Needs JDK 17+, Maven, Node 20+, Docker.

```bash
# 1. database
docker compose up -d db

# 2. API  (http://localhost:8080, Swagger UI at /swagger-ui.html)
cd backend
mvn spring-boot:run

# 3. web app (http://localhost:5173, proxies /api to :8080)
cd frontend
npm install
npm run dev
```

First start creates the schema with Flyway and a manager account **admin / Admin@1234**; you must change that
password at first sign-in. Set `ADMIN_PASSWORD` to choose a different initial one.

### Bring over the desktop app's data

Stop the API, then start it once with the desktop app's `data/` folder (default `desktop/data`, or whatever you
passed as `-Dgb.data`):

```bash
cd backend
MIGRATE_CSV=/path/to/data mvn spring-boot:run
```

Products, suppliers, categories, the stock ledger, sales (with their original receipt numbers and prices), users,
activity and settings are imported in one transaction. Old PBKDF2 passwords keep working and are re-hashed with bcrypt
at each user's first login. The import refuses to run if the database already has data.

### Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | local Docker database | PostgreSQL connection |
| `JWT_SECRET` | dev value | **Set in production**, 32+ chars (`openssl rand -base64 48`) |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | 15 / 14 | token lifetimes |
| `ADMIN_PASSWORD` | `Admin@1234` | initial manager password (empty database only) |
| `CORS_ORIGINS` | `http://localhost:5173` | allowed web origins |
| `MIGRATE_CSV` | – | desktop data folder to import once |

### Tests

```bash
cd backend && mvn test      # H2 in PostgreSQL mode, no Docker needed
```

Integration tests cover login, lockout, refresh rotation and reuse detection, password reset, checkout and void
(stock + ledger), role restrictions, concurrent-sale safety, the CSV import and legacy password verification.

## Design notes

- **Schema** (`db/migration/V1__schema.sql`): users, categories, suppliers, products, stock_movements (append-only
  ledger), sales + sale_items (price/cost/name snapshots so history survives edits), purchase_orders + items,
  activity_log, app_settings, refresh/reset tokens. Check constraints enforce price > 0, cost ≤ price, stock ≥ 0.
  Hibernate runs in `validate` mode – Flyway owns the schema.
- **Transactions:** checkout locks the product rows (`SELECT … FOR UPDATE`, in id order) and re-checks stock/expiry
  before deducting, so two cashiers cannot sell the last unit. Void restores stock and writes ledger rows in the same
  transaction. Products with history are archived, never deleted.
- **Security:** stateless JWT access tokens (15 min) + opaque refresh tokens stored as SHA-256 hashes, single-use
  with reuse detection (a replayed token revokes every session of that user). Five failed logins lock the account for
  15 minutes. Same error for unknown user and wrong password. Password reset tokens are hashed, expire in 30 minutes and
  are single-use; by default the token is written to the server log (`LoggingPasswordResetNotifier`) until Phase 5
  adds e-mail. Roles: `MANAGER` (everything) and `ASSISTANT` (POS, product lookup, own receipts, max 10 % discount).
- **Errors:** every failure is RFC 7807 `application/problem+json`.

## Known limits / next steps

- Access tokens are not checked against the database on each request, so a deactivated user keeps API access until
  the 15-minute token expires (their refresh tokens are revoked immediately).
- Reset e-mails, barcode scanning, loyalty, refunds, branches and batch tracking are Phase 5.
- CI, container images for the API/web app, and deployment are Phase 7.
