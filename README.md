# Git-To-Go

**A single-click deployment platform that turns any GitHub repository into a running Docker container.**

Git-To-Go is a self-hosted PaaS (Platform as a Service) — think of it as a mini Heroku/Vercel. Users connect their GitHub repos, and the platform automatically detects the language, generates a Dockerfile (if needed), builds a Docker image, and deploys it as a container — all with a single click.

---

## Architecture Overview

```
                         +------------------+
                         |   React Frontend |
                         |   (Vite :5173)   |
                         +--------+---------+
                                  |
                            REST API + WebSocket
                                  |
                         +--------+---------+
                         | Spring Boot API  |
                         |    (Java :8080)  |
                         +----+------+------+
                              |      |
                +-------------+      +-------------+
                |                                   |
        +-------+--------+               +---------+--------+
        |   PostgreSQL   |               |  Docker Engine   |
        |  (Data Store)  |               | (Build & Deploy) |
        +----------------+               +------------------+
```

### How a Deployment Works (Behind the Scenes)

```
User clicks "Deploy"
       |
       v
[1] Controller receives request (returns 202 Accepted immediately)
       |
       v
[2] Deployment entity created (status: QUEUED)
       |
       v
[3] Async build pipeline starts in background thread pool
       |
       v
[4] Git Clone ──> shallow clone (--depth 1) with 5-min timeout
       |
       v
[5] Language Detection ──> checks marker files (package.json, pom.xml, go.mod, etc.)
       |
       v
[6] Dockerfile Generation ──> if no Dockerfile exists, generates optimized multi-stage build
       |
       v
[7] Docker Build ──> builds image with 10-min timeout (git2go/<project>:v<version>)
       |
       v
[8] Stop Old Container ──> gracefully stops previous deployment
       |
       v
[9] Docker Run ──> starts container (512MB RAM, 0.5 CPU limit, dynamic port)
       |
       v
[10] Status Update ──> deployment marked RUNNING, deployed URL saved
       |
       v
[11] Email Notification ──> success/failure email sent to user
```

---

## Tech Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Frontend | React 19, Vite 8, Tailwind CSS 4 | SPA with dark theme UI |
| Backend | Spring Boot 4.0.4, Java 17 | REST API + business logic |
| Database | PostgreSQL | Persistent data store |
| Containerization | Docker (CLI via ProcessBuilder) | Build & run user apps |
| Auth | JWT + OAuth2 (Google, GitHub) | Stateless authentication |
| Real-time | WebSocket (STOMP + SockJS) | Live build log streaming |
| Monitoring | Spring Actuator + Micrometer | Health checks & metrics |
| Email | Spring Mail + Thymeleaf | Notification templates |

---

## Features

- **One-Click Deploy** — Push a GitHub repo, click deploy, get a running URL
- **Auto Language Detection** — Supports Node.js, Java (Maven/Gradle), Python, Go, Static HTML
- **Auto Dockerfile Generation** — No Dockerfile needed for supported languages
- **GitHub OAuth** — Import repos directly from your GitHub account
- **GitHub Webhooks** — Auto-deploy on push to configured branch (HMAC-SHA256 verified)
- **Real-time Logs** — WebSocket-based live build & runtime log streaming
- **Container Monitoring** — CPU, memory, uptime stats with auto-refresh
- **Health Checks** — Automatic health monitoring every 60s (3 failures = alert)
- **Admin Panel** — User management, force-stop deployments, platform stats
- **Audit Logging** — Every sensitive action tracked (who, what, when, from where)
- **Email Notifications** — Verification, deploy success/failure emails
- **Rate Limiting** — Sliding window (20 req/min) on auth endpoints
- **Role-Based Access** — USER and ADMIN roles with granular permissions

---

## Supported Languages

| Language | Detected By | Dockerfile Generated |
|----------|------------|---------------------|
| Node.js | `package.json` | Multi-stage (node:alpine) |
| Java (Maven) | `pom.xml` | Multi-stage (maven + eclipse-temurin) |
| Java (Gradle) | `build.gradle` | Multi-stage (gradle + eclipse-temurin) |
| Python | `requirements.txt` / `Pipfile` / `pyproject.toml` | python:slim |
| Go | `go.mod` | Multi-stage (golang:alpine + scratch) |
| Static HTML | `index.html` | nginx:alpine |
| Custom | User's `Dockerfile` | Uses existing Dockerfile as-is |

> If the repo already contains a `Dockerfile`, the platform uses it directly without generating one.

---

## Project Structure

```
Git-To-Go/
├── backend/                  # Spring Boot API (Java 17, Maven)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/git2go/platform/
│       │   ├── config/       # Security, Async, WebSocket, CORS configs
│       │   ├── controller/   # 8 REST controllers
│       │   ├── entity/       # 7 JPA entities
│       │   ├── enums/        # Role, Status, Language enums
│       │   ├── service/      # 14 services (core business logic)
│       │   ├── repository/   # Spring Data JPA repositories
│       │   ├── security/     # JWT, OAuth2, Rate limiting
│       │   ├── orchestrator/ # Docker container management
│       │   ├── logging/      # File-based log storage
│       │   ├── audit/        # AOP-based audit logging
│       │   ├── dto/          # Request/Response DTOs
│       │   ├── exception/    # Global exception handling
│       │   └── util/         # Encryption, port allocation, signature verification
│       └── resources/
│           └── application.yaml
│
├── frontend/                 # React SPA (Vite + Tailwind)
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/              # Axios HTTP client + API modules
│       ├── components/       # Reusable UI + layout components
│       ├── context/          # Auth context (JWT + OAuth state)
│       ├── pages/            # Route pages (Dashboard, Projects, Admin, etc.)
│       └── main.jsx
│
└── .gitignore
```

---

## Getting Started

### Prerequisites

- **Java 17+**
- **Node.js 18+**
- **Docker** (running on the host machine)
- **PostgreSQL** (local or remote)
- **Maven 3.8+**

### Backend Setup

```bash
cd backend

# Set environment variables (or use defaults from application.yaml)
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=<your-base64-secret>
export GITHUB_CLIENT_ID=<your-github-oauth-client-id>
export GITHUB_CLIENT_SECRET=<your-github-oauth-client-secret>
export GOOGLE_CLIENT_ID=<your-google-oauth-client-id>
export GOOGLE_CLIENT_SECRET=<your-google-oauth-client-secret>

# Run
./mvnw spring-boot:run
```

Backend starts on `http://localhost:8080`

### Frontend Setup

```bash
cd frontend

npm install
npm run dev
```

Frontend starts on `http://localhost:5173`

---

## API Endpoints (Summary)

| Module | Base Path | Key Endpoints |
|--------|----------|---------------|
| Auth | `/api/auth` | signup, login, refresh, logout, verify-email |
| Projects | `/api/projects` | CRUD + deploy trigger |
| Deployments | `/api/deployments` | get, stop, restart |
| Logs | `/api/deployments/{id}/logs` | build logs, runtime logs, stream |
| Webhooks | `/api/webhooks` | GitHub webhook receiver + config |
| Monitoring | `/api/monitoring` | container stats, health checks |
| Admin | `/api/admin` | dashboard, user/project management |
| Audit | `/api/audit` | activity feed, resource history |
| User | `/api/user` | profile, GitHub repos |

---

## Security

| Mechanism | Implementation |
|-----------|---------------|
| Password Hashing | BCrypt with salt |
| Token Auth | JWT (HMAC-SHA256), 1hr access + 7d refresh |
| OAuth2 | Google + GitHub with encrypted token storage |
| Encryption | AES-256-GCM for secrets (GitHub tokens, webhook secrets) |
| Webhook Verification | HMAC-SHA256 with timing-safe comparison |
| Rate Limiting | Sliding window, 20 req/min on auth endpoints |
| RBAC | USER/ADMIN roles via Spring Security `@PreAuthorize` |
| Audit Trail | AOP-based logging of all sensitive operations |
| Email Verification | UUID token with 24-hour expiry (one-time use) |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_USERNAME` | postgres | PostgreSQL username |
| `DB_PASSWORD` | postgres | PostgreSQL password |
| `JWT_SECRET` | (built-in) | Base64-encoded JWT signing key |
| `GITHUB_CLIENT_ID` | - | GitHub OAuth app client ID |
| `GITHUB_CLIENT_SECRET` | - | GitHub OAuth app client secret |
| `GOOGLE_CLIENT_ID` | - | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | - | Google OAuth client secret |
| `SMTP_USERNAME` | - | Email sender address |
| `SMTP_PASSWORD` | - | Email app password |
| `ENCRYPTION_KEY` | (built-in) | AES-256 key (Base64, 32 bytes) |
| `CORS_ORIGINS` | localhost:3000,5173 | Allowed frontend origins |
| `DEPLOYMENT_BASE_URL` | http://localhost | Base URL for deployed apps |
| `BUILDS_DIR` | /tmp/git2go/builds | Temp build directory |
| `LOGS_DIR` | /tmp/git2go/logs | Log storage directory |

---

## License

This project is for educational and portfolio purposes.
