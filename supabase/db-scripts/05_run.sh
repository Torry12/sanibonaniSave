#!/usr/bin/env bash
set -euo pipefail

# 05_run.sh - POSIX shell runner for applying consolidated schema, migrations, and seed
# Usage:
#   ./05_run.sh -h localhost -p 5432 -U postgres -d sanibonani -W
#   PGPASSWORD=mypass ./05_run.sh -h localhost -p 5432 -U postgres -d sanibonani
# Options:
#   -h HOST
#   -p PORT (default 5432)
#   -U USER
#   -d DATABASE
#   -n    Dry run (print actions, do not execute)
#   -i    Create an inlined single SQL file (in supabase/db-scripts/inlined_all.sql)
#   -x    Execute the generated inlined SQL (implies -i)
#   -?    Show usage

usage() {
  sed -n '1,120p' "$0" | sed -n '1,100p'
  echo
  echo "Options:"
  echo "  -h HOST   (required)"
  echo "  -p PORT   (default 5432)"
  echo "  -U USER   (required)"
  echo "  -d DB     (required)"
  echo "  -n        Dry run"
  echo "  -i        Create inlined SQL file"
  echo "  -x        Execute generated inlined SQL (implies -i)"
  echo "  -?        Show this help"
  exit 1
}

HOST=""
PORT=5432
USER=""
DB=""
DRYRUN=false
CREATE_INLINED=false
EXEC_INLINED=false

while getopts ":h:p:U:d:nix?" opt; do
  case ${opt} in
    h) HOST=${OPTARG} ;;
    p) PORT=${OPTARG} ;;
    U) USER=${OPTARG} ;;
    d) DB=${OPTARG} ;;
    n) DRYRUN=true ;;
    i) CREATE_INLINED=true ;;
    x) EXEC_INLINED=true ; CREATE_INLINED=true ;;
    ?) usage ;;
  esac
done

if [ -z "$HOST" ] || [ -z "$USER" ] || [ -z "$DB" ]; then
  echo "Host, User and Database are required." >&2
  usage
fi

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_SUPABASE=$(cd "$SCRIPT_DIR/.." && pwd)

# Files
SCHEMA_CONSOLIDATED="$REPO_SUPABASE/CONSOLIDATED_SCHEMA_FOR_DASHBOARD.sql"
SEED_CONSOLIDATED="$REPO_SUPABASE/CONSOLIDATED_FOR_DASHBOARD_SCHEMA_PLUS_SAFE_SEED.sql"
CREATE_SCHEMA_SCRIPT="$SCRIPT_DIR/01_create_schema.sql"
MIGRATION_GLOB="$REPO_SUPABASE/??_*.sql"
INLINED_OUT="$SCRIPT_DIR/inlined_all.sql"

psql_exec() {
  local file="$1"
  echo ">> psql -h $HOST -p $PORT -U $USER -d $DB -f $file"
  if [ "$DRYRUN" = true ]; then
    return 0
  fi
  PGPASSWORD=${PGPASSWORD:-} psql -h "$HOST" -p "$PORT" -U "$USER" -d "$DB" -f "$file"
}

# 1) Run create schema (uses consolidated schema include)
if [ ! -f "$CREATE_SCHEMA_SCRIPT" ]; then
  echo "Missing $CREATE_SCHEMA_SCRIPT" >&2
  exit 2
fi

echo "==> Running create schema script: $CREATE_SCHEMA_SCRIPT"
psql_exec "$CREATE_SCHEMA_SCRIPT"

# 2) Run migrations in lexical order, skipping consolidated/seed files
echo "==> Applying migrations from $REPO_SUPABASE"
for m in $(ls $REPO_SUPABASE | sort); do
  if [[ "$m" =~ ^[0-9][0-9]_.*\.sql$ ]]; then
    # Skip consolidated dumps and seed files explicitly by pattern match
    case "$m" in
      *CONSOLIDATED*|*CONSOLIDATED_SCHEMA*|*CONSOLIDATED_FULL*|*FOR_DASHBOARD*|*SEED*|01_DATABASE_SCHEMA.sql)
        echo "- Skipping $m"; continue ;;
      *)
        full="$REPO_SUPABASE/$m"
        # skip if the file is inside db-scripts directory
        if [[ "$full" == *"/db-scripts/"* ]]; then
          continue
        fi
        echo "- Applying migration: $m"
        psql_exec "$full"
        ;;
    esac
  fi
done

# 3) Seed with consolidated safe seed (if present)
if [ -f "$SEED_CONSOLIDATED" ]; then
  echo "==> Applying consolidated seed: $SEED_CONSOLIDATED"
  psql_exec "$SEED_CONSOLIDATED"
else
  echo "No consolidated seed found at $SEED_CONSOLIDATED, skipping seed step."
fi

# Optionally create inlined SQL file (schema + migrations + seed)
if [ "$CREATE_INLINED" = true ]; then
  echo "==> Generating inlined SQL: $INLINED_OUT"
  rm -f "$INLINED_OUT"
  echo "-- Inlined consolidated schema + migrations + seed" > "$INLINED_OUT"
  echo "-- Generated at $(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$INLINED_OUT"
  echo "\n-- ==== Consolidated schema ====\n" >> "$INLINED_OUT"
  cat "$SCHEMA_CONSOLIDATED" >> "$INLINED_OUT"

  echo "\n-- ==== Migrations ====\n" >> "$INLINED_OUT"
  for m in $(ls $REPO_SUPABASE | sort); do
    if [[ "$m" =~ ^[0-9][0-9]_.*\.sql$ ]]; then
      case "$m" in
        *CONSOLIDATED*|*SEED*|01_DATABASE_SCHEMA.sql)
          echo "-- Skipping $m" >> "$INLINED_OUT"; continue ;;
        *)
          full="$REPO_SUPABASE/$m"
          if [[ "$full" == *"/db-scripts/"* ]]; then
            continue
          fi
          echo "\n-- Migration: $m\n" >> "$INLINED_OUT"
          cat "$full" >> "$INLINED_OUT"
          ;;
      esac
    fi
  done

  if [ -f "$SEED_CONSOLIDATED" ]; then
    echo "\n-- ==== Seed ====\n" >> "$INLINED_OUT"
    cat "$SEED_CONSOLIDATED" >> "$INLINED_OUT"
  fi

  echo "Inlined SQL created: $INLINED_OUT"

  if [ "$EXEC_INLINED" = true ]; then
    echo "==> Executing inlined SQL"
    psql_exec "$INLINED_OUT"
  fi
fi

echo "All done."

