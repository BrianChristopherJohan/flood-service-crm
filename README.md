# flood-service-crm

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=java&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Upstash-DC382D?logo=redis&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-black?logo=jsonwebtokens)
![License](https://img.shields.io/badge/license-MIT-blue)

> **REST API backend for the FloodWatch CRM dashboard.** Serves sensor data, analytics, community moderation, user management, and admin operations for operations managers and administrators.

---

## Overview

`flood-service-crm` is a Spring Boot 3 microservice powering the **flood-website-crm** dashboard. It provides secure REST endpoints for:
- Real-time sensor node ingestion and retrieval
- Analytics aggregation (daily/weekly/monthly flood event data)
- Admin user management with role-based access control
- Community content moderation (posts, groups, blog)
- Push notification broadcasting
- Incident reports and flood zone management

---

## Features

- **JWT Authentication** — Access tokens (15 min) + Refresh tokens (7 days), ROLE_ADMIN / ROLE_OPERATIONS_MANAGER
- **Sensor Node Management** — Ingest water level readings, query node status, history
- **Analytics Engine** — Pre-aggregated chart data for dashboard consumption
- **Community Moderation** — Approve, delete, or manage posts, groups, and blogs
- **User Management** — CRUD for admin accounts with configurable roles
- **Favourites** — Per-user favourite node persistence
- **Settings** — Persistent user preferences (refresh intervals, UI config)
- **Broadcasts** — Push notification campaigns to mobile users via Expo
- **CORS** — Configurable allowed origins for CRM frontend

---

## Tech Stack

| Technology        | Version | Purpose                            |
|-------------------|---------|------------------------------------|
| Spring Boot       | 3.2     | Application framework              |
| Java              | 17      | Runtime                            |
| Spring Security   | 6.x     | JWT authentication & authorization |
| PostgreSQL (Neon) | 15      | Primary relational database        |
| Redis (Upstash)   | 7.x     | Token blacklist, caching           |
| Maven             | 3.9     | Build tool                         |
| JUnit 5           | 5.x     | Unit & integration testing         |
| Lombok            | 1.18    | Boilerplate reduction              |

---

## Architecture

```
flood-website-crm (Next.js :3000)
         │
         ▼  REST / JWT
flood-service-crm (Spring Boot :4002)
         │
         ├── PostgreSQL (Neon) — persistent data
         └── Redis (Upstash)   — token store, cache
```

---

## Prerequisites

- Java 17+
- Maven 3.9+
- PostgreSQL database (Neon cloud or local)
- Redis instance (Upstash cloud or local)

---

## Getting Started

### 1. Clone and configure

```bash
# From the monorepo root
cd flood-service-crm
```

The committed [`src/main/resources/application.yml`](src/main/resources/application.yml) already uses `${ENV_VAR:default}` placeholders for everything sensitive — no copy step needed. Just provide the env vars in step 2.

### 2. Set environment variables

Create a `.env` file or set these as system environment variables:

```bash
DATABASE_URL=jdbc:postgresql://<host>/<db>?sslmode=require
DATABASE_USERNAME=your_db_user
DATABASE_PASSWORD=your_db_password
JWT_SECRET=your-256-bit-jwt-secret-minimum-32-chars
JWT_REFRESH_SECRET=your-256-bit-refresh-secret
REDIS_URL=redis://default:<token>@<host>:6379
PORT=4002
```

### 3. Run the application

On Windows PowerShell, use the local launcher:

```powershell
.\run.bat
```

Or run Maven directly:

```powershell
.\mvnw.cmd spring-boot:run "-Dmaven.test.skip=true"
```

PowerShell does not run commands from the current directory unless you prefix
them with `.\`. Quoting the `-D...` flag also prevents Maven from receiving a
broken `.test.skip=true` argument.

On macOS / Linux / Git Bash:

```bash
./mvnw spring-boot:run
```

Or skip tests for faster startup:

```bash
./mvnw spring-boot:run -Dmaven.test.skip=true
```

The API will be available at `http://localhost:4002`.

### 4. Run tests

```bash
./mvnw test
```

---

## Environment Variables

| Variable             | Description                          | Example                              |
|----------------------|--------------------------------------|--------------------------------------|
| `DATABASE_URL`       | JDBC URL for PostgreSQL              | `jdbc:postgresql://ep-xxx.neon.tech/floodcrm` |
| `DATABASE_USERNAME`  | Database username                    | `neondb_owner`                       |
| `DATABASE_PASSWORD`  | Database password                    | `your-password`                      |
| `JWT_SECRET`         | Secret for signing access tokens     | 32+ character random string          |
| `JWT_REFRESH_SECRET` | Secret for signing refresh tokens    | 32+ character random string          |
| `REDIS_URL`          | Redis connection string              | `redis://default:token@host:6379`    |
| `PORT`               | Server port                          | `4002`                               |

---

## API Endpoints

### Authentication

| Method | Path                  | Description                    | Auth Required |
|--------|-----------------------|--------------------------------|---------------|
| POST   | `/auth/login`         | Admin login (returns JWT)      | No            |
| POST   | `/auth/refresh`       | Refresh access token           | No            |
| POST   | `/auth/logout`        | Invalidate refresh token       | Yes           |

### Sensor Nodes

| Method | Path                     | Description                    | Auth Required |
|--------|--------------------------|--------------------------------|---------------|
| GET    | `/sensors`               | List all sensor nodes          | Yes           |
| POST   | `/sensors/ingest`        | Ingest a new sensor reading    | Yes (ADMIN)   |
| GET    | `/sensors/{id}`          | Get node by ID                 | Yes           |
| DELETE | `/sensors/{id}`          | Delete a sensor node           | Yes (ADMIN)   |

### Analytics

| Method | Path                     | Description                    | Auth Required |
|--------|--------------------------|--------------------------------|---------------|
| GET    | `/dashboard/analytics`   | Aggregated analytics for CRM   | Yes           |

### Admin Users

| Method | Path                     | Description                    | Auth Required |
|--------|--------------------------|--------------------------------|---------------|
| GET    | `/admin/users`           | List all admin users           | Yes (ADMIN)   |
| POST   | `/admin/users`           | Create admin user              | Yes (ADMIN)   |
| PUT    | `/admin/users/{id}`      | Update admin user              | Yes (ADMIN)   |
| DELETE | `/admin/users/{id}`      | Delete admin user              | Yes (ADMIN)   |

### Community (Moderation)

| Method | Path                     | Description                    | Auth Required |
|--------|--------------------------|--------------------------------|---------------|
| GET    | `/feed`                  | Get community posts feed       | Yes           |
| DELETE | `/admin/posts/{id}`      | Delete a community post        | Yes (ADMIN)   |
| GET    | `/admin/groups`          | List all groups                | Yes           |
| DELETE | `/admin/groups/{id}`     | Delete a group                 | Yes (ADMIN)   |

### Broadcasts

| Method | Path                     | Description                    | Auth Required |
|--------|--------------------------|--------------------------------|---------------|
| GET    | `/broadcasts`            | List broadcast notifications   | Yes           |
| POST   | `/broadcasts`            | Send a broadcast notification  | Yes (ADMIN)   |

---

## Project Structure

```
flood-service-crm/
├── src/
│   ├── main/
│   │   ├── java/com/fyp/floodmonitoring/
│   │   │   ├── config/          # Security, CORS, Redis config
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Request/response DTOs
│   │   │   ├── entity/          # JPA entities
│   │   │   ├── exception/       # Global exception handler
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── security/        # JWT filter, auth provider
│   │   │   └── service/         # Business logic layer
│   │   └── resources/
│   │       └── application.yml  # App configuration
│   └── test/                    # Unit and integration tests
├── Dockerfile
└── pom.xml
```

---

## Docker

```bash
# Build and run with Docker Compose
docker-compose up --build
```

Or build the image directly:

```bash
docker build -t flood-service-crm .
docker run -p 4002:4002 \
  -e DATABASE_URL=... \
  -e JWT_SECRET=... \
  flood-service-crm
```

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'feat: add your feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

---

## License

MIT — see [LICENSE](LICENSE) for details.
