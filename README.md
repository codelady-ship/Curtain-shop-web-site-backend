# Perde.az: curtain shop API (Spring Boot)

[![CI](https://github.com/codelady-ship/Curtain-shop-web-site-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/codelady-ship/Curtain-shop-web-site-backend/actions/workflows/ci.yml)
[![Frontend demo](https://img.shields.io/badge/frontend%20demo-GitHub%20Pages-C5A059)](https://codelady-ship.github.io/Curtain-shop-web-site-frontend/)

> AZ: Pərdə mağazası üçün REST API: məhsullar, müraciətlər (lead), bannerlər, partnyorlar və admin panel. Render + Neon üzərində pulsuz yerləşdirmə üçün hazırdır.

REST API for the [Perde.az curtain shop frontend](https://github.com/codelady-ship/Curtain-shop-web-site-frontend). It serves the product catalog, stores lead and order requests from the site's forms, and powers the admin panel (products, banners, partners, leads, visitor stats).

**Status:** deploy-ready but **not deployed yet**. The frontend's GitHub Pages demo runs on bundled demo data until this API is live. To deploy it, follow [Deploy (Render + Neon)](#deploy-render--neon) below.

## Features

- Product catalog with colour and size options, paging, search, category/room filters and sorting
- Lead forms (order, promo code, free measurement, room photo upload) with phone validation and image upload
- Admin auth: JWT, BCrypt passwords, login rate limit, password reset by email or WhatsApp code
- Banners and partners managed from the admin panel; visitor and lead statistics
- Deny-by-default access policy (`AdminAuthInterceptor`): every `/api` route is admin-only unless it is on the public allow-list
- Liquibase migrations, `GET /api/health` for the platform health check, CORS from an env var

## Tech stack

Java 21, Spring Boot 3.2 (Web, Data JPA, Validation, Mail), PostgreSQL, Liquibase, MapStruct, Lombok, springdoc-openapi, Gradle (wrapper included), Docker.

## Run locally

Requirements: JDK 21 and a local PostgreSQL. Gradle does not need to be installed because the wrapper (`./gradlew`) downloads it.

```bash
createdb properde_db                       # or any name; Liquibase creates the tables on first start

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/properde_db
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=<your local password>
export ADMIN_PASSWORD=<at least 12 characters>
export SPRING_PROFILES_ACTIVE=dev          # dev-only JWT secret; never use on a server

./gradlew bootRun                          # http://localhost:8080  (Windows: gradlew.bat bootRun)
```

- Health check: http://localhost:8080/api/health returns `{"status":"UP"}`
- Swagger UI (local only): http://localhost:8080/swagger-ui/index.html
- Tests (no database needed): `./gradlew test`
- Jar: `./gradlew bootJar`, then `java -jar build/libs/<name>.jar`

All variables are listed in [`.env.example`](.env.example) and in the table below. Start the frontend with `npm start`: it proxies `/api` to `http://127.0.0.1:8080`.

## Main endpoints

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/health` | public |
| GET | `/api/products/all`, `/api/products/filter`, `/api/products/{id}` | public |
| GET | `/api/banners`, `/api/partners` | public |
| POST | `/api/leads`, `/api/leads/submit` (JSON or multipart with an image), `/api/leads/upload` | public |
| POST | `/api/analytics/hit` | public |
| POST | `/api/admin/auth/login`, `/forgot-password`, `/reset-password` | public |
| GET, PATCH, DELETE | `/api/leads/**` (list, stats, status, contacted, promo, soft delete) | admin |
| GET, POST, PUT, DELETE | `/api/admin/products/**`, `/api/admin/banners/**`, `/api/admin/partners/**`, `/api/admin/visitors/**` | admin |
| GET, PUT, POST | `/api/admin/auth/profile`, `/api/admin/auth/change-password` | admin |
| GET | `/api/analytics/dashboard` | admin |

## Owner steps to go live (summary)

1. **Neon:** create a free project, then copy the host, database, user and password from **Connect** (direct connection, not the pooler).
2. **Render:** go to **New → Blueprint**, pick this repo on branch `main` and enter `SPRING_DATASOURCE_URL` (`jdbc:postgresql://<host>/<db>?sslmode=require`), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` and `ADMIN_PASSWORD`, then click **Apply**. Render generates `JWT_SECRET`.
3. Check that `https://curtain-shop-api.onrender.com/api/health` returns `{"status":"UP"}`.
4. **Frontend:** add `VITE_API_BASE_URL` and `VITE_BACKEND_ORIGIN` (the Render URL) to the frontend's `.env.pages` and push to `main`. The Pages workflow rebuilds the site against the real API.
5. Log in to the admin panel once, which creates the admin account. Then save an admin email or phone in Settings.

The detailed steps follow.

## Deploy (Render + Neon)

Free hosting: the API runs as a free Docker web service on [Render](https://render.com), the database is a free PostgreSQL project on [Neon](https://neon.tech). Files used: `Dockerfile`, `.dockerignore`, `render.yaml`.

### Environment variables

| Variable | Required | What it is |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | yes | `jdbc:postgresql://<host>/<database>?sslmode=require` (Neon needs SSL) |
| `SPRING_DATASOURCE_USERNAME` | yes | Neon role, e.g. `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | yes | Neon role password. There is no default password (locally too): set it, or `DB_PASSWORD`, in your environment. |
| `JWT_SECRET` | yes | Signs admin login tokens. **At least 32 bytes; there is no default and the app refuses to start without it.** `render.yaml` lets Render generate it. For local development run with `SPRING_PROFILES_ACTIVE=dev` (`application-dev.yml` has a dev-only secret). **Never use `dev` on a server:** the app refuses to start when the `dev` profile is active and `PORT` is set (as on Render); locally use `SERVER_PORT` with `dev`. |
| `ADMIN_USERNAME` | no (default `admin`) | Admin login name created on the first admin login |
| `ADMIN_PASSWORD` | **yes** | Password for that first admin, **at least 12 characters and at most 72 UTF-8 bytes** (BCrypt limit). There is no built-in fallback: while it is missing or shorter, the admin account is not created, admin login is refused and an ERROR is logged. |
| `APP_CORS_ALLOWED_ORIGINS` | yes | Comma-separated frontend origins (scheme + host, no path). `render.yaml`: `https://codelady-ship.github.io,https://perde.az,https://www.perde.az` (https only). |
| `PORT` | set by Render | `server.port` = `${PORT:${SERVER_PORT:8080}}` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `APP_MAIL_FROM` | no | SMTP for admin password-reset codes by email |
| `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID` | no | Meta WhatsApp Cloud API for reset codes by WhatsApp |
| `JAVA_TOOL_OPTIONS` | no | JVM flags; the Dockerfile default is tuned for 512 MB RAM |

### 1. Neon (database)

1. Sign in at https://neon.tech and click **New Project** (any name, e.g. `curtain-shop`; pick the region closest to Render's, e.g. AWS Frankfurt).
2. On the project dashboard click **Connect**. Keep branch `main`, database `neondb`, role `neondb_owner`. Turn **Connection pooling off** (use the direct host, without `-pooler`).
3. From the shown connection string `postgresql://<user>:<password>@<host>/<database>?sslmode=require` write down the four parts. You will enter them in Render as:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<host>/<database>?sslmode=require`
     (example shape: `jdbc:postgresql://ep-xxxx-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require`)
   - `SPRING_DATASOURCE_USERNAME` = `<user>`
   - `SPRING_DATASOURCE_PASSWORD` = `<password>`

   Do not put the user/password inside the JDBC URL and never commit them.

Nothing else is needed in Neon: on the first start Liquibase (`db/changelog/db.changelog-master.yml`) creates every table on the empty database and seeds the default banners (3 main campaigns + 2 general) and 9 partner names. No products are seeded; add them from the admin panel. (`src/main/resources/templates/data.sql` is a local-only sample and is never run automatically.)

### 2. Render (API)

`render.yaml` deploys the `main` branch, so this blueprint must be merged to `main` first.

1. Sign in at https://render.com with GitHub.
2. **New** -> **Blueprint** -> connect/pick the `Curtain-shop-web-site-backend` repository -> branch `main`.
3. Render reads `render.yaml` (service `curtain-shop-api`, free plan, Docker) and asks for the secret values:
   - `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` from step 1.
   - `ADMIN_PASSWORD`: a password of your own, 12 characters up to 72 bytes (keep it in a password manager).
   - `MAIL_*`, `APP_MAIL_FROM`, `WHATSAPP_*`: optional, leave empty if not used.
   `JWT_SECRET` is generated by Render; `APP_CORS_ALLOWED_ORIGINS` is `https://codelady-ship.github.io,https://perde.az,https://www.perde.az` (edit it in the service's **Environment** tab if the frontend moves). Swagger UI / OpenAPI docs are switched off (`SPRINGDOC_*_ENABLED=false`).
4. Click **Apply**. The first Docker build takes several minutes.
5. The service URL is shown on the service page, normally `https://curtain-shop-api.onrender.com` (Render adds a suffix if the name is taken; then use the real URL everywhere below). Check:
   - `https://curtain-shop-api.onrender.com/api/health` -> `{"status":"UP"}`
   - `https://curtain-shop-api.onrender.com/api/banners` -> JSON list
6. Open the frontend admin panel and log in once with `ADMIN_USERNAME` / `ADMIN_PASSWORD`. That first login creates the admin row. **Then open admin Settings and save the admin email and/or phone.** Password reset only works for a contact saved there (see below).

Changing `ADMIN_PASSWORD` later does not change an existing admin; use the admin panel's change-password form instead.

### Which API routes need the admin token

Admin-only (`Authorization: Bearer <token from /api/admin/auth/login>`, otherwise 401):

- everything under `/api/admin/**` except `POST /api/admin/auth/login`, `/forgot-password`, `/reset-password`
- `/api/leads/**` except the public forms `POST /api/leads`, `POST /api/leads/submit`, `POST /api/leads/upload`
- every non-GET request to `/api/products/**`
- `/api/files/**`
- `/api/analytics/dashboard`

Public (the complete allow-list): `GET`/`HEAD` `/api/products/**` (list, filter, detail), `GET /api/banners`, `GET /api/partners`, `GET /api/health`, `POST /api/analytics/hit`, `POST /api/leads`, `POST /api/leads/submit`, `POST /api/leads/upload`, `POST /api/admin/auth/login`, `/forgot-password`, `/reset-password`, and CORS preflight (`OPTIONS`). **Everything else under `/api` needs the admin token**, including any endpoint added later until it is put on the list in `AdminAuthInterceptor.isPublic()`.

Admin login is limited to 5 failed attempts per client IP + username and 20 per username across all IPs in 15 minutes (HTTP 429 after that; in memory, reset on restart). Admin passwords are stored as BCrypt (`spring-security-crypto` 6.4.13); old SHA-256 hashes are replaced on the next successful login. New admin passwords need 12 characters to 72 UTF-8 bytes.

### Admin password reset (forgot password)

- The public forgot-password form never changes the admin profile. The email/phone can only be changed in admin Settings while logged in.
- A code is sent only if the admin already has that email (or phone) saved **and** the form names the same email/phone. It is sent to the saved contact only. The form always shows the same generic message, whether or not a code was sent.
- Codes are 8 random digits (SecureRandom), valid for 15 minutes and single-use. At most one request per 60 seconds. After 5 wrong codes the code is burned and a new one must be requested. The code is never returned by any API (not even the admin profile).
- Delivery needs SMTP (`MAIL_*`) for email or the WhatsApp Cloud API (`WHATSAPP_*`) for phone. Delivery failures are only written to the server log.

### 3. Point the frontend at the API

The frontend's GitHub Pages site is built by its `.github/workflows/pages.yml` on every push to `main`. Add the API URL to the committed `.env.pages` in `Curtain-shop-web-site-frontend`. These URLs are public, not secrets:

```bash
# Curtain-shop-web-site-frontend/.env.pages
VITE_DEMO_FALLBACK=true
VITE_API_BASE_URL=https://curtain-shop-api.onrender.com
VITE_BACKEND_ORIGIN=https://curtain-shop-api.onrender.com
```

Commit and push to `main`. The workflow runs `npm run build:pages` and publishes to `gh-pages`. With the URL set, the site uses the real API. It falls back to the demo products only when the API cannot be reached (for example while the free service is waking up).

For a build on your own domain (`npm run build`), put the two `VITE_*` lines in `.env.production.local` and add that domain to `APP_CORS_ALLOWED_ORIGINS`.

### Existing databases (the current perde.az server): rotate credentials

Earlier versions of this repository contained a default database password in `application.yml` and built-in admin passwords in the Java code. They are still visible in the public git history. On any database that was ever used with those defaults (including the current perde.az server):

1. Change the PostgreSQL user's password, then set the new one only through `SPRING_DATASOURCE_PASSWORD` (or `DB_PASSWORD`).
2. Log in to the admin panel and change the admin password (Settings -> change password; at least 12 characters).
3. Do not reuse either old password anywhere.

### Free-plan limits to know

- **Sleeping:** a free Render service sleeps after ~15 minutes without traffic; the next request wakes it and can take up to about a minute. Neon's free compute also suspends when idle and wakes on the next query (about a second).
- **Uploads are wiped on Render free:** images uploaded through the API are written to `/app/uploads` inside the container. Render's free filesystem is ephemeral, so these files are deleted on every redeploy, restart and spin-down, and their `/uploads/...` links break. Prefer full image URLs (`https://...`) for product/banner images, or move uploads to an external storage service later. The `uploads/` folder is git-ignored and is not copied into the image.
- **Memory:** 512 MB. The image runs with `-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1`, a Hikari pool of 5 and at most 20 Tomcat threads.
- `GET /api/health` does not touch the database, so Render's health checks do not keep Neon awake.

### Run the container locally (optional, needs Docker)

```bash
docker build -t curtain-shop-api .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://<host>/<db>?sslmode=require' \
  -e SPRING_DATASOURCE_USERNAME=... -e SPRING_DATASOURCE_PASSWORD=... \
  -e JWT_SECRET=... -e ADMIN_PASSWORD=... \
  curtain-shop-api
```

## What I learned

- Designing a deny-by-default API: one interceptor with an explicit public allow-list, covered by parameterised tests.
- Making a Spring Boot app 12-factor: every secret comes from env vars, there are no default passwords, and the app refuses to start with an unsafe config.
- Fitting a JVM into a 512 MB free container (heap percentage, Serial GC, small pools) and keeping the health check off the database so a serverless Postgres can sleep.
- Schema changes with Liquibase instead of `ddl-auto`, so a new empty database (Neon) is created and seeded on first start.
