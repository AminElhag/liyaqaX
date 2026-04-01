#!/usr/bin/env bash
set -uo pipefail

PASS=0
FAIL=0

check() {
  local label="$1"
  shift
  if "$@" > /dev/null 2>&1; then
    printf "  ✅ PASS  %s\n" "$label"
    PASS=$((PASS + 1))
  else
    printf "  ❌ FAIL  %s\n" "$label"
    FAIL=$((FAIL + 1))
  fi
}

container_healthy() {
  local status
  status="$(docker inspect --format='{{.State.Health.Status}}' "$1" 2>/dev/null)"
  [ "$status" = "healthy" ]
}

echo ""
echo "=== Local Environment Verification ==="
echo ""

echo "── Docker ──"
check "Docker daemon is running" docker info

echo ""
echo "── Containers ──"
check "arena_postgres is healthy" container_healthy arena_postgres
check "arena_redis is healthy"    container_healthy arena_redis

echo ""
echo "── Backend ──"
check "Backend health (localhost:8080)" curl -sf http://localhost:8080/actuator/health

echo ""
echo "── Frontends ──"
check "web-nexus (localhost:5173)" curl -sf http://localhost:5173
check "web-pulse (localhost:5174)" curl -sf http://localhost:5174
check "web-coach (localhost:5175)" curl -sf http://localhost:5175
check "web-arena (localhost:5176)" curl -sf http://localhost:5176

echo ""
echo "────────────────────────────"
printf "Results: %d passed, %d failed\n" "$PASS" "$FAIL"
echo "────────────────────────────"
echo ""

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
