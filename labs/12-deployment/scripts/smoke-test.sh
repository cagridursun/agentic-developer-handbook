#!/usr/bin/env bash
# Starts the packaged jar, checks liveness, readiness, and identity, sends SIGTERM, and
# checks the shutdown was graceful. Needs Java 27 and curl; no Docker, no network beyond
# loopback, no credentials.
#
#   ./mvnw -B package -DskipTests -pl labs/12-deployment -am
#   labs/12-deployment/scripts/smoke-test.sh
set -euo pipefail

JAR="${1:-labs/12-deployment/target/lab-12-deployment.jar}"
PORT="${SMOKE_PORT:-18080}"
LOG="$(mktemp)"
trap 'kill "${PID:-0}" 2>/dev/null || true; rm -f "$LOG"' EXIT

APP_ENV=smoke APP_PORT="$PORT" java -jar "$JAR" >"$LOG" 2>&1 &
PID=$!

for _ in $(seq 1 50); do
  curl -fs "http://127.0.0.1:$PORT/ready" >/dev/null 2>&1 && break
  sleep 0.2
done

echo "health: $(curl -fs "http://127.0.0.1:$PORT/health")"
echo "ready:  $(curl -fs "http://127.0.0.1:$PORT/ready")"
echo "info:   $(curl -fs "http://127.0.0.1:$PORT/info")"

kill -TERM "$PID"
set +e
wait "$PID"
STATUS=$?
set -e

grep -q '"event":"SHUTDOWN_COMPLETED"' "$LOG" || { echo "FAIL: no SHUTDOWN_COMPLETED event"; cat "$LOG"; exit 1; }
[ "$STATUS" -eq 143 ] || { echo "FAIL: expected exit status 143 after SIGTERM, got $STATUS"; exit 1; }
echo "graceful shutdown: ok (exit $STATUS)"
