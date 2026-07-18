#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: verify_toast_profile_wsl.sh MANIFEST" >&2
  exit 64
fi

manifest="$1"
if [[ ! -f "$manifest" ]]; then
  echo "profile manifest not found: $manifest" >&2
  exit 66
fi

read_string() {
  local key="$1"
  jq -er --arg key "$key" '.[$key] | select(type == "string" and length > 0)' "$manifest"
}

verify_checksum() {
  local label="$1"
  local path="$2"
  local expected="$3"
  local actual
  if [[ ! -f "$path" ]]; then
    echo "$label not found: $path" >&2
    exit 66
  fi
  actual="$(sha256sum -- "$path" | cut -d' ' -f1)"
  if [[ "$actual" != "$expected" ]]; then
    echo "$label checksum mismatch: expected $expected, got $actual" >&2
    exit 65
  fi
}

source_path="$(read_string source_path)"
source_ref="$(read_string implementation_ref)"
binary_path="$(read_string binary_path)"
binary_checksum="$(read_string binary_checksum)"
config_path="$(read_string config_file)"
config_checksum="$(read_string config_checksum)"
fixture_path="$(read_string fixture_path)"
fixture_checksum="$(read_string database_checksum)"
support_status="$(read_string support_status)"

if [[ "$support_status" != "supported" ]]; then
  echo "profile is not supported: $support_status" >&2
  exit 65
fi

actual_source_ref="$(git -C "$source_path" rev-parse HEAD)"
if [[ "$actual_source_ref" != "$source_ref" ]]; then
  echo "Toast source HEAD mismatch: expected $source_ref, got $actual_source_ref" >&2
  exit 65
fi

if [[ ! -x "$binary_path" ]]; then
  echo "Toast executable is not executable: $binary_path" >&2
  exit 65
fi

verify_checksum "Toast executable" "$binary_path" "$binary_checksum"
verify_checksum "Toast configuration" "$config_path" "$config_checksum"
verify_checksum "database fixture" "$fixture_path" "$fixture_checksum"

fixture_source_path="$(jq -r '.fixture_source_path // empty' "$manifest")"
fixture_source_ref="$(jq -r '.fixture_source_ref // empty' "$manifest")"
if [[ -n "$fixture_source_path" || -n "$fixture_source_ref" ]]; then
  if [[ -z "$fixture_source_path" || -z "$fixture_source_ref" ]]; then
    echo "fixture_source_path and fixture_source_ref must be specified together" >&2
    exit 65
  fi
  actual_fixture_source_ref="$(git -C "$fixture_source_path" rev-parse HEAD)"
  if [[ "$actual_fixture_source_ref" != "$fixture_source_ref" ]]; then
    echo "fixture source HEAD mismatch: expected $fixture_source_ref, got $actual_fixture_source_ref" >&2
    exit 65
  fi
fi
