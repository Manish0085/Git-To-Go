# Git-To-Go Frontend

**React 19 SPA** for the Git-To-Go deployment platform. Dark-themed dashboard for managing GitHub repo deployments, real-time log streaming, container monitoring, and admin operations.

---

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| React | 19.2.4 | UI framework |
| Vite | 8.0.1 | Build tool + dev server |
| Tailwind CSS | 4.2.2 | Utility-first styling |
| React Router | 7.13.2 | Client-side routing |
| Axios | 1.14.0 | HTTP client |
| Framer Motion | 12.38.0 | Animations & transitions |
| Lucide React | 1.7.0 | Icon library |
| react-hot-toast | 2.6.0 | Toast notifications |
| @stomp/stompjs | 7.3.0 | WebSocket (STOMP protocol) |
| sockjs-client | 1.6.1 | WebSocket fallback |

---

## Project Structure

```
frontend/
├── index.html                    # HTML entry point
├── package.json                  # Dependencies & scripts
├── vite.config.js                # Vite config (proxy, plugins)
├── eslint.config.js              # ESLint rules
│
└── src/
    ├── main.jsx                  # React root render
    ├── App.jsx                   # Router + route definitions
    ├── index.css                 # Tailwind imports + CSS variables
    │
    ├── api/                      # HTTP client layer
    │   ├── axios.js              # Axios instance + interceptors (auto token refresh)
    │   ├── auth.js               # Auth endpoints (signup, login, logout, verify)
    │   ├── projects.js           # Project CRUD + deploy + webhook APIs
    │   ├── deployments.js        # Deployment management + logs + monitoring APIs
    │   └── admin.js              # Admin dashboard + user management APIs
    │
    ├── context/
    │   └── AuthContext.jsx        # Auth state (user, login, signup, logout, isAdmin)
    │
    ├── components/
    │   ├── layout/
    │   │   ├── AppLayout.jsx     # Protected route wrapper (auth check + sidebar)
    │   │   └── Sidebar.jsx       # Fixed left nav (Dashboard, Projects, Monitoring, Admin)
    │   └── ui/
    │       ├── Button.jsx        # Variants: primary, success, danger, ghost, outline
    │       ├── Input.jsx         # Form input with label, icon, error state
    │       ├── StatsCard.jsx     # Dashboard stat card with icon + animation
    │       └── StatusBadge.jsx   # Status indicator (RUNNING, BUILDING, FAILED, etc.)
    │
    ├── pages/
    │   ├── auth/
    │   │   ├── LoginPage.jsx     # Email/password + OAuth (Google, GitHub)
    │   │   ├── SignupPage.jsx    # Registration form
    │   │   └── OAuthCallback.jsx # OAuth2 redirect handler (stores tokens)
    │   ├── dashboard/
    │   │   ├── DashboardPage.jsx # Project overview + stats cards
    │   │   ├── MonitoringPage.jsx# Real-time container stats (auto-refresh 15s)
    │   │   └── ActivityPage.jsx  # Audit log viewer
    │   ├── project/
    │   │   ├── NewProjectPage.jsx    # GitHub repo picker + create form
    │   │   └── ProjectDetailPage.jsx # Tabbed: Overview, Logs, Deployments, Settings
    │   └── admin/
    │       └── AdminPage.jsx     # User management + platform stats (ADMIN only)
    │
    └── assets/
        ├── hero.png              # Branding image
        ├── react.svg             # React logo
        └── vite.svg              # Vite logo
```

---

## Routing

| Path | Page | Auth | Description |
|------|------|------|-------------|
| `/login` | LoginPage | Public | Email/password + OAuth login |
| `/signup` | SignupPage | Public | User registration |
| `/oauth2/callback` | OAuthCallback | Public | OAuth2 token handler |
| `/dashboard` | DashboardPage | Protected | Project overview + stats |
| `/projects/new` | NewProjectPage | Protected | Create project from GitHub repo |
| `/projects/:id` | ProjectDetailPage | Protected | Project management (4 tabs) |
| `/monitoring` | MonitoringPage | Protected | Real-time container metrics |
| `/activity` | ActivityPage | Protected | Audit log viewer |
| `/admin` | AdminPage | Protected (ADMIN) | User & platform management |
| `/` | — | — | Redirects to `/dashboard` |

---

## Key Pages

### Dashboard
- 4 stats cards: Total Projects, Running, Failed, Monitored
- Responsive project grid (1 col mobile, 3 cols desktop)
- Each project card shows: name, status badge, repo URL, branch, port, deployed URL
- Empty state with "Create your first project" CTA

### New Project
- **Left panel**: GitHub repo list (fetched from user's GitHub account via OAuth token)
  - Search/filter repos
  - Shows: name, description, language, stars, private badge
  - Click to auto-populate form fields
- **Right panel**: Project creation form
  - Project name, repo URL, branch, port
  - Dynamic environment variables (add/remove key-value pairs)

### Project Detail (4 Tabs)
- **Overview**: Project info, container stats (CPU/memory bars), quick actions (deploy, stop, restart, delete), webhook config
- **Build Logs**: Scrollable log viewer with color-coded levels (ERROR=red, WARN=amber, INFO=blue)
- **Deployments**: Version history with status badges, stop/restart buttons
- **Settings**: Edit environment variables (visibility toggle), danger zone (delete project)

### Monitoring
- Auto-refreshes every 15 seconds
- Per-container cards: CPU % bar, memory usage bar, uptime
- Color-coded bars (green < 60%, amber < 80%, red >= 80%)

### Admin
- 5 stats cards: Users, Projects, Deployments, Running, Failed
- User management table: name, email, provider, role toggle, verified status, delete

---

## Auth Flow

```
1. User logs in (email/password OR OAuth2)
       │
2. Backend returns { accessToken, refreshToken, user }
       │
3. Tokens stored in localStorage
       │
4. Axios request interceptor attaches "Authorization: Bearer <token>"
       │
5. On 401 response:
   ├── Attempt token refresh (POST /api/auth/refresh)
   ├── If success → update tokens, retry original request
   └── If fail → redirect to /login
```

---

## API Layer

All API calls go through a configured Axios instance ([axios.js](src/api/axios.js)):

- **Base URL**: `/api` (proxied to `http://localhost:8080` in dev)
- **Request interceptor**: Auto-attaches JWT from localStorage
- **Response interceptor**: Auto-refreshes expired tokens (401 handling)

### API Modules

| Module | File | Endpoints |
|--------|------|-----------|
| Auth | [auth.js](src/api/auth.js) | signup, login, logout, refresh, verify, profile, GitHub repos |
| Projects | [projects.js](src/api/projects.js) | CRUD, deploy, deployments list, webhook config |
| Deployments | [deployments.js](src/api/deployments.js) | get, stop, restart, build/runtime logs, monitoring stats |
| Admin | [admin.js](src/api/admin.js) | dashboard, users, projects, force-stop, audit logs |

---

## Styling

- **Framework**: Tailwind CSS v4 with `@tailwindcss/vite` plugin
- **Theme**: Dark (gray-950 base background)
- **Accent Colors**:
  - Primary: `#6366f1` (Indigo)
  - Success: `#10b981` (Emerald)
  - Danger: `#ef4444` (Red)
  - Warning: `#f59e0b` (Amber)
- **Animations**: Framer Motion (page transitions, card entry, hover/tap effects)
- **Icons**: Lucide React (consistent icon set)
- **Notifications**: react-hot-toast (top-right, dark theme)

---

## Dev Server Configuration

[vite.config.js](vite.config.js) sets up proxies so the frontend can talk to the backend during development:

| Route | Proxy Target | Purpose |
|-------|-------------|---------|
| `/api` | `http://localhost:8080` | REST API |
| `/oauth2/authorization` | `http://localhost:8080` | OAuth2 login redirect |
| `/ws` | `http://localhost:8080` | WebSocket (log streaming) |

---

## Running

```bash
# Install dependencies
npm install

# Start dev server (http://localhost:5173)
npm run dev

# Production build
npm run build

# Preview production build
npm run preview

# Lint
npm run lint
```

> Make sure the backend is running on `http://localhost:8080` before starting the frontend.

---

## Component Library

### Button
Variants: `primary` (indigo), `success` (emerald), `danger` (red), `ghost` (gray), `outline`
- Loading state with spinner
- Framer Motion hover/tap animations

### Input
- Optional left icon
- Label + error message support
- Focus ring (indigo)

### StatsCard
- Icon + large value + optional subtext
- Color variants: indigo, emerald, amber, red, purple
- Framer Motion entry animation

### StatusBadge
- Color-coded per status:
  - RUNNING (emerald, pulsing dot)
  - BUILDING/DEPLOYING/CLONING (amber/purple/cyan, pulsing)
  - FAILED (red)
  - STOPPED/QUEUED (gray)
