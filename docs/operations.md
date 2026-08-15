# Banteng operations

Launch:

```bash
JAVA_HOME=/opt/java/25 build/install/banteng/bin/banteng --database "${BANTENG_DATABASE}" --checkpoint "${BANTENG_CHECKPOINT}" --listen-address 127.0.0.1 --port "${BANTENG_PORT}"
```

Checkpoint:

```bash
exec 3<>"/dev/tcp/127.0.0.1/${BANTENG_PORT}"; printf 'connect Wizard password\r\n; return dump_database();\r\n' >&3; sleep 1; exec 3>&-
```

JFR:

```bash
JAVA_HOME=/opt/java/25 JAVA_TOOL_OPTIONS="-XX:StartFlightRecording=settings=src/main/resources/jfr/banteng-production.jfc,filename=${BANTENG_JFR},dumponexit=true" build/install/banteng/bin/banteng --database "${BANTENG_DATABASE}" --checkpoint "${BANTENG_CHECKPOINT}" --listen-address 127.0.0.1 --port "${BANTENG_PORT}"
```

Recovery:

```bash
JAVA_HOME=/opt/java/25 build/install/banteng/bin/banteng --database "${BANTENG_RECOVERY_DATABASE}" --checkpoint "${BANTENG_RECOVERY_CHECKPOINT}" --listen-address 127.0.0.1 --port "${BANTENG_PORT}"
```
