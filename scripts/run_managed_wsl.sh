#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "usage: run_managed_wsl.sh {toast|banteng} {stock|mongoose|toastcore} SUITE..." >&2
  exit 64
fi

target="$1"
profile="$2"
shift 2

case "$target" in
  toast|banteng) ;;
  *) echo "invalid target: $target" >&2; exit 64 ;;
esac

case "$profile" in
  stock|mongoose|toastcore) ;;
  *) echo "invalid profile: $profile" >&2; exit 64 ;;
esac

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
banteng_repo="$(cd -- "$script_dir/.." && pwd)"
conformance_repo="$(cd -- "$banteng_repo/../moo-conformance-tests" && pwd)"

case "$profile" in
  stock) oracle_relative="profiles/toast/stock-wsl-testdb.json" ;;
  mongoose) oracle_relative="profiles/toast/mongoose-wsl-mongoose.json" ;;
  toastcore) oracle_relative="profiles/toast/stock-wsl-toastcore.json" ;;
esac
oracle_manifest="$banteng_repo/$oracle_relative"

if [[ "$target" == "toast" ]]; then
  target_manifest="$oracle_manifest"
  launcher="$script_dir/run_toast_wsl.sh"
  launcher_manifest="$oracle_manifest"
else
  target_relative="profiles/banteng/$profile.json"
  target_manifest="$banteng_repo/$target_relative"
  launcher="$script_dir/run_banteng_wsl.sh"
  launcher_manifest="$target_manifest"
fi

suites=("$@")
if [[ "${suites[0]}" == "profile" ]]; then
  if [[ ${#suites[@]} -ne 1 ]]; then
    echo "profile may not be combined with explicit suites" >&2
    exit 64
  fi
  mapfile -t suites < <(jq -er '.test_suites | select(type == "array") | .[]' "$target_manifest")
  if [[ ${#suites[@]} -eq 0 ]]; then
    echo "profile has no checked-in test suites: $profile" >&2
    exit 65
  fi
fi

for suite in "${suites[@]}"; do
  case "$suite" in
    src/moo_conformance/_tests|src/moo_conformance/_tests/*) ;;
    *) echo "invalid conformance suite path: $suite" >&2; exit 64 ;;
  esac
  if [[ "$suite" == *".."* || ! -e "$conformance_repo/$suite" ]]; then
    echo "conformance suite path not found: $suite" >&2
    exit 66
  fi
done

fixture="$(jq -er '.fixture_path | select(type == "string" and length > 0)' "$target_manifest")"
if [[ ! -f "$fixture" ]]; then
  echo "profile fixture not found: $fixture" >&2
  exit 66
fi

server_command="$launcher {db} {port} $launcher_manifest"
command=(
  uv run moo-conformance
  "${suites[@]}"
  --server-command "$server_command"
  --server-db "$fixture"
  --server-db-dir src/moo_conformance/_db/startup
  --oracle-profile-manifest "$oracle_manifest"
  --target-profile-manifest "$target_manifest"
  --fail-on-unexpected-skip
)

mapfile -t login_commands < <(jq -er '.login_commands | select(type == "array") | .[]' "$target_manifest")
if [[ ${#login_commands[@]} -gt 0 ]]; then
  printf -v BANTENG_MOO_LOGIN_COMMANDS '%s\n' "${login_commands[@]}"
  export BANTENG_MOO_LOGIN_COMMANDS
  command+=(--moo-login-script-env BANTENG_MOO_LOGIN_COMMANDS)
fi

skip_standard_properties="$(
  jq -r '
    if (.skip_standard_properties | type) == "boolean"
    then .skip_standard_properties
    else error("skip_standard_properties must be boolean")
    end
  ' "$target_manifest"
)"
if [[ "$skip_standard_properties" == "true" ]]; then
  command+=(--moo-skip-standard-properties)
fi

cd "$conformance_repo"
export UV_PROJECT_ENVIRONMENT=/root/.venvs/moo-conformance
exec "${command[@]}"
