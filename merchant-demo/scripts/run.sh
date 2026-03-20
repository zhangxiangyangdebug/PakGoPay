#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_JAR="$ROOT_DIR/target/merchant-demo.jar"

if ! command -v java >/dev/null 2>&1; then
  echo "java not found. Please install JDK 17+"
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found. Please install Maven"
  exit 1
fi

cd "$ROOT_DIR"

echo "[1/2] Building merchant-demo..."
mvn -q -DskipTests package

echo "[2/2] Running app..."
exec java -jar "$TARGET_JAR"
