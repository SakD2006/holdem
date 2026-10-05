#!/bin/sh
# Starts the Hold'em desktop app on macOS or Linux. Needs Java 21 or newer.
# Run it with:  sh run.sh
cd "$(dirname "$0")" || exit 1

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. Install Java 21 or newer from https://adoptium.net and try again."
  exit 1
fi

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64) platform=mac-aarch64 ;;
  Darwin-*)     platform=mac ;;
  Linux-x86_64) platform=linux ;;
  *)
    echo "This computer ($(uname -s) $(uname -m)) is not supported. Use Windows, macOS, or 64-bit Intel/AMD Linux."
    exit 1
    ;;
esac

exec java --module-path "javafx/$platform" --add-modules javafx.controls,javafx.media \
  -cp "lib/*" com.saksham.poker.client.app.HoldemApp "$@"
