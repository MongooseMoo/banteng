#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

repo="$tmp/banteng"
mkdir -p "$repo/scripts" "$repo/build/install/banteng/lib" "$tmp/java/moo/app" "$tmp/classes"
cp "$script_dir/run_banteng_wsl.sh" "$script_dir/run_toast_wsl.sh" "$repo/scripts/"

cat > "$tmp/java/moo/app/Banteng.java" <<'JAVA'
package moo.app;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Banteng {
  private Banteng() {}

  public static void main(String[] args) throws Exception {
    Files.writeString(Path.of(System.getenv("ARG_CAPTURE")), String.join("\n", args));
  }
}
JAVA

/opt/java/25/bin/javac -d "$tmp/classes" "$tmp/java/moo/app/Banteng.java"
/opt/java/25/bin/jar --create --file "$repo/build/install/banteng/lib/capture.jar" -C "$tmp/classes" .
jq -n '{features: {"option.PROMOTE_NUMBERS": true}}' > "$tmp/promote.json"
jq -n '{features: {"option.PROMOTE_NUMBERS": false}}' > "$tmp/no-promote.json"

assert_rejected() {
  if "$repo/scripts/run_banteng_wsl.sh" "$@" >/dev/null 2>&1; then
    echo "run_banteng_wsl.sh accepted invalid arguments: $*" >&2
    exit 1
  fi
}

assert_rejected
assert_rejected database 7777
assert_rejected database 7777 "$tmp/promote.json" extra
assert_rejected database 7777 "$tmp/missing.json"

ARG_CAPTURE="$tmp/promote.args" \
PROMOTE_NUMBERS=false \
BANTENG_JAVA_HOME=/nonexistent \
  "$repo/scripts/run_banteng_wsl.sh" "$tmp/world.db" 7777 "$tmp/promote.json"
grep -Fx -- "--database" "$tmp/promote.args"
grep -Fx -- "$tmp/world.db" "$tmp/promote.args"
grep -Fx -- "--checkpoint" "$tmp/promote.args"
grep -Fx -- "$tmp/world.db.new" "$tmp/promote.args"
grep -Fx -- "--port" "$tmp/promote.args"
grep -Fx -- "7777" "$tmp/promote.args"
grep -Fx -- "--promote-numbers=true" "$tmp/promote.args"

ARG_CAPTURE="$tmp/no-promote.args" \
PROMOTE_NUMBERS=true \
  "$repo/scripts/run_banteng_wsl.sh" "$tmp/world.db" 8888 "$tmp/no-promote.json"
grep -Fx -- "--promote-numbers=false" "$tmp/no-promote.args"

cat > "$repo/scripts/verify_toast_profile_wsl.sh" <<'VERIFY'
#!/usr/bin/env bash
set -euo pipefail
printf 'preflight\n' >> "$TRACE"
if [[ "${FAIL_PREFLIGHT:-0}" == "1" ]]; then
  exit 1
fi
VERIFY
chmod +x "$repo/scripts/verify_toast_profile_wsl.sh"

cat > "$tmp/toast" <<'TOAST'
#!/usr/bin/env bash
set -euo pipefail
printf 'launch\n' >> "$TRACE"
printf '%s\n' "$@" > "$TOAST_ARG_CAPTURE"
TOAST
chmod +x "$tmp/toast"
jq -n --arg binary "$tmp/toast" '{binary_path: $binary}' > "$tmp/toast.json"

TRACE="$tmp/toast.trace" TOAST_ARG_CAPTURE="$tmp/toast.args" \
  "$repo/scripts/run_toast_wsl.sh" "$tmp/toast-world.db" 9999 "$tmp/toast.json"
test "$(sed -n '1p' "$tmp/toast.trace")" = preflight
test "$(sed -n '2p' "$tmp/toast.trace")" = launch
grep -Fx -- "$tmp/toast-world.db" "$tmp/toast.args" >/dev/null
grep -Fx -- "$tmp/toast-world.db.new" "$tmp/toast.args" >/dev/null
grep -Fx -- "9999" "$tmp/toast.args" >/dev/null

: > "$tmp/toast.trace"
if TRACE="$tmp/toast.trace" TOAST_ARG_CAPTURE="$tmp/toast.args" FAIL_PREFLIGHT=1 \
  "$repo/scripts/run_toast_wsl.sh" "$tmp/toast-world.db" 9999 "$tmp/toast.json"; then
  echo "run_toast_wsl.sh launched after a failed preflight" >&2
  exit 1
fi
test "$(cat "$tmp/toast.trace")" = preflight

cp "$script_dir/run_managed_wsl.sh" "$repo/scripts/"
mkdir -p "$repo/profiles/toast" "$repo/profiles/banteng" "$repo/fixtures"
printf 'stock\n' > "$repo/fixtures/stock.db"
printf 'mongoose\n' > "$repo/fixtures/mongoose.db"
printf 'toastcore\n' > "$repo/fixtures/toastcore.db"

write_profile() {
  local path="$1"
  local fixture="$2"
  local suites="$3"
  local login="$4"
  local skip_properties="$5"
  local promote_numbers="$6"
  jq -n \
    --arg fixture "$fixture" \
    --argjson suites "$suites" \
    --argjson login "$login" \
    --argjson skip_properties "$skip_properties" \
    --argjson promote_numbers "$promote_numbers" \
    '{
      fixture_path: $fixture,
      test_suites: $suites,
      login_commands: $login,
      skip_standard_properties: $skip_properties,
      features: {"option.PROMOTE_NUMBERS": $promote_numbers}
    }' > "$path"
}

stock_suite='src/moo_conformance/_tests/selected/stock.yaml'
toastcore_suite='src/moo_conformance/_tests/selected/toastcore.yaml'
write_profile "$repo/profiles/toast/stock-wsl-testdb.json" \
  "$repo/fixtures/stock.db" "[\"$stock_suite\"]" '[]' false false
write_profile "$repo/profiles/toast/mongoose-wsl-mongoose.json" \
  "$repo/fixtures/mongoose.db" "[\"$stock_suite\"]" '[]' false true
write_profile "$repo/profiles/toast/stock-wsl-toastcore.json" \
  "$repo/fixtures/toastcore.db" '[]' '["connect wizard"]' true false
write_profile "$repo/profiles/banteng/stock.json" \
  "$repo/fixtures/stock.db" "[\"$stock_suite\"]" '[]' false false
write_profile "$repo/profiles/banteng/mongoose.json" \
  "$repo/fixtures/mongoose.db" "[\"$stock_suite\"]" '[]' false true
write_profile "$repo/profiles/banteng/toastcore.json" \
  "$repo/fixtures/toastcore.db" '[]' '["connect wizard"]' true false

conformance="$tmp/moo-conformance-tests"
mkdir -p "$conformance/src/moo_conformance/_tests/selected" \
  "$conformance/src/moo_conformance/_db/startup" "$conformance/tests"
printf 'name: stock\ntests: []\n' > "$conformance/$stock_suite"
printf 'name: toastcore\ntests: []\n' > "$conformance/$toastcore_suite"
printf 'tracked\n' > "$conformance/tests/tracked.txt"

git -C "$repo" init -q
git -C "$repo" add scripts profiles fixtures
git -C "$repo" -c user.name=test -c user.email=test@example.invalid commit -qm authorities
git -C "$conformance" init -q
git -C "$conformance" add src tests
git -C "$conformance" -c user.name=test -c user.email=test@example.invalid commit -qm suites

mkdir -p "$tmp/bin"
cat > "$tmp/bin/uv" <<'UV'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$UV_CAPTURE"
printf '%s' "${BANTENG_MOO_LOGIN_COMMANDS:-}" > "$LOGIN_CAPTURE"
UV
chmod +x "$tmp/bin/uv"

managed="$repo/scripts/run_managed_wsl.sh"
PATH="$tmp/bin:$PATH" UV_CAPTURE="$tmp/stock.uv" LOGIN_CAPTURE="$tmp/stock.login" \
  "$managed" toast stock profile
grep -Fx -- "$stock_suite" "$tmp/stock.uv" >/dev/null
grep -Fx -- "--fail-on-unexpected-skip" "$tmp/stock.uv" >/dev/null
grep -Fx -- "$repo/scripts/run_toast_wsl.sh {db} {port} $repo/profiles/toast/stock-wsl-testdb.json" \
  "$tmp/stock.uv" >/dev/null
if grep -Fx -- "--moo-login-script-env" "$tmp/stock.uv" >/dev/null; then
  echo "stock unexpectedly supplied login commands" >&2
  exit 1
fi
if grep -Fx -- "--moo-skip-standard-properties" "$tmp/stock.uv" >/dev/null; then
  echo "stock unexpectedly skipped standard properties" >&2
  exit 1
fi
test ! -s "$tmp/stock.login"

PATH="$tmp/bin:$PATH" UV_CAPTURE="$tmp/toastcore.uv" LOGIN_CAPTURE="$tmp/toastcore.login" \
  "$managed" banteng toastcore "$toastcore_suite"
grep -Fx -- "$toastcore_suite" "$tmp/toastcore.uv" >/dev/null
grep -Fx -- "--moo-login-script-env" "$tmp/toastcore.uv" >/dev/null
grep -Fx -- "BANTENG_MOO_LOGIN_COMMANDS" "$tmp/toastcore.uv" >/dev/null
grep -Fx -- "--moo-skip-standard-properties" "$tmp/toastcore.uv" >/dev/null
grep -Fx -- "$repo/scripts/run_banteng_wsl.sh {db} {port} $repo/profiles/banteng/toastcore.json" \
  "$tmp/toastcore.uv" >/dev/null
test "$(cat "$tmp/toastcore.login")" = "connect wizard"

managed_rejected() {
  if PATH="$tmp/bin:$PATH" UV_CAPTURE="$tmp/rejected.uv" LOGIN_CAPTURE="$tmp/rejected.login" \
    "$managed" "$@" >/dev/null 2>&1; then
    echo "run_managed_wsl.sh accepted invalid invocation: $*" >&2
    exit 1
  fi
}

managed_rejected toast stock
managed_rejected other stock profile
managed_rejected toast other profile
managed_rejected toast stock profile "$stock_suite"
managed_rejected toast toastcore profile
managed_rejected toast stock outside.yaml
managed_rejected toast stock src/moo_conformance/_tests/missing.yaml
