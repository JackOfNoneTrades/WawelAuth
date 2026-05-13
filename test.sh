#!/usr/bin/env bash
set -euo pipefail

: "${YGG_BASE_URL:=http://127.0.0.1:25565/auth}"
: "${YGG_USERNAME:=test}"
: "${YGG_PASSWORD:=1234}"
: "${YGG_TIMEOUT_SECONDS:=8.0}"

export YGG_BASE_URL
export YGG_USERNAME
export YGG_PASSWORD
export YGG_TIMEOUT_SECONDS

if ! curl -kfsS --max-time 2 "${YGG_BASE_URL%/}/" >/dev/null; then
  echo "WawelAuth test server is not reachable at ${YGG_BASE_URL%/}/" >&2
  echo "Start the server first, or override YGG_BASE_URL before running this script." >&2
  exit 2
fi

exec .venv/bin/pytest -q tests/yggdrasil -m integration "$@"
