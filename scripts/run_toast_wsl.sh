#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: run_toast_wsl.sh DATABASE PORT MANIFEST" >&2
  exit 64
fi

database="$1"
port="$2"
manifest="$3"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

"$script_dir/verify_toast_profile_wsl.sh" "$manifest"
binary_path="$(jq -er '.binary_path | select(type == "string" and length > 0)' "$manifest")"

exec "$binary_path" "$database" "${database}.new" "$port"
