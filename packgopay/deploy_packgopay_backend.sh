#!/usr/bin/env bash
set -euo pipefail

# ===== configurable =====
REMOTE_HOST="8.219.132.103"
REMOTE_USER="root"
REMOTE_SSH="${REMOTE_USER}@${REMOTE_HOST}"
SSH_OPTS="-o ControlMaster=auto -o ControlPersist=10m -o ControlPath=~/.ssh/cm-%r@%h:%p"
IMAGE_NAME="packgopay-backend:latest"
CONTAINER_NAME="pakgopay"
CONTAINER_PORT="8090"
NETWORK_NAME="pakgopay-net"
# ========================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -f pom.xml ]; then
  echo "ERROR: pom.xml not found in $SCRIPT_DIR" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found" >&2
  exit 1
fi

if ! command -v ssh >/dev/null 2>&1 || ! command -v scp >/dev/null 2>&1; then
  echo "ERROR: ssh/scp not found" >&2
  exit 1
fi

# prompt for secret env var
read -r -s -p "Enter JASYPT_ENCRYPTOR_PASSWORD: " JASYPT_ENCRYPTOR_PASSWORD
printf "\n"
if [ -z "$JASYPT_ENCRYPTOR_PASSWORD" ]; then
  echo "ERROR: JASYPT_ENCRYPTOR_PASSWORD is required" >&2
  exit 1
fi

# prompt for active profile
read -r -p "Enter SPRING_PROFILES_ACTIVE (e.g. prod/dev): " SPRING_PROFILES_ACTIVE
if [ -z "$SPRING_PROFILES_ACTIVE" ]; then
  echo "ERROR: SPRING_PROFILES_ACTIVE is required" >&2
  exit 1
fi

# 1) build package locally (using dockerized Maven)
docker run --rm \
  -v "$HOME/.m2":/root/.m2 \
  -v "$SCRIPT_DIR":/app \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn clean package -DskipTests

# 2) build docker image locally
if ! docker buildx inspect >/dev/null 2>&1; then
  docker buildx create --use >/dev/null
fi
docker buildx build --platform linux/amd64 -t "$IMAGE_NAME" --load .

# 3) upload image to remote
IMAGE_TAR="/tmp/${IMAGE_NAME//[:\/]/_}.tar"
docker save -o "$IMAGE_TAR" "$IMAGE_NAME"
OLD_IMAGE_ID="$(ssh $SSH_OPTS "$REMOTE_SSH" "docker image inspect -f '{{.Id}}' $IMAGE_NAME 2>/dev/null || true")"
scp $SSH_OPTS "$IMAGE_TAR" "$REMOTE_SSH:/tmp/packgopay_backend_image.tar"
ssh $SSH_OPTS "$REMOTE_SSH" "docker load -i /tmp/packgopay_backend_image.tar && rm -f /tmp/packgopay_backend_image.tar"
rm -f "$IMAGE_TAR"

# 4) run container on remote
ssh $SSH_OPTS "$REMOTE_SSH" "if [ -n \"\$(docker ps -q -f publish=$CONTAINER_PORT)\" ]; then docker ps -q -f publish=$CONTAINER_PORT | xargs -r docker rm -f; fi"
ssh $SSH_OPTS "$REMOTE_SSH" "if [ -n \"\$(docker ps -q -f name=^/${CONTAINER_NAME}\$)\" ]; then docker stop $CONTAINER_NAME; fi"
ssh $SSH_OPTS "$REMOTE_SSH" "docker rm -f $CONTAINER_NAME >/dev/null 2>&1 || true"
ssh $SSH_OPTS "$REMOTE_SSH" "docker network create $NETWORK_NAME >/dev/null 2>&1 || true"
ssh $SSH_OPTS "$REMOTE_SSH" "docker run -d --name $CONTAINER_NAME --network $NETWORK_NAME -p $CONTAINER_PORT:$CONTAINER_PORT -e JASYPT_ENCRYPTOR_PASSWORD='$JASYPT_ENCRYPTOR_PASSWORD' -e SPRING_PROFILES_ACTIVE='$SPRING_PROFILES_ACTIVE' $IMAGE_NAME"
NEW_IMAGE_ID="$(ssh $SSH_OPTS "$REMOTE_SSH" "docker image inspect -f '{{.Id}}' $IMAGE_NAME 2>/dev/null || true")"
if [ -n "$OLD_IMAGE_ID" ] && [ -n "$NEW_IMAGE_ID" ] && [ "$OLD_IMAGE_ID" != "$NEW_IMAGE_ID" ]; then
  ssh $SSH_OPTS "$REMOTE_SSH" "docker image rm -f $OLD_IMAGE_ID >/dev/null 2>&1 || true"
fi

echo "Done. Image=$IMAGE_NAME Container=$CONTAINER_NAME"
