#!/usr/bin/env bash
# Runs the image built from labs/12-deployment/Dockerfile and checks what the jar smoke test
# checks, plus that the container runs as a non-root user. Needs Docker and curl. It pulls
# nothing from a registry of ours and pushes nothing anywhere.
#
#   docker build -f labs/12-deployment/Dockerfile -t helio-assistant:local .
#   labs/12-deployment/scripts/container-smoke-test.sh helio-assistant:local
set -euo pipefail

IMAGE="${1:-helio-assistant:local}"
PORT="${SMOKE_PORT:-18081}"
NAME="helio-assistant-smoke-$$"
trap 'docker rm -f "$NAME" >/dev/null 2>&1 || true' EXIT

# Configuration check first: no server is started, and no secret is needed.
docker run --rm -e APP_ENV=ci "$IMAGE" --check-config

# Published on loopback only: the container listens on 0.0.0.0 inside, but the host does not expose it.
docker run -d --name "$NAME" -e APP_ENV=ci -p "127.0.0.1:$PORT:8080" "$IMAGE" >/dev/null

for _ in $(seq 1 50); do
  curl -fs "http://127.0.0.1:$PORT/ready" >/dev/null 2>&1 && break
  sleep 0.2
done

echo "health: $(curl -fs "http://127.0.0.1:$PORT/health")"
echo "ready:  $(curl -fs "http://127.0.0.1:$PORT/ready")"
echo "info:   $(curl -fs "http://127.0.0.1:$PORT/info")"
echo "user:   $(docker exec "$NAME" id -u)"
[ "$(docker exec "$NAME" id -u)" != "0" ] || { echo "FAIL: the container runs as root"; exit 1; }

docker stop --time 15 "$NAME" >/dev/null
STATUS="$(docker inspect -f '{{.State.ExitCode}}' "$NAME")"
docker logs "$NAME" 2>&1 | grep -q '"event":"SHUTDOWN_COMPLETED"' || { echo "FAIL: no SHUTDOWN_COMPLETED event"; exit 1; }
[ "$STATUS" = "143" ] || { echo "FAIL: expected exit status 143 after SIGTERM, got $STATUS"; exit 1; }
echo "graceful shutdown in the container: ok (exit $STATUS)"
