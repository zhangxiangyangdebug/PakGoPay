#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKDIR_DEFAULT="$(cd "${SCRIPT_DIR}/.." && pwd)"

TARGET_HOST="${TARGET_HOST:-8.219.132.103}"
TARGET_USER="${TARGET_USER:-root}"
TARGET_PORT="${TARGET_PORT:-8092}"
WORKDIR="${WORKDIR:-$WORKDIR_DEFAULT}"

IMAGE_NAME="${IMAGE_NAME:-thirdparty-demo:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-thirdparty-demo}"
NETWORK_NAME="${NETWORK_NAME:-pakgopay-net}"
REMOTE_LOG_DIR="${REMOTE_LOG_DIR:-/data/thirdparty-demo/logs}"
CONTAINER_LOG_DIR="${CONTAINER_LOG_DIR:-/app/logs}"

SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
DOCKER_LOG_MAX_SIZE="${DOCKER_LOG_MAX_SIZE:-200m}"
DOCKER_LOG_MAX_FILE="${DOCKER_LOG_MAX_FILE:-10}"

SSH_OPTS="${SSH_OPTS:--o ControlMaster=auto -o ControlPersist=10m -o ControlPath=~/.ssh/cm-%r@%h:%p}"
CHECK_REMOTE="${CHECK_REMOTE:-no}"

usage() {
  cat <<USAGE
Usage:
  bash scripts/deploy_thirdparty_demo.sh [options]

Options:
  --host <host>           target host (default: ${TARGET_HOST})
  --user <user>           target user (default: ${TARGET_USER})
  --port <port>           container port mapping (default: ${TARGET_PORT})
  --workdir <dir>         local project dir (default: ${WORKDIR_DEFAULT})
  -h, --help

Env overrides:
  TARGET_HOST TARGET_USER TARGET_PORT WORKDIR
  IMAGE_NAME CONTAINER_NAME NETWORK_NAME
  REMOTE_LOG_DIR CONTAINER_LOG_DIR
  SPRING_PROFILES_ACTIVE DOCKER_LOG_MAX_SIZE DOCKER_LOG_MAX_FILE

Example:
  bash scripts/deploy_thirdparty_demo.sh --host 8.219.132.103 --user root --port 8092
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) TARGET_HOST="${2:-}"; shift 2 ;;
    --user) TARGET_USER="${2:-}"; shift 2 ;;
    --port) TARGET_PORT="${2:-}"; shift 2 ;;
    --workdir) WORKDIR="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "ERROR: unknown arg: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: command not found: $1" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd ssh
require_cmd scp

if [[ ! -d "$WORKDIR" ]]; then
  echo "ERROR: WORKDIR not found: $WORKDIR" >&2
  exit 1
fi

cd "$WORKDIR"
if [[ ! -f pom.xml ]]; then
  echo "ERROR: pom.xml not found in $WORKDIR" >&2
  exit 1
fi

REMOTE_SSH="${TARGET_USER}@${TARGET_HOST}"

echo "[thirdparty-demo] target: ${REMOTE_SSH}"

if [[ "$CHECK_REMOTE" != "yes" && "$CHECK_REMOTE" != "no" ]]; then
  echo "ERROR: CHECK_REMOTE must be yes or no" >&2
  exit 1
fi

MVN_ARGS="clean package -DskipTests -Dmaven.repo.local=/root/.m2/repository"
if [[ "$CHECK_REMOTE" == "no" ]]; then
  MVN_ARGS="${MVN_ARGS} -nsu"
fi

SETTINGS_MOUNT=()
if [[ -f "$HOME/.m2/settings-docker.xml" ]]; then
  SETTINGS_MOUNT=(-v "$HOME/.m2/settings-docker.xml:/root/.m2/settings.xml")
fi

docker run --rm \
  -v "$HOME/.m2":/root/.m2 \
  "${SETTINGS_MOUNT[@]}" \
  -v "$WORKDIR":/app \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn ${MVN_ARGS}

if ! docker buildx inspect >/dev/null 2>&1; then
  docker buildx create --use >/dev/null
fi

docker buildx build --platform linux/amd64 -t "$IMAGE_NAME" --load .

IMAGE_TAR="/tmp/${IMAGE_NAME//[:\/]/_}.tar"
OLD_IMAGE_ID="$(ssh $SSH_OPTS "$REMOTE_SSH" "docker image inspect -f '{{.Id}}' $IMAGE_NAME 2>/dev/null || true")"

docker save -o "$IMAGE_TAR" "$IMAGE_NAME"
scp $SSH_OPTS "$IMAGE_TAR" "$REMOTE_SSH:/tmp/thirdparty_demo_image.tar"
ssh $SSH_OPTS "$REMOTE_SSH" "docker load -i /tmp/thirdparty_demo_image.tar && rm -f /tmp/thirdparty_demo_image.tar"
rm -f "$IMAGE_TAR"

ssh $SSH_OPTS "$REMOTE_SSH" "if [ -n \"\$(docker ps -q -f publish=${TARGET_PORT})\" ]; then docker ps -q -f publish=${TARGET_PORT} | xargs -r docker rm -f; fi"
ssh $SSH_OPTS "$REMOTE_SSH" "docker rm -f ${CONTAINER_NAME} >/dev/null 2>&1 || true"
ssh $SSH_OPTS "$REMOTE_SSH" "docker network create ${NETWORK_NAME} >/dev/null 2>&1 || true"
ssh $SSH_OPTS "$REMOTE_SSH" "mkdir -p ${REMOTE_LOG_DIR}"
ssh $SSH_OPTS "$REMOTE_SSH" "docker run -d --name ${CONTAINER_NAME} --network ${NETWORK_NAME} -p ${TARGET_PORT}:8092 -v ${REMOTE_LOG_DIR}:${CONTAINER_LOG_DIR} --log-driver=json-file --log-opt max-size=${DOCKER_LOG_MAX_SIZE} --log-opt max-file=${DOCKER_LOG_MAX_FILE} -e SPRING_PROFILES_ACTIVE='${SPRING_PROFILES_ACTIVE}' ${IMAGE_NAME}"

NEW_IMAGE_ID="$(ssh $SSH_OPTS "$REMOTE_SSH" "docker image inspect -f '{{.Id}}' $IMAGE_NAME 2>/dev/null || true")"
if [[ -n "$OLD_IMAGE_ID" && -n "$NEW_IMAGE_ID" && "$OLD_IMAGE_ID" != "$NEW_IMAGE_ID" ]]; then
  ssh $SSH_OPTS "$REMOTE_SSH" "docker image rm -f $OLD_IMAGE_ID >/dev/null 2>&1 || true"
fi

echo "[thirdparty-demo] done: ${REMOTE_SSH}"
