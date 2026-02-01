#!/usr/bin/env bash
set -euo pipefail

echo "== Running all tests (placeholder) =="

# Backend
if [ -d "apps/quiz-game/backend" ]; then
  echo "Backend tests..."
  (cd apps/quiz-game/backend && echo "TODO: mvn test")
fi

# Frontend
if [ -d "apps/quiz-game/frontend" ]; then
  echo "Frontend tests..."
  (cd apps/quiz-game/frontend && echo "TODO: npm test")
fi

echo "Done."
