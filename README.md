# Curtain-shop-web-site-backend
### **Overview**

This project is the backend for the leads management system, built using **Spring Boot** and **PostgreSQL**. It handles the core functionality of processing lead data, including adding new leads, updating statuses, managing promo codes, and storing relevant data.

### **Technologies**

* **Java 11** or higher
* **Spring Boot**
* **PostgreSQL**
* **JPA (Java Persistence API)**
* **MapStruct** for DTO mapping
* **CORS** for cross-origin requests
* **Spring Security** (if applicable for your project)
* **Lombok** for reducing boilerplate code

### **Setup**

1. **Clone the Repository**

   ```bash
   git clone https://github.com/username/repository.git
   ```

2. **Install Dependencies**
   In the backend directory, run the following command to install dependencies:

   ```bash
   ./gradle clean install
   ```

3. **Database Configuration**
   Make sure to update the `application.properties` or `application.yml` file with your database credentials:

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/your_db_name
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

4. **Run the Application**
   You can run the backend using Maven:

   ```bash
   ./gradle spring-boot:run
   ```

   Alternatively, you can build the JAR file and run it:

   ```bash
   ./gradle clean package
   java -jar target/your-application.jar
   ```

   The backend will be available at `http://localhost:8080`.

### **Endpoints**

* **GET** `/api/leads` - Fetch leads with filtering options.
* **POST** `/api/leads/upload` - Upload visualization image for a lead.
* **POST** `/api/leads` - Create new leads.
* **GET** `/api/leads/{id}` - Get a single lead by ID.
* **PUT** `/api/leads/{id}/status` - Update lead status.
* **PUT** `/api/leads/{id}/contacted` - Update contacted status for a lead.
* **PUT** `/api/leads/{id}/promo` - Update promo code for a lead.

## Deploy (Render + Neon)

Free hosting: the API runs as a free Docker web service on [Render](https://render.com), the database is a free PostgreSQL project on [Neon](https://neon.tech). Files used: `Dockerfile`, `.dockerignore`, `render.yaml`.

### Environment variables

| Variable | Required | What it is |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | yes | `jdbc:postgresql://<host>/<database>?sslmode=require` (Neon needs SSL) |
| `SPRING_DATASOURCE_USERNAME` | yes | Neon role, e.g. `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | yes | Neon role password |
| `JWT_SECRET` | yes | Signs admin login tokens. `render.yaml` lets Render generate it. |
| `ADMIN_USERNAME` | no (default `admin`) | Admin login name created on the first admin login |
| `ADMIN_PASSWORD` | **yes on any public deploy** | Password for that first admin. If empty, the built-in default from the source code is used, and this repo is public. |
| `APP_CORS_ALLOWED_ORIGINS` | yes | Comma-separated frontend origins (scheme + host, no path), e.g. `https://codelady-ship.github.io,https://perde.az` |
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
   - `ADMIN_PASSWORD`: a long password of your own (keep it in a password manager).
   - `MAIL_*`, `APP_MAIL_FROM`, `WHATSAPP_*`: optional, leave empty if not used.
   `JWT_SECRET` is generated by Render; `APP_CORS_ALLOWED_ORIGINS` already includes `https://codelady-ship.github.io` and `https://perde.az` (edit it in the service's **Environment** tab if the frontend moves).
4. Click **Apply**. The first Docker build takes several minutes.
5. The service URL is shown on the service page, normally `https://curtain-shop-api.onrender.com` (Render adds a suffix if the name is taken; then use the real URL everywhere below). Check:
   - `https://curtain-shop-api.onrender.com/api/health` -> `{"status":"UP"}`
   - `https://curtain-shop-api.onrender.com/api/banners` -> JSON list
6. Open the frontend admin panel and log in once with `ADMIN_USERNAME` / `ADMIN_PASSWORD`. That first login creates the admin row. **Right after that, open admin Settings and save the admin email and phone**: while the admin has no email/phone saved, the public "forgot password" form accepts whatever email/phone is typed in.

Changing `ADMIN_PASSWORD` later does not change an existing admin; use the admin panel's change-password form instead.

### 3. Point the frontend at the API

In `Curtain-shop-web-site-frontend` (see its README / `.env.example`):

```bash
# GitHub Pages build (vite --mode pages reads .env.pages.local)
echo VITE_API_BASE_URL=https://curtain-shop-api.onrender.com  >  .env.pages.local
echo VITE_BACKEND_ORIGIN=https://curtain-shop-api.onrender.com >> .env.pages.local
npm ci
npm run build:pages
npx gh-pages -d dist      # publishes dist/ to the gh-pages branch
```

For a normal production build (`npm run build`, e.g. for perde.az) put the same two lines in `.env.production.local` instead.

### Free-plan limits to know

- **Sleeping:** a free Render service sleeps after ~15 minutes without traffic; the next request wakes it and can take up to about a minute. Neon's free compute also suspends when idle and wakes on the next query (about a second).
- **Uploads are not permanent:** images uploaded through the API are written to `/app/uploads` inside the container. Render's free filesystem is ephemeral, so these files are deleted on every redeploy, restart and spin-down, and their `/uploads/...` links break. Prefer full image URLs (`https://...`) for product/banner images, or move uploads to an external storage service later. The `uploads/` folder in this repo is not copied into the image.
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
