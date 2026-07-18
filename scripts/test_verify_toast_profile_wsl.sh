#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
verifier="$script_dir/verify_toast_profile_wsl.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

source_dir="$tmp/toast"
fixture_source_dir="$tmp/fixture-source"
mkdir -p "$source_dir" "$fixture_source_dir"

printf '#!/usr/bin/env bash\nexit 0\n' > "$source_dir/moo"
chmod +x "$source_dir/moo"
printf '#define TEST_CONFIG 1\n' > "$source_dir/options.h"
printf 'fixture\n' > "$fixture_source_dir/Test.db"

git -C "$source_dir" init -q
git -C "$source_dir" add moo options.h
git -C "$source_dir" -c user.name=test -c user.email=test@example.invalid commit -qm identity
git -C "$fixture_source_dir" init -q
git -C "$fixture_source_dir" add Test.db
git -C "$fixture_source_dir" -c user.name=test -c user.email=test@example.invalid commit -qm fixture

source_ref="$(git -C "$source_dir" rev-parse HEAD)"
fixture_source_ref="$(git -C "$fixture_source_dir" rev-parse HEAD)"
binary_checksum="$(sha256sum "$source_dir/moo" | cut -d' ' -f1)"
config_checksum="$(sha256sum "$source_dir/options.h" | cut -d' ' -f1)"
fixture_checksum="$(sha256sum "$fixture_source_dir/Test.db" | cut -d' ' -f1)"
base_manifest="$tmp/base.json"

jq -n \
  --arg source_path "$source_dir" \
  --arg source_ref "$source_ref" \
  --arg binary_path "$source_dir/moo" \
  --arg binary_checksum "$binary_checksum" \
  --arg config_file "$source_dir/options.h" \
  --arg config_checksum "$config_checksum" \
  --arg fixture_path "$fixture_source_dir/Test.db" \
  --arg fixture_checksum "$fixture_checksum" \
  --arg fixture_source_path "$fixture_source_dir" \
  --arg fixture_source_ref "$fixture_source_ref" \
  '{
    source_path: $source_path,
    implementation_ref: $source_ref,
    binary_path: $binary_path,
    binary_checksum: $binary_checksum,
    config_file: $config_file,
    config_checksum: $config_checksum,
    fixture_path: $fixture_path,
    database_checksum: $fixture_checksum,
    fixture_source_path: $fixture_source_path,
    fixture_source_ref: $fixture_source_ref,
    support_status: "supported"
  }' > "$base_manifest"

"$verifier" "$base_manifest"

expect_failure() {
  local name="$1"
  local manifest="$2"
  if "$verifier" "$manifest" >/dev/null 2>&1; then
    echo "verifier accepted invalid $name manifest" >&2
    exit 1
  fi
  if env \
    TOAST_SOURCE_DIR="$source_dir" \
    TOAST_EXECUTABLE="$source_dir/moo" \
    TOAST_CONFIG="$source_dir/options.h" \
    TOAST_FIXTURE="$fixture_source_dir/Test.db" \
    TOAST_SUPPORT_STATUS=supported \
    "$verifier" "$manifest" >/dev/null 2>&1; then
    echo "environment variables bypassed invalid $name manifest" >&2
    exit 1
  fi
}

jq '.implementation_ref = "0000000000000000000000000000000000000000"' "$base_manifest" > "$tmp/source-head.json"
expect_failure source-head "$tmp/source-head.json"

jq '.binary_checksum = "00"' "$base_manifest" > "$tmp/binary-checksum.json"
expect_failure binary-checksum "$tmp/binary-checksum.json"

chmod -x "$source_dir/moo"
expect_failure executable-bit "$base_manifest"
chmod +x "$source_dir/moo"

jq '.config_checksum = "00"' "$base_manifest" > "$tmp/config-checksum.json"
expect_failure config-checksum "$tmp/config-checksum.json"

jq '.database_checksum = "00"' "$base_manifest" > "$tmp/fixture-checksum.json"
expect_failure fixture-checksum "$tmp/fixture-checksum.json"

jq '.support_status = "unsupported"' "$base_manifest" > "$tmp/unsupported.json"
expect_failure support-status "$tmp/unsupported.json"

jq '.fixture_source_ref = "0000000000000000000000000000000000000000"' "$base_manifest" > "$tmp/fixture-source-head.json"
expect_failure fixture-source-head "$tmp/fixture-source-head.json"
