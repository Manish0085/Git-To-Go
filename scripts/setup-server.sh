#!/bin/bash
# =============================================================================
# Git-To-Go — Complete Server Setup Script
# Run on fresh Ubuntu 22.04/24.04 EC2 instance
#
# Usage:
#   chmod +x setup-server.sh
#   sudo ./setup-server.sh
# =============================================================================

set -e

# ==================== COLORS ====================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# ==================== HELPER FUNCTIONS ====================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo ""
    echo -e "${CYAN}=============================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}=============================================${NC}"
}

check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "Please run as root: sudo ./setup-server.sh"
        exit 1
    fi
}

get_server_ip() {
    # EC2 metadata se public IP nikalo
    curl -s --connect-timeout 3 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || \
    curl -s --connect-timeout 3 http://checkip.amazonaws.com 2>/dev/null || \
    echo "YOUR_SERVER_IP"
}

# ==================== SETUP FUNCTIONS ====================

update_system() {
    log_step "[1/7] Updating System Packages"

    apt update && apt upgrade -y
    apt install -y \
        curl \
        wget \
        git \
        unzip \
        apt-transport-https \
        ca-certificates \
        software-properties-common \
        gnupg \
        lsb-release \
        fontconfig

    log_info "System packages updated successfully"
}

install_docker() {
    log_step "[2/7] Installing Docker + Docker Compose"

    # Skip if already installed
    if command -v docker &> /dev/null; then
        log_warn "Docker already installed: $(docker --version)"
        return
    fi

    # Add Docker GPG key
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    # Add Docker repository
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt update
    apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Add ubuntu user to docker group
    usermod -aG docker ubuntu

    # Start and enable Docker
    systemctl start docker
    systemctl enable docker

    log_info "Docker installed: $(docker --version)"
    log_info "Docker Compose installed: $(docker compose version)"
}

install_java() {
    log_step "[3/7] Installing Java 17 (Jenkins Dependency)"

    # Skip if already installed
    if java -version 2>&1 | grep -q "17"; then
        log_warn "Java 17 already installed"
        return
    fi

    apt install -y openjdk-17-jre

    log_info "Java installed: $(java -version 2>&1 | head -1)"
}

install_jenkins() {
    log_step "[4/7] Installing Jenkins"

    # Skip if already installed
    if systemctl is-active --quiet jenkins 2>/dev/null; then
        log_warn "Jenkins already running"
        return
    fi

    # Download Jenkins WAR directly (avoids GPG key issues)
    log_info "Downloading Jenkins WAR..."
    mkdir -p /opt/jenkins
    wget -q -O /opt/jenkins/jenkins.war https://get.jenkins.io/war-stable/latest/jenkins.war

    # Create Jenkins home directory
    mkdir -p /var/lib/jenkins
    chown ubuntu:ubuntu /var/lib/jenkins

    # Create systemd service
    cat > /etc/systemd/system/jenkins.service << 'EOF'
[Unit]
Description=Jenkins CI/CD Server
After=network.target

[Service]
Type=simple
User=ubuntu
Environment="JENKINS_HOME=/var/lib/jenkins"
ExecStart=/usr/bin/java -Xmx512m -jar /opt/jenkins/jenkins.war --httpPort=8082
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

    # Start Jenkins
    systemctl daemon-reload
    systemctl start jenkins
    systemctl enable jenkins

    # Wait for Jenkins to initialize
    log_info "Waiting for Jenkins to start..."
    sleep 20

    log_info "Jenkins installed on port 8082"
}

install_psql_client() {
    log_step "[5/7] Installing PostgreSQL Client"

    # Skip if already installed
    if command -v psql &> /dev/null; then
        log_warn "psql already installed"
        return
    fi

    apt install -y postgresql-client

    log_info "PostgreSQL client installed: $(psql --version)"
}

configure_firewall() {
    log_step "[6/7] Configuring Firewall"

    ufw allow 22/tcp    comment 'SSH'
    ufw allow 80/tcp    comment 'HTTP'
    ufw allow 443/tcp   comment 'HTTPS'
    ufw allow 8082/tcp  comment 'Jenkins'
    ufw allow 3001/tcp  comment 'Grafana'
    ufw allow 9090/tcp  comment 'Prometheus'
    ufw --force enable

    log_info "Firewall rules:"
    ufw status numbered
}

setup_project() {
    log_step "[7/7] Setting Up Project"

    local PROJECT_DIR="/home/ubuntu/Git-To-Go"
    local SERVER_IP
    SERVER_IP=$(get_server_ip)

    # Clone or pull project
    clone_project "$PROJECT_DIR"

    # Create .env file
    create_env_file "$PROJECT_DIR" "$SERVER_IP"

    # Set ownership
    chown -R ubuntu:ubuntu "$PROJECT_DIR"

    log_info "Project setup complete at $PROJECT_DIR"
}

clone_project() {
    local PROJECT_DIR=$1

    if [ -d "$PROJECT_DIR" ]; then
        log_warn "Project already exists, pulling latest..."
        cd "$PROJECT_DIR" && git pull origin main
    else
        log_info "Cloning project..."
        git clone https://github.com/Manish0085/Git-To-Go.git "$PROJECT_DIR"
    fi
}

create_env_file() {
    local PROJECT_DIR=$1
    local SERVER_IP=$2
    local JWT_SECRET
    local ENCRYPTION_KEY

    JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
    ENCRYPTION_KEY=$(openssl rand -base64 32 | tr -d '\n')

    log_info "Generating .env with server IP: $SERVER_IP"

    cat > "$PROJECT_DIR/.env" << ENVEOF
# =============================================================================
# Git-To-Go — Production Environment Variables
# Generated on: $(date)
# Server IP: ${SERVER_IP}
# =============================================================================

# ==================== PostgreSQL (AWS RDS) ====================
DB_URL=REPLACE_WITH_RDS_ENDPOINT
DB_USERNAME=postgres
DB_PASSWORD=REPLACE_WITH_DB_PASSWORD

# ==================== JWT ====================
JWT_SECRET=${JWT_SECRET}
JWT_EXPIRATION=86400000

# ==================== OAuth2 — GitHub ====================
GITHUB_OAUTH_CLIENT_ID=REPLACE_WITH_GITHUB_CLIENT_ID
GITHUB_OAUTH_CLIENT_SECRET=REPLACE_WITH_GITHUB_CLIENT_SECRET

# ==================== OAuth2 — Google ====================
GOOGLE_OAUTH_CLIENT_ID=REPLACE_WITH_GOOGLE_CLIENT_ID
GOOGLE_OAUTH_CLIENT_SECRET=REPLACE_WITH_GOOGLE_CLIENT_SECRET

# ==================== Email (Brevo SMTP) ====================
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USERNAME=REPLACE_WITH_BREVO_USERNAME
SMTP_PASSWORD=REPLACE_WITH_BREVO_SMTP_KEY
EMAIL_FROM=REPLACE_WITH_YOUR_EMAIL
EMAIL_ENABLED=true

# ==================== Encryption ====================
ENCRYPTION_KEY=${ENCRYPTION_KEY}

# ==================== Server URLs ====================
SERVER_URL=http://git-2-go.duckdns.org
OAUTH2_REDIRECT_URI=http://git-2-go.duckdns.org/oauth2/callback
CORS_ORIGINS=http://git-2-go.duckdns.org
DEPLOYMENT_BASE_URL=http://git-2-go.duckdns.org
EMAIL_VERIFICATION_URL=http://git-2-go.duckdns.org/api/auth/verify-email
WEBHOOK_BASE_URL=http://git-2-go.duckdns.org

# ==================== Build & Logs ====================
BUILDS_DIR=/tmp/git2go/builds
LOGS_DIR=/tmp/git2go/logs
LOGS_RETENTION_DAYS=7
LOG_STORAGE=file

# ==================== Grafana ====================
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=git2go-grafana-2026

# ==================== Server ====================
SERVER_PORT=8080
JPA_DDL_AUTO=update
SHOW_SQL=false
ENVEOF

    log_info ".env file created — JWT_SECRET and ENCRYPTION_KEY auto-generated"
    log_warn "IMPORTANT: Edit .env and replace CHANGE_ME values!"
}

# ==================== PRINT SUMMARY ====================

print_summary() {
    local SERVER_IP
    SERVER_IP=$(get_server_ip)
    local JENKINS_PASS

    JENKINS_PASS=$(cat /var/lib/jenkins/secrets/initialAdminPassword 2>/dev/null || echo "Run: sudo cat /var/lib/jenkins/secrets/initialAdminPassword")

    echo ""
    echo -e "${GREEN}=============================================${NC}"
    echo -e "${GREEN}  SETUP COMPLETE!${NC}"
    echo -e "${GREEN}=============================================${NC}"
    echo ""
    echo -e "  Server IP:         ${CYAN}${SERVER_IP}${NC}"
    echo -e "  Domain:            ${CYAN}http://git-2-go.duckdns.org${NC}"
    echo -e "  Jenkins:           ${CYAN}http://git-2-go.duckdns.org:8082${NC}"
    echo -e "  App (after deploy):${CYAN}http://git-2-go.duckdns.org${NC}"
    echo -e "  Grafana:           ${CYAN}http://git-2-go.duckdns.org:3001${NC}"
    echo -e "  Prometheus:        ${CYAN}http://git-2-go.duckdns.org:9090${NC}"
    echo ""
    echo -e "  Jenkins Password:  ${YELLOW}${JENKINS_PASS}${NC}"
    echo ""
    echo -e "${YELLOW}  NEXT STEPS:${NC}"
    echo "  ────────────────────────────────────────────"
    echo "  1. Create RDS database:"
    echo "     psql -h <RDS_ENDPOINT> -U postgres -c 'CREATE DATABASE deploydb;'"
    echo ""
    echo "  2. Edit .env — replace CHANGE_ME values:"
    echo "     nano /home/ubuntu/Git-To-Go/.env"
    echo ""
    echo "  3. Create GitHub OAuth app:"
    echo "     → Homepage:  http://git-2-go.duckdns.org"
    echo "     → Callback:  http://git-2-go.duckdns.org/login/oauth2/code/github"
    echo ""
    echo "  4. Deploy the app:"
    echo "     cd /home/ubuntu/Git-To-Go"
    echo "     docker compose up -d --build"
    echo ""
    echo "  5. Setup Jenkins:"
    echo "     → Open http://${SERVER_IP}:8082"
    echo "     → Paste initial password"
    echo "     → Install suggested plugins"
    echo "     → Create admin user"
    echo "     → Install plugins: NodeJS, Docker Pipeline, SSH Agent"
    echo "     → Configure tools: JDK-17, NodeJS-18"
    echo "     → Create pipeline from Jenkinsfile"
    echo -e "  ${GREEN}=============================================${NC}"
}

# ==================== MAIN ====================

main() {
    log_step "Git-To-Go Server Setup — Starting"
    check_root

    update_system
    install_docker
    install_java
    install_jenkins
    install_psql_client
    configure_firewall
    setup_project
    print_summary
}

# Run main function
main "$@"
