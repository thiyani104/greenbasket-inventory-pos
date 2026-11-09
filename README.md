# Green Basket – Inventory & POS System

A full-stack grocery store management system: product and stock control, a point-of-sale checkout with receipts,
purchase orders, sales reports and secure role-based access.

**Stack:** Java 17 · Spring Boot 3 · Spring Security (JWT) · Spring Data JPA · PostgreSQL · Flyway · React 18 ·
TypeScript · Tailwind CSS · Vite · Docker

<!-- Add screenshots here, e.g.
![Dashboard](docs/screenshots/dashboard.png)
![POS](docs/screenshots/pos.png)
-->

## Features

**Inventory**
- Product catalogue with categories, suppliers, cost/selling price, reorder level and expiry date
- Search, filter by status (low stock, out of stock, expiring, expired) and sort
- Stock ledger: every receive, sale, void, write-off and recount is recorded with user and time
- Archive products instead of deleting, so sales history stays intact

**Point of sale**
- Cart, discounts, tax, cash / card / mobile payment and change calculation
- Atomic checkout: stock is deducted, the receipt is created and the ledger is written in one transaction
- Row-level locking, so two cashiers cannot sell the last unit
- Expired or out-of-stock items cannot be sold; managers can void a sale and return the stock

**Purchasing**
- Purchase orders with a draft → ordered → received flow, including partial deliveries
- Receiving stock updates quantities and the stock ledger automatically

**Reports and dashboard**
- Daily sales target, last 7 days chart, stock alerts and stock value
- Sales by day, hour, weekday, category, cashier and payment method
- Top products, profit and margin, reorder list, expiring stock and dead stock

**Users and security**
- Roles: **Manager** (full access) and **Sales Assistant** (POS and product lookup, discount capped at 10%)
- JWT access tokens with rotating, single-use refresh tokens (reuse revokes all sessions)
- Password reset by token, forced password change for temporary passwords
- Account lockout after 5 failed logins, activity log of all important actions

## Architecture

```
React + TypeScript (Vite, Tailwind)  ──HTTP/JSON──▶  Spring Boot REST API  ──JPA──▶  PostgreSQL
        frontend/                                          backend/                    (Flyway migrations)
```

- `backend/` – REST API, business rules, security, database migrations, tests
- `frontend/` – admin dashboard and POS interface
- `docker-compose.yml` – PostgreSQL for local development

### Database

Normalized schema with foreign keys and check constraints (price > 0, cost ≤ price, stock ≥ 0): `users`,
`categories`, `suppliers`, `products`, `stock_movements`, `sales`, `sale_items`, `purchase_orders`,
`purchase_order_items`, `activity_log`, `app_settings`, `refresh_tokens`, `password_reset_tokens`.
Sale lines keep a snapshot of name, category, price and cost, so old receipts never change when a product is edited.
Flyway owns the schema and Hibernate runs in `validate` mode.

## Getting started

**Requirements:** JDK 17+, Maven, Node.js 20+, Docker

```bash
# 1. Start PostgreSQL
docker compose up -d db

# 2. Start the API  → http://localhost:8080  (Swagger UI: /swagger-ui.html)
cd backend
mvn spring-boot:run

# 3. Start the web app  → http://localhost:5173
cd frontend
npm install
npm run dev
```

On the first start the database schema is created automatically and an initial manager account is added:

| Username | Password |
|---|---|
| `admin` | `Admin@1234` |

You will be asked to change this password at first sign-in. Set `ADMIN_PASSWORD` to choose a different initial one.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/greenbasket` | Database URL |
| `DB_USER` / `DB_PASSWORD` | `greenbasket` / `greenbasket` | Database login |
| `JWT_SECRET` | development value | **Set this in production** (32+ characters, e.g. `openssl rand -base64 48`) |
| `JWT_ACCESS_MINUTES` | `15` | Access token lifetime |
| `JWT_REFRESH_DAYS` | `14` | Refresh token lifetime |
| `ADMIN_PASSWORD` | `Admin@1234` | Initial manager password (only used on an empty database) |
| `CORS_ORIGINS` | `http://localhost:5173` | Allowed web origins |

## API overview

Interactive documentation is available at `http://localhost:8080/swagger-ui.html` once the API is running.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `/refresh`, `/logout`, `/change-password`, `/forgot-password`, `/reset-password` |
| Products | `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}`, `POST /api/products/{id}/stock` |
| Catalogue | `/api/categories`, `/api/suppliers` |
| Sales | `POST /api/sales` (checkout), `GET /api/sales`, `POST /api/sales/{id}/void` |
| Purchasing | `/api/purchase-orders` (+ `/order`, `/receive`, `/cancel`) |
| Reports | `/api/reports/dashboard`, `/summary`, `/reorder`, `/expiring`, `/dead-stock` |
| Admin | `/api/users`, `/api/settings`, `/api/activity`, `/api/stock-movements` |

Errors are returned as RFC 7807 `application/problem+json`.

## Testing

```bash
cd backend
mvn test
```

Integration tests run against an in-memory H2 database (PostgreSQL mode), so no Docker is needed. They cover login,
lockout, refresh-token rotation and reuse detection, password reset, role restrictions, checkout and void (stock and
ledger), concurrent sales of the last unit, stock adjustments, product validation and search, purchase orders and
reports.

## Project structure

```
backend/
  src/main/java/com/greenbasket/
    config/      security and bootstrap configuration
    domain/      JPA entities
    repository/  Spring Data repositories
    service/     business logic and transactions
    security/    JWT, authentication, password encoding
    web/         REST controllers, DTOs, error handling
  src/main/resources/db/migration/   Flyway SQL migrations
  src/test/                          integration tests
frontend/
  src/pages/       Dashboard, Products, POS, Sales, Purchasing, Reports, Users, Activity, Settings
  src/components/  shared UI components
  src/api/         typed API client
docker-compose.yml
```

## Roadmap

- Barcode scanning at the POS
- Customer loyalty points
- Returns and refunds
- Multi-branch inventory
- Email notifications
- Batch and expiry tracking
- Sales forecasting and reorder recommendations (Python, Pandas, scikit-learn)
- Docker images, CI with GitHub Actions, deployment and monitoring

## License

Add a license of your choice (for example MIT) in a `LICENSE` file.
