@echo off
setlocal

set "BANTENG_DIR=%~dp0"
set "TOASTCORE_DATABASE=%USERPROFILE%\src\toastcore\toastcore.db"
set "TOASTCORE_CHECKPOINT=%TOASTCORE_DATABASE%.new"

if not exist "%TOASTCORE_DATABASE%" (
  echo ToastCore database not found: "%TOASTCORE_DATABASE%" 1>&2
  exit /b 66
)

pushd "%BANTENG_DIR%" || exit /b 1
call gradlew.bat --no-daemon run --args="--database %TOASTCORE_DATABASE% --checkpoint %TOASTCORE_CHECKPOINT% --listen-address 127.0.0.1 --port 7777 --promote-numbers=false"
set "BANTENG_EXIT_CODE=%ERRORLEVEL%"
popd

exit /b %BANTENG_EXIT_CODE%
