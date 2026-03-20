#!/usr/bin/env bash
set -euo pipefail

# Build + package merchant demo.
# Usage:
#   1) Interactive mode:
#      ./scripts/package.sh
#   2) Argument mode:
#      ./scripts/package.sh --os mac --type all
#      ./scripts/package.sh --os mac --type dmg
#      ./scripts/package.sh --os windows --type msi

OS=""
TYPE="all"
APP_NAME="MerchantDemo"
MAIN_JAR="merchant-demo.jar"
MAIN_CLASS="com.pakgopay.demo.MerchantDemoApp"

HOST_UNAME="$(uname -s)"
HOST_OS=""
case "$HOST_UNAME" in
  Darwin) HOST_OS="mac" ;;
  MINGW*|MSYS*|CYGWIN*|Windows_NT) HOST_OS="windows" ;;
  *) HOST_OS="unknown" ;;
esac

while [[ $# -gt 0 ]]; do
  case "$1" in
    --os) OS="$2"; shift 2 ;;
    --type) TYPE="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

choose_os() {
  if [[ "$HOST_OS" == "mac" ]]; then
    OS="mac"
    return
  fi
  if [[ "$HOST_OS" == "windows" ]]; then
    OS="windows"
    return
  fi
  echo "Select target OS:"
  echo "  1) mac"
  echo "  2) windows"
  read -r -p "Enter choice [1-2]: " os_choice
  case "$os_choice" in
    1) OS="mac" ;;
    2) OS="windows" ;;
    *) echo "Invalid OS choice"; exit 1 ;;
  esac
}

choose_type() {
  if [[ "$OS" == "mac" ]]; then
    echo "Select package type:"
    echo "  1) all (dmg + pkg)"
    echo "  2) dmg"
    echo "  3) pkg"
    read -r -p "Enter choice [1-3]: " type_choice
    case "$type_choice" in
      1) TYPE="all" ;;
      2) TYPE="dmg" ;;
      3) TYPE="pkg" ;;
      *) echo "Invalid package type"; exit 1 ;;
    esac
  elif [[ "$OS" == "windows" ]]; then
    echo "Select package type:"
    echo "  1) all (msi + exe)"
    echo "  2) msi"
    echo "  3) exe"
    read -r -p "Enter choice [1-3]: " type_choice
    case "$type_choice" in
      1) TYPE="all" ;;
      2) TYPE="msi" ;;
      3) TYPE="exe" ;;
      *) echo "Invalid package type"; exit 1 ;;
    esac
  else
    echo "Unsupported OS: $OS"
    exit 1
  fi
}

if [[ -z "$OS" ]]; then
  choose_os
  choose_type
fi

if [[ "$HOST_OS" == "mac" && "$OS" == "windows" ]]; then
  echo "Current host is macOS. Windows package must be built on Windows."
  exit 1
fi
if [[ "$HOST_OS" == "windows" && "$OS" == "mac" ]]; then
  echo "Current host is Windows. macOS package must be built on macOS."
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
TARGET_DIR="$ROOT_DIR/target"

cd "$ROOT_DIR"
mvn -q -DskipTests package
mkdir -p "$DIST_DIR"

if [[ "$OS" == "mac" ]]; then
  if [[ "$TYPE" == "all" || "$TYPE" == "dmg" ]]; then
    jpackage \
      --name "$APP_NAME" \
      --input "$TARGET_DIR" \
      --main-jar "$MAIN_JAR" \
      --main-class "$MAIN_CLASS" \
      --type dmg \
      --dest "$DIST_DIR"
    echo "Generated DMG in $DIST_DIR"
  fi
  if [[ "$TYPE" == "all" || "$TYPE" == "pkg" ]]; then
    jpackage \
      --name "$APP_NAME" \
      --input "$TARGET_DIR" \
      --main-jar "$MAIN_JAR" \
      --main-class "$MAIN_CLASS" \
      --type pkg \
      --dest "$DIST_DIR"
    echo "Generated PKG in $DIST_DIR"
  fi
elif [[ "$OS" == "windows" ]]; then
  if [[ "$TYPE" == "all" || "$TYPE" == "msi" ]]; then
    jpackage \
      --name "$APP_NAME" \
      --input "$TARGET_DIR" \
      --main-jar "$MAIN_JAR" \
      --main-class "$MAIN_CLASS" \
      --type msi \
      --win-menu \
      --win-shortcut \
      --dest "$DIST_DIR"
    echo "Generated MSI in $DIST_DIR"
  fi
  if [[ "$TYPE" == "all" || "$TYPE" == "exe" ]]; then
    jpackage \
      --name "$APP_NAME" \
      --input "$TARGET_DIR" \
      --main-jar "$MAIN_JAR" \
      --main-class "$MAIN_CLASS" \
      --type exe \
      --win-menu \
      --win-shortcut \
      --dest "$DIST_DIR"
    echo "Generated EXE in $DIST_DIR"
  fi
else
  echo "Unsupported --os: $OS"
  exit 1
fi
