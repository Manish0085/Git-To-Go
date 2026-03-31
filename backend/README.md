# Git-To-Go Backend

**Spring Boot 4.0.4 REST API** powering the Git-To-Go deployment platform. Handles authentication, project management, Docker build/deploy orchestration, real-time log streaming, container monitoring, and admin operations.

---

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Language |
| Spring Boot | 4.0.4 | Framework |
| Spring Security | - | Auth + RBAC |
| Spring Data JPA | - | ORM + Repositories |
| Spring WebSocket | - | Real-time log streaming |
| Spring WebFlux | - | GitHub API calls (WebClient) |
| Spring Mail + Thymeleaf | - | Email notifications |
| Spring Actuator + Micrometer | - | Monitoring + Prometheus metrics |
| PostgreSQL | - | Database |
| JJWT | 0.12.6 | JWT token management |
| Lombok | - | Boilerplate reduction |
| Docker | - | Container orchestration (via CLI) |

---

## Architecture — How Things Work Behind the Scenes

### Request Flow

```
HTTP Request
     |
     v
[RateLimitFilter] ──> 429 if limit exceeded (auth endpoints only)
     |
     v
[JwtAuthenticationFilter] ──> Extract & validate Bearer token
     |                         Load UserDetails from DB
     |                         Set SecurityContext
     v
[Controller] ──> Input validation (@Valid)
     |
     v
[Service Layer] ──> Business logic + authorization checks
     |
     v
[Repository] ──> JPA/Hibernate ──> PostgreSQL
```

### Authentication Flow

```
                    ┌──────────────────────────────────┐
                    │        AUTH FLOWS                 │
                    └──────────────────────────────────┘

    ┌─────────────────────┐          ┌─────────────────────────┐
    │   Email/Password    │          │   OAuth2 (Google/GitHub) │
    └─────────┬───────────┘          └────────────┬────────────┘
              │                                    │
    POST /api/auth/signup              GET /oauth2/authorization/{provider}
              │                                    │
    ┌─────────v───────────┐          ┌─────────────v──────────────┐
    │ Create User (LOCAL) │          │ Redirect to Provider       │
    │ Send Verification   │          │ (Google/GitHub login page)  │
    │ Email (24hr token)  │          └─────────────┬──────────────┘
    └─────────┬───────────┘                        │
              │                          Provider callback with code
    GET /api/auth/verify-email                     │
              │                          ┌─────────v──────────────┐
    ┌─────────v───────────┐              │ OAuth2SuccessHandler   │
    │ emailVerified=true  │              │ - Create/update user   │
    │ Token nullified     │              │ - Encrypt GitHub token │
    │ (replay prevention) │              │ - Generate JWT         │
    └─────────┬───────────┘              │ - Generate refresh tok │
              │                          └─────────┬──────────────┘
    POST /api/auth/login                           │
              │                          Redirect to frontend with
    ┌─────────v───────────┐              tokens as query params
    │ Verify credentials  │                        │
    │ Check emailVerified │              ┌─────────v──────────────┐
    │ Generate JWT        │              │ Frontend stores tokens │
    │ Generate refresh    │              │ in localStorage        │
    └─────────┬───────────┘              └────────────────────────┘
              │
    ┌─────────v───────────┐
    │ Return AuthResponse │
    │ {accessToken,       │
    │  refreshToken,      │
    │  user profile}      │
    └─────────────────────┘

    Token Refresh:
    POST /api/auth/refresh {refreshToken}
         └──> Verify token exists & not expired
         └──> Delete old, create new refresh token
         └──> Generate new access token
         └──> Return new AuthResponse
```

### Build & Deploy Pipeline (Core Flow)

This is the heart of the platform. When a user clicks "Deploy":

```
POST /api/projects/{id}/deploy
              │
              v
    ┌─────────────────────────────────────────────────┐
    │ [1] TRIGGER (Synchronous — Controller Thread)   │
    │                                                  │
    │  - Validate project ownership                    │
    │  - Validate repo URL (HTTPS only, regex)         │
    │  - Create Deployment entity (status: QUEUED)     │
    │  - Set project status → BUILDING                 │
    │  - Eagerly load env variables into HashMap       │
    │    (avoids LazyInitializationException in async)  │
    │  - Return 202 Accepted + deployment ID           │
    └──────────────────────┬──────────────────────────┘
                           │
              Handoff to @Async("buildExecutor")
              Thread pool: core=5, max=10, queue=25
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [2] GIT CLONE (status: CLONING)                 │
    │                                                  │
    │  - ProcessBuilder: git clone --branch X          │
    │    --depth 1 <url> <target>                      │
    │  - Shallow clone (--depth 1) for speed           │
    │  - Timeout: 5 minutes, then destroyForcibly()    │
    │  - Branch name validated against regex            │
    │  - Target: /tmp/git2go/builds/{projectId}/{ver}  │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [3] LANGUAGE DETECTION                           │
    │                                                  │
    │  Checks for marker files in cloned repo:         │
    │  - package.json     → NODEJS                     │
    │  - pom.xml          → JAVA_MAVEN                 │
    │  - build.gradle     → JAVA_GRADLE                │
    │  - requirements.txt → PYTHON                     │
    │  - Pipfile          → PYTHON                     │
    │  - pyproject.toml   → PYTHON                     │
    │  - go.mod           → GO                         │
    │  - index.html       → STATIC_HTML                │
    │  - None matched     → UNKNOWN                    │
    │                                                  │
    │  If UNKNOWN + no Dockerfile → FAIL               │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [4] DOCKERFILE GENERATION (if not present)       │
    │                                                  │
    │  If repo has Dockerfile → skip, use as-is        │
    │  Otherwise generate optimized multi-stage:       │
    │                                                  │
    │  Node.js:   node:18-alpine (build) → copy dist   │
    │  Maven:     maven:3.9 (build) → temurin:17-jre   │
    │  Gradle:    gradle:8 (build) → temurin:17-jre    │
    │  Python:    python:3.11-slim + pip install        │
    │  Go:        golang:1.21-alpine → scratch binary   │
    │  Static:    nginx:alpine + copy html              │
    │                                                  │
    │  Written to: <projectDir>/Dockerfile             │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [5] DOCKER BUILD (status: BUILDING)              │
    │                                                  │
    │  - ProcessBuilder: docker build -t <image> .     │
    │  - Image tag: git2go/<sanitized-name>:v<version> │
    │  - Timeout: 10 minutes                           │
    │  - Error stream redirected for capture            │
    │  - Build output logged to file                   │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [6] STOP OLD CONTAINER                           │
    │                                                  │
    │  - Find latest RUNNING deployment for project    │
    │  - docker stop <containerId>                     │
    │  - docker rm <containerId>                       │
    │  - Mark old deployment → STOPPED                 │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [7] DOCKER RUN (status: DEPLOYING)               │
    │                                                  │
    │  docker run -d                                   │
    │    --name git2go-<name>-v<ver>                   │
    │    -p <hostPort>:<containerPort>                  │
    │    --memory 512m                                  │
    │    --cpus 0.5                                     │
    │    -e KEY1=VAL1 -e KEY2=VAL2 ...                 │
    │    <imageName>                                    │
    │                                                  │
    │  - Port allocated by PortAllocator               │
    │  - Resource limits enforced per container         │
    │  - Env variables injected from project config    │
    └──────────────────────┬──────────────────────────┘
                           │
    ┌──────────────────────v──────────────────────────┐
    │ [8] FINALIZE (status: RUNNING)                   │
    │                                                  │
    │  - Save containerId, containerName, hostPort     │
    │  - Set deployedUrl = baseUrl:hostPort            │
    │  - Update project status → RUNNING               │
    │  - Send success email to user                    │
    │  - Cleanup temp build directory (rm -rf)         │
    └─────────────────────────────────────────────────┘

    On ANY failure:
    - deployment.status → FAILED
    - project.status → FAILED
    - failureReason saved
    - Failure email sent
    - Build directory cleaned up
```

### Webhook Auto-Deploy Flow

```
GitHub Push Event
       │
       v
POST /api/webhooks/github/{projectId}  (public endpoint, no JWT)
       │
       v
┌──────────────────────────────────────┐
│ [1] Signature Verification           │
│     - Extract X-Hub-Signature-256    │
│     - HMAC-SHA256(body, secret)      │
│     - Timing-safe comparison         │
│     (MessageDigest.isEqual)          │
└──────────────────┬───────────────────┘
                   │
┌──────────────────v───────────────────┐
│ [2] Validation                       │
│     - Check autoDeployEnabled        │
│     - Check not already BUILDING     │
│     - Parse JSON payload             │
│     - Extract branch from refs/heads │
│     - Match against project branch   │
└──────────────────┬───────────────────┘
                   │
                   v
         Trigger Build Pipeline
         (same async flow as above)
```

### Health Check System

```
@Scheduled(fixedRate = 60000)  ──> Every 60 seconds
       │
       v
Find all deployments with status = RUNNING
       │
       v
For each deployment:
  ├── docker inspect <containerId> → check container status
  ├── HTTP GET http://localhost:<port>/ → 5-sec timeout
  │
  ├── If healthy → reset failure counter
  └── If unhealthy → increment failure counter
       │
       └── 3 consecutive failures → status = FAILED
                                    project status = FAILED
```

### WebSocket Log Streaming

```
Client                          Server
  │                                │
  ├── CONNECT /ws (SockJS) ──────>│
  │                                │
  ├── SUBSCRIBE /topic/logs/{id} ─>│
  │                                │
  ├── POST /stream/start ────────>│──> docker logs -f <containerId>
  │                                │    @Async with 2-hour timeout
  │<── MESSAGE {log line} ────────│    ConcurrentHashMap dedup
  │<── MESSAGE {log line} ────────│
  │<── MESSAGE {log line} ────────│
  │                                │
  ├── POST /stream/stop ─────────>│──> Process.destroy()
  │                                │
  └── DISCONNECT ─────────────────>│
```

### Audit Logging (AOP)

```
@Auditable(action = "DEPLOY_PROJECT")
public DeploymentResponse triggerDeployment(...) { ... }
       │
       v
AuditAspect (@Around advice)
       │
       ├── Before: capture user email, IP address
       ├── Execute method
       ├── After success: save AuditLog(action, SUCCESS, ...)
       └── After failure: save AuditLog(action, FAILURE, error message)
```

---

## Package Structure

```
com.git2go.platform/
│
├── SingleClickDeploymentSystemApplication.java    # Main entry point
│
├── config/
│   ├── SecurityConfig.java          # Spring Security filter chain, CORS, public routes
│   ├── AsyncConfig.java             # Thread pool (core=5, max=10, queue=25)
│   ├── WebSocketConfig.java         # STOMP broker (/ws, /topic)
│   └── JacksonConfig.java           # JSON serialization config
│
├── controller/
│   ├── AuthController.java          # /api/auth — signup, login, refresh, logout, verify
│   ├── ProjectController.java       # /api/projects — CRUD operations
│   ├── DeploymentController.java    # /api/deployments — deploy, stop, restart
│   ├── LogController.java           # /api/deployments/{id}/logs — build & runtime logs
│   ├── WebhookController.java       # /api/webhooks — GitHub webhooks + config
│   ├── UserController.java          # /api/user — profile, GitHub repos
│   ├── MonitoringController.java    # /api/monitoring — container stats, health
│   ├── AdminController.java         # /api/admin — user/project management (ADMIN only)
│   └── AuditLogController.java      # /api/audit — activity feed
│
├── entity/
│   ├── User.java                    # id, name, email, password, role, authProvider, maxProjects
│   ├── Project.java                 # id, name, repoUrl, branch, port, status, webhookSecret
│   ├── Deployment.java              # id, version, imageId, containerId, status, deployedUrl
│   ├── BuildLog.java                # id, message, level, timestamp
│   ├── EnvVariable.java             # id, key, value → ManyToOne Project
│   ├── RefreshToken.java            # id, token, expiryDate → ManyToOne User
│   └── AuditLog.java               # id, action, userEmail, result, resourceInfo, ipAddress
│
├── enums/
│   ├── Role.java                    # USER, ADMIN
│   ├── AuthProvider.java            # LOCAL, GOOGLE, GITHUB
│   ├── ProjectStatus.java           # CREATED, BUILDING, DEPLOYING, RUNNING, STOPPED, FAILED
│   ├── DeploymentStatus.java        # QUEUED, CLONING, BUILDING, DEPLOYING, RUNNING, FAILED, STOPPED
│   └── ProjectLanguage.java         # NODEJS, JAVA_MAVEN, JAVA_GRADLE, PYTHON, GO, STATIC_HTML, UNKNOWN
│
├── service/
│   ├── AuthService.java             # Signup, login, token refresh, logout
│   ├── ProjectService.java          # CRUD + 5-project limit enforcement
│   ├── BuildService.java            # Core async build pipeline (the heart of the app)
│   ├── WebhookService.java          # Auto-deploy, HMAC verification, secret management
│   ├── GitHubService.java           # Fetch user repos via GitHub API (WebClient)
│   ├── LogService.java              # Build/runtime logs + WebSocket streaming
│   ├── EmailService.java            # Verification, welcome, deploy success/failure emails
│   ├── AdminService.java            # Dashboard stats, user/project management
│   ├── AuditLogService.java         # Activity feed queries
│   ├── RefreshTokenService.java     # Token CRUD + expiry verification
│   ├── EmailVerificationService.java# Token generation, verification, resend
│   ├── LanguageDetectorService.java # Marker file detection (package.json, pom.xml, etc.)
│   ├── DockerfileGeneratorService.java # Multi-stage Dockerfile templates
│   ├── ContainerStatsService.java   # Real-time CPU/memory stats from docker stats
│   ├── HealthCheckService.java      # Scheduled health checks (60s interval)
│   └── LogCleanupService.java       # Nightly cleanup of logs older than 7 days
│
├── repository/
│   ├── UserRepository.java          # findByEmail, existsByEmail, findByAuthProviderAndProviderId
│   ├── ProjectRepository.java       # findByUserId (paginated), existsByNameAndUserId
│   ├── DeploymentRepository.java    # findByProjectId, findByStatus, countByProjectId
│   ├── RefreshTokenRepository.java  # findByToken, deleteByUserId
│   ├── EnvVariableRepository.java   # Basic JpaRepository
│   ├── AuditLogRepository.java      # findByUserEmail, findByResourceTypeAndResourceId
│   └── BuildLogRepository.java      # Basic JpaRepository
│
├── security/
│   ├── JwtTokenProvider.java        # Generate, validate JWT (HMAC-SHA256)
│   ├── JwtAuthenticationFilter.java # Extract Bearer token, set SecurityContext
│   ├── CustomUserDetailsService.java# Load user from DB, map roles
│   ├── OAuth2AuthenticationSuccessHandler.java  # Handle Google/GitHub callback
│   └── RateLimitFilter.java         # Sliding window, 20 req/min on /api/auth/**
│
├── orchestrator/
│   ├── ContainerOrchestrator.java   # Interface (strategy pattern)
│   ├── DockerOrchestrator.java      # Docker CLI implementation (ProcessBuilder)
│   ├── BuildRequest.java            # Build parameters DTO
│   ├── RunContainerRequest.java     # Run parameters DTO
│   ├── ContainerStatus.java         # RUNNING, EXITED, STOPPED, NOT_FOUND
│   └── ContainerStats.java          # CPU%, memory, network stats
│
├── logging/
│   ├── LogStorageService.java       # Interface for log persistence
│   ├── FileLogStorageService.java   # File-based: /tmp/git2go/logs/{deploymentId}.log
│   └── LogEntry.java               # message, level, timestamp
│
├── audit/
│   ├── Auditable.java              # Custom annotation @Auditable(action = "...")
│   └── AuditAspect.java            # AOP @Around — captures user, IP, result
│
├── dto/
│   ├── request/
│   │   ├── SignupRequest.java       # name, email, password (validated)
│   │   ├── LoginRequest.java        # email, password
│   │   ├── RefreshTokenRequest.java # refreshToken
│   │   ├── CreateProjectRequest.java# name, repoUrl, branch, port, envVariables
│   │   └── UpdateProjectRequest.java# name, branch, port, autoDeployEnabled, envVariables
│   └── response/
│       ├── ApiResponse.java         # Generic wrapper: success, message, data, timestamp
│       ├── AuthResponse.java        # accessToken, refreshToken, tokenType, user
│       ├── UserProfileResponse.java # id, name, email, avatarUrl, role, authProvider
│       ├── ProjectResponse.java     # Full project details + env vars
│       ├── DeploymentResponse.java  # id, version, status, deployedUrl, failureReason
│       ├── BuildLogResponse.java    # message, level, timestamp
│       ├── ContainerStatsResponse.java # CPU, memory, network, uptime
│       ├── HealthCheckResponse.java # containerStatus, healthy, httpStatusCode, responseTimeMs
│       ├── WebhookConfigResponse.java  # autoDeployEnabled, webhookUrl, webhookSecret
│       ├── AdminDashboardResponse.java # totalUsers, totalProjects, running, failed counts
│       ├── AdminUserResponse.java   # User details + projectCount
│       ├── AuditLogResponse.java    # action, result, resource details, IP, timestamp
│       ├── GitHubRepoResponse.java  # name, description, cloneUrl, language, stars, forks
│       └── PagedResponse.java       # Generic paginated response wrapper
│
├── exception/
│   ├── ApiException.java            # Custom exception with HttpStatus
│   └── GlobalExceptionHandler.java  # @ControllerAdvice — maps exceptions to ApiResponse
│
└── util/
    ├── EncryptionUtil.java          # AES-256-GCM encrypt/decrypt (random IV per operation)
    ├── PortAllocator.java           # Unique port allocation for containers
    ├── GitHubSignatureVerifier.java # HMAC-SHA256 webhook verification (timing-safe)
    └── WebhookSecretGenerator.java  # SecureRandom 32-byte secret generation
```

---

## API Reference

### Auth (`/api/auth`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/signup` | Register with email/password | Public |
| POST | `/login` | Login, returns JWT + refresh token | Public |
| POST | `/refresh` | Refresh access token | Public |
| POST | `/logout` | Invalidate all refresh tokens | Public |
| GET | `/verify-email?token=` | Verify email address | Public |
| POST | `/resend-verification?email=` | Resend verification email | Public |

### Projects (`/api/projects`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/` | Create project | User |
| GET | `/?page=0&size=10` | List user's projects (paginated) | User |
| GET | `/{id}` | Get project details | Owner |
| PUT | `/{id}` | Update project | Owner |
| DELETE | `/{id}` | Delete project + all deployments | Owner |

### Deployments

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/projects/{projectId}/deploy` | Trigger deployment (202) | Owner |
| GET | `/api/projects/{projectId}/deployments` | List project deployments | Owner |
| GET | `/api/deployments/{id}` | Get deployment details | Owner |
| POST | `/api/deployments/{id}/stop` | Stop container | Owner |
| POST | `/api/deployments/{id}/restart` | Restart container | Owner |

### Logs (`/api/deployments/{id}/logs`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/build` | Full build logs | Owner |
| GET | `/build/tail?lines=50` | Last N build logs | Owner |
| GET | `/runtime?lines=100` | Runtime logs (docker logs) | Owner |
| POST | `/stream/start` | Start WebSocket log stream | Owner |
| POST | `/stream/stop` | Stop log stream | Owner |

### Webhooks

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/webhooks/github/{projectId}` | GitHub webhook receiver | Public (HMAC) |
| POST | `/api/projects/{projectId}/webhook/enable` | Enable auto-deploy | Owner |
| POST | `/api/projects/{projectId}/webhook/disable` | Disable auto-deploy | Owner |
| GET | `/api/projects/{projectId}/webhook` | Get webhook config | Owner |
| POST | `/api/projects/{projectId}/webhook/regenerate` | Regenerate secret | Owner |

### Monitoring (`/api/monitoring`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/stats` | All running containers stats | User |
| GET | `/deployments/{id}/stats` | Single deployment stats | Owner |
| GET | `/deployments/{id}/health` | Manual health check | Owner |

### Admin (`/api/admin`) — ADMIN role required

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/dashboard` | Platform overview stats |
| GET | `/users?page=0&size=20` | List all users |
| PUT | `/users/{userId}/role` | Change user role |
| DELETE | `/users/{userId}` | Delete user + all resources |
| GET | `/projects?page=0&size=20` | List all projects |
| POST | `/deployments/{id}/stop` | Force stop any deployment |

### Audit (`/api/audit`)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/activity?page=0&size=20` | User's activity feed | User |
| GET | `/resource/{type}/{id}` | Resource-specific history | User |

---

## Database Schema (Entity Relationships)

```
User (1) ──────< (N) Project (1) ──────< (N) Deployment
  │                     │                        │
  │                     │                        └──< (N) BuildLog
  │                     │
  │                     └──< (N) EnvVariable
  │
  └──< (N) RefreshToken

AuditLog (standalone — linked by userEmail, not FK)
```

---

## Running

```bash
# Prerequisites: Java 17+, Maven 3.8+, PostgreSQL, Docker

# With environment variables
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
./mvnw spring-boot:run

# Or with defaults (connects to 65.1.135.221:5432)
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`

### Actuator Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health status |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus-format metrics |
| `/actuator/info` | Application info |

---

## Key Design Decisions

| Decision | Why |
|----------|-----|
| **@Async build pipeline** | Controller returns 202 immediately; client polls for status. Non-blocking UX. |
| **Eager loading before @Async** | Hibernate session closes after `@Transactional`. Lazy loading in async thread throws `LazyInitializationException`. All data passed as primitives/maps. |
| **File-based logs (not DB)** | Build logs can be massive. File I/O is cheaper than DB writes during active builds. 7-day auto-cleanup. |
| **Docker CLI via ProcessBuilder** | No Docker SDK dependency. Direct CLI commands are simpler, more debuggable, and version-agnostic. |
| **AES-256-GCM encryption** | GitHub tokens and webhook secrets are sensitive. GCM provides authenticated encryption (integrity + confidentiality). Random IV per operation prevents pattern analysis. |
| **Timing-safe HMAC comparison** | `MessageDigest.isEqual()` prevents timing attacks on webhook signature verification. |
| **Sliding window rate limiting** | ConcurrentLinkedQueue tracks request timestamps. Old entries pruned on each check. Thread-safe without locks. |
| **Separate refresh tokens** | Access tokens are short-lived (1hr) and stateless. Refresh tokens are long-lived (7d), stored in DB, and revocable on logout. |
