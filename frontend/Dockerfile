# =============================================================================
# Git-To-Go Frontend — Multi-stage Docker Build
# Stage 1: Node.js build (Vite + React)
# Stage 2: Nginx static file server
# =============================================================================

# --------------- Stage 1: Build ---------------
FROM node:22-alpine AS build

WORKDIR /app

# Copy package files first for dependency caching
COPY package.json package-lock.json* ./

# Install dependencies
RUN npm ci --ignore-scripts

# Copy source code and build
COPY . .
RUN npm run build

# --------------- Stage 2: Serve ---------------
FROM nginx:1.27-alpine

LABEL maintainer="git2go-team"
LABEL description="Git-To-Go React Frontend"

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Add custom nginx config for SPA routing
COPY --from=build /app/dist /usr/share/nginx/html

# Minimal nginx config to serve the SPA on port 3000
RUN printf 'server {\n\
    listen 3000;\n\
    server_name _;\n\
    root /usr/share/nginx/html;\n\
    index index.html;\n\
\n\
    # SPA fallback — all routes resolve to index.html\n\
    location / {\n\
        try_files $uri $uri/ /index.html;\n\
    }\n\
\n\
    # Cache static assets aggressively\n\
    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {\n\
        expires 1y;\n\
        add_header Cache-Control "public, immutable";\n\
    }\n\
\n\
    # No cache for index.html (ensures fresh deploys)\n\
    location = /index.html {\n\
        expires -1;\n\
        add_header Cache-Control "no-store, no-cache, must-revalidate";\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:3000/ || exit 1
