#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: run_banteng_wsl.sh DATABASE PORT" >&2
  exit 64
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"
distribution_lib="$repo_dir/build/install/banteng/lib"

if [[ ! -d "$distribution_lib" ]]; then
  echo "installed Banteng libraries not found: $distribution_lib" >&2
  exit 66
fi

if [[ -z "${BANTENG_JAVA_HOME:-}" ]]; then
  echo "BANTENG_JAVA_HOME must name the WSL path to a Java 25 installation" >&2
  exit 78
fi
if [[ ! -x "$BANTENG_JAVA_HOME/bin/java.exe" ]]; then
  echo "Java launcher not found: $BANTENG_JAVA_HOME/bin/java.exe" >&2
  exit 66
fi

database_win="$(wslpath -w "$1")"
checkpoint_win="$(wslpath -w "$1.new")"
classpath_win="$(wslpath -w "$distribution_lib")\\*"
listen_address="${BANTENG_LISTEN_ADDRESS:-127.0.0.1}"

exec "$BANTENG_JAVA_HOME/bin/java.exe" \
  -classpath "$classpath_win" \
  moo.app.Banteng \
  --database "$database_win" \
  --checkpoint "$checkpoint_win" \
  --listen-address "$listen_address" \
  --port "$2"
