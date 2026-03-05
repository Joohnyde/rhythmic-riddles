#!/usr/bin/env bash
set -euo pipefail

# -------------------------------------------------------
# Verifies formatting and static checks for the repository
# Used by CI pipeline gates and can also be run locally.
# -------------------------------------------------------

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

BACKEND_DIR="${ROOT_DIR}/apps/backend"
FRONTEND_DIR="${ROOT_DIR}/apps/frontend"

echo "================================================="
echo "Running repository code-quality verification"
echo "================================================="

# -------------------------------------------------------
# Backend checks
# -------------------------------------------------------

if [ -d "$BACKEND_DIR" ]; then
  echo ""
  echo "Backend checks (Spotless + Checkstyle)"
  echo "---------------------------------------"

  cd "$BACKEND_DIR"

  echo "Checking Java formatting (Spotless)..."
  mvn -B -ntp spotless:check

  echo "Running Checkstyle..."
  mvn -B -ntp -Pci checkstyle:check

  cd "$ROOT_DIR"
else
  echo "Backend directory not found, skipping backend checks."
fi

# -------------------------------------------------------
# Frontend checks
# -------------------------------------------------------

if [ -d "$FRONTEND_DIR" ]; then
  echo ""
  echo "Frontend checks (Prettier + ESLint)"
  echo "-----------------------------------"

  cd "$FRONTEND_DIR"

  echo "Installing dependencies..."
  npm ci

  echo "Checking formatting (Prettier)..."
  npm run format:check

  echo "Running ESLint..."
  npm run lint

  cd "$ROOT_DIR"
else
  echo "Frontend directory not found, skipping frontend checks."
fi

echo ""
echo "================================================="
echo "All code quality checks passed."
echo "================================================="
