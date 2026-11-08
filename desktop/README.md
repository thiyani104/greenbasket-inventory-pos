# Green Basket Inventory System

Java Swing desktop app (Java 17+). Open in NetBeans (main class `main.Main`) or run:

    javac -encoding UTF-8 -d out $(find src -name '*.java') && java -cp out main.Main

First sign-in: `admin` / `1234` (you are asked to change it). Data is stored in `./data` as CSV files
(override with `-Dgb.data=PATH`). An existing `products.txt` is imported automatically once.

Run the tests: `./test.sh`  (21 tests, no dependencies).

## Features
- Role-based access: Store Manager / Sales Assistant, salted PBKDF2 passwords, login lockout, last-manager guard
- Point of sale: search/ID entry, quantity buttons, discount limits per role, tax, cash/card/mobile, change, receipts (print/save/copy), F2/F9 shortcuts
- Products: search, filters, sort, add/edit/delete, CSV import/export, margin and expiry tracking
- Stock & Orders: alerts, editable reorder plan with purchase orders per supplier, receive delivery, stock ledger, slow movers
- Sales history with void (restores stock), Reports (any period, vs previous period, hourly/weekday patterns, cashiers, end-of-day cash-up)
- Staff accounts, audit activity log, settings (tax, currency, targets), backup/restore + daily auto-backup, sample data
