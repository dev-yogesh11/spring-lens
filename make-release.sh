#!/bin/bash
# =============================================================================
# make-release.sh — Springlens Release Builder
# Usage: ./make-release.sh [version]
# Example: ./make-release.sh 1.0.0
# =============================================================================

set -e

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# ── Config ────────────────────────────────────────────────────────────────────
PROJECT_NAME="springlens-ai-platform"
APP_IMAGE="spring-lens-app"
RAGAS_IMAGE="spring-lens-ragas"
VERSION=${1:-$(date +"%Y%m%d-%H%M%S")}
RELEASE_NAME="${PROJECT_NAME}-release-${VERSION}"
RELEASE_DIR="./releases/${RELEASE_NAME}"
OUTPUT_TAR="./releases/${RELEASE_NAME}.tar.gz"

# ── Helpers ───────────────────────────────────────────────────────────────────
log()     { echo -e "${BLUE}[INFO]${NC}  $1"; }
success() { echo -e "${GREEN}[OK]${NC}    $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }
section() { echo -e "\n${BOLD}${CYAN}=== $1 ===${NC}"; }

# ── Preflight Checks ──────────────────────────────────────────────────────────
section "Preflight Checks"

command -v docker &>/dev/null        || error "Docker is not installed or not in PATH"
docker info &>/dev/null              || error "Docker daemon is not running"
command -v docker compose &>/dev/null || error "Docker Compose v2 is not installed"
[ -f "docker-compose.yaml" ]         || error "docker-compose.yaml not found in current directory"
[ -f ".env" ]                        || error ".env file not found — copy .env.example and fill in secrets"

success "All preflight checks passed"

# ── Build Images ──────────────────────────────────────────────────────────────
section "Building Docker Images"

log "Building app image..."
docker compose build app
success "App image built → ${APP_IMAGE}"

log "Building ragas image..."
docker compose build ragas
success "Ragas image built → ${RAGAS_IMAGE}"

# ── Prepare Release Directory ─────────────────────────────────────────────────
section "Preparing Release Package"

rm -rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"
log "Release directory created → ${RELEASE_DIR}"

# ── Export Docker Images ──────────────────────────────────────────────────────
log "Exporting Docker images to tar (this may take a while)..."
docker save "${APP_IMAGE}" "${RAGAS_IMAGE}" | gzip > "${RELEASE_DIR}/images.tar.gz"
success "Images exported → ${RELEASE_DIR}/images.tar.gz"

# ── Copy Deployment Files ─────────────────────────────────────────────────────
log "Copying deployment files..."
cp docker-compose.yaml "${RELEASE_DIR}/docker-compose.yaml"
success "docker-compose.yaml copied"

# Copy .env.example if exists (never copy real .env into release)
if [ -f ".env.example" ]; then
  cp .env.example "${RELEASE_DIR}/.env.example"
  success ".env.example copied (reference only)"
fi

# ── Generate deploy.sh inside release ────────────────────────────────────────
log "Generating deploy.sh script..."
cat > "${RELEASE_DIR}/deploy.sh" << 'EOF'
#!/bin/bash
# =============================================================
# deploy.sh — Run this on the TARGET machine to deploy
# =============================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()     { echo -e "${GREEN}[DEPLOY]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}   $1"; }
error()   { echo -e "${RED}[ERROR]${NC}  $1"; exit 1; }

command -v docker &>/dev/null         || error "Docker not installed on this machine"
command -v docker compose &>/dev/null || error "Docker Compose v2 not installed"

[ -f ".env" ] || error ".env file missing! Copy .env.example → .env and fill in your secrets"
[ -f "images.tar.gz" ] || error "images.tar.gz not found in current directory"

log "Loading Docker images..."
docker load -i images.tar.gz

log "Starting all services..."
docker compose up -d

log "Waiting for services to be healthy..."
sleep 10
docker compose ps

echo ""
echo -e "${GREEN}✅ Deployment complete!${NC}"
echo "   App  → http://localhost:8087"
echo "   Ragas → http://localhost:8088"
EOF
chmod +x "${RELEASE_DIR}/deploy.sh"
success "deploy.sh generated"

# ── Generate README inside release ───────────────────────────────────────────
log "Generating README.txt..."
cat > "${RELEASE_DIR}/README.txt" << EOF
======================================================
  Springlens Release — ${VERSION}
  Built on: $(date)
======================================================

CONTENTS:
  images.tar.gz     → Docker images (app + ragas)
  docker-compose.yml → Service definitions
  .env.example      → Environment variable reference
  deploy.sh         → Deployment helper script
  README.txt        → This file

REQUIREMENTS ON TARGET MACHINE:
  - Docker 20+
  - Docker Compose v2
  - Internet access (for Redis & Postgres pulls)
  - Ports 8087, 8088, 5432, 6380 available

DEPLOYMENT STEPS:
  1. Copy this entire folder to the target machine
  2. Create your .env file:
       cp .env.example .env
       nano .env   # fill in all secrets
  3. Run the deploy script:
       chmod +x deploy.sh
       ./deploy.sh

SERVICES:
  App      → http://<host>:8087
  Ragas    → http://<host>:8088
  Postgres → localhost:5432  (internal)
  Redis    → localhost:6380  (internal)

NOTE:
  Redis and Postgres images are pulled from Docker Hub
  automatically on first deploy. Internet access required.
======================================================
EOF
success "README.txt generated"

# ── Package Everything into Final Tar ─────────────────────────────────────────
section "Packaging Release"

mkdir -p "./releases"
tar -czf "${OUTPUT_TAR}" -C "./releases" "${RELEASE_NAME}"
success "Release package created → ${OUTPUT_TAR}"

# ── Cleanup Temp Dir ──────────────────────────────────────────────────────────
rm -rf "${RELEASE_DIR}"

# ── Summary ───────────────────────────────────────────────────────────────────
section "Release Summary"

RELEASE_SIZE=$(du -sh "${OUTPUT_TAR}" | cut -f1)
echo -e "  ${BOLD}Version:${NC}  ${VERSION}"
echo -e "  ${BOLD}Output:${NC}   ${OUTPUT_TAR}"
echo -e "  ${BOLD}Size:${NC}     ${RELEASE_SIZE}"
echo -e "  ${BOLD}Images:${NC}   ${APP_IMAGE}, ${RAGAS_IMAGE}"
echo ""
echo -e "${GREEN}${BOLD} Release ready to ship!${NC}"
echo ""
echo "  Ship this file to target machine:"
echo "    scp ${OUTPUT_TAR} user@server:/opt/springlens/"
echo ""
echo "  On target machine:"
echo "    tar -xzf ${RELEASE_NAME}.tar.gz"
echo "    cd ${RELEASE_NAME}"
echo "    cp .env.example .env && nano .env"
echo "    ./deploy.sh"