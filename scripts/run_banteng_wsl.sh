#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: run_banteng_wsl.sh DATABASE PORT MANIFEST" >&2
  exit 64
fi

database="$1"
port="$2"
manifest="$3"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd -- "$script_dir/.." && pwd)"
distribution_lib="$repo_dir/build/install/banteng/lib"

if [[ ! -d "$distribution_lib" ]]; then
  echo "installed Banteng libraries not found: $distribution_lib" >&2
  exit 66
fi
if [[ ! -f "$manifest" ]]; then
  echo "target manifest not found: $manifest" >&2
  exit 66
fi

promote_numbers="$(
  jq -r '
    if (.features["option.PROMOTE_NUMBERS"] | type) == "boolean"
    then .features["option.PROMOTE_NUMBERS"]
    else error("option.PROMOTE_NUMBERS must be boolean")
    end
  ' "$manifest"
)"

exec /opt/java/25/bin/java \
  -classpath "$distribution_lib/*" \
  moo.app.Banteng \
  --database "$database" \
  --checkpoint "${database}.new" \
  --listen-address 127.0.0.1 \
  --port "$port" \
  --promote-numbers="$promote_numbers"
