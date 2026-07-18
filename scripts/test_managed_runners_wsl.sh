#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

repo="$tmp/banteng"
mkdir -p "$repo/scripts" "$repo/build/install/banteng/lib" "$tmp/java/moo/app" "$tmp/classes"
cp "$script_dir/run_banteng_wsl.sh" "$repo/scripts/"

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
