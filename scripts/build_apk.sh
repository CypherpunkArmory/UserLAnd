#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_SDK_DIR="$ROOT_DIR/build/android-sdk"
SDK_DIR="${SDK_DIR:-$DEFAULT_SDK_DIR}"
BUILD_TASK="assembleDebug"
SKIP_SDK=false
SKIP_BUILD=false

usage() {
  cat <<'USAGE'
Usage: scripts/build_apk.sh [options]

Prepares a local Android SDK and runs the Gradle task needed to build the APK.

Options:
  --task <gradle_task>   Gradle task to run (default: assembleDebug).
  --sdk-dir <path>       Location to install or reuse the Android SDK
                         (default: build/android-sdk).
  --skip-sdk             Do not download or update the Android SDK.
  --skip-build           Skip invoking Gradle after ensuring dependencies.
  -h, --help             Show this help message and exit.

The script expects Java 11. It will try to auto-detect a Java 11 home via
$JAVA_HOME_11_X64, $JAVA_HOME, /usr/libexec/java_home (macOS), or
/usr/lib/jvm/java-11-openjdk-amd64 (Debian/Ubuntu). Set JAVA_HOME manually if
none of those are available.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --task)
      BUILD_TASK="$2"
      shift 2
      ;;
    --sdk-dir)
      SDK_DIR="$2"
      shift 2
      ;;
    --skip-sdk)
      SKIP_SDK=true
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

find_java_home() {
  local candidate
  for candidate in "${JAVA_HOME_11_X64:-}" "${JAVA_HOME:-}" "$(/usr/libexec/java_home -v 11 2>/dev/null || true)" \
    "/usr/lib/jvm/java-11-openjdk-amd64"; do
    if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
      "$candidate/bin/java" -version >/tmp/java_version.log 2>&1 || true
      if grep -q 'version "11' /tmp/java_version.log; then
        echo "$candidate"
        return 0
      fi
    fi
  done
  return 1
}

JAVA_HOME_VALUE=$(find_java_home || true)
if [[ -z "$JAVA_HOME_VALUE" ]]; then
  echo "Java 11 was not found. Install it and set JAVA_HOME (or JAVA_HOME_11_X64)." >&2
  exit 1
fi
export JAVA_HOME="$JAVA_HOME_VALUE"
export PATH="$JAVA_HOME/bin:$PATH"
echo "Using JAVA_HOME=$JAVA_HOME"

if [[ "$SKIP_SDK" == false ]]; then
  mkdir -p "$SDK_DIR"
  TOOLS_DIR="$SDK_DIR/cmdline-tools/latest"
  if [[ ! -x "$TOOLS_DIR/bin/sdkmanager" ]]; then
    echo "Downloading Android command line tools into $SDK_DIR ..."
    tmpdir=$(mktemp -d)
    pushd "$tmpdir" >/dev/null
    curl -LO https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
    unzip -q commandlinetools-linux-9477386_latest.zip
    mkdir -p "$SDK_DIR/cmdline-tools"
    mv cmdline-tools "$TOOLS_DIR"
    popd >/dev/null
    rm -rf "$tmpdir"
  fi

  export ANDROID_SDK_ROOT="$SDK_DIR"
  yes | "$TOOLS_DIR/bin/sdkmanager" --licenses >/dev/null
  "$TOOLS_DIR/bin/sdkmanager" --install "platform-tools" "platforms;android-30" "build-tools;30.0.3"

  cat > "$ROOT_DIR/local.properties" <<EOF_LOCAL
sdk.dir=$SDK_DIR
EOF_LOCAL
  echo "Wrote local.properties pointing at $SDK_DIR"
else
  echo "Skipping SDK install/update as requested."
fi

if [[ "$SKIP_BUILD" == true ]]; then
  echo "Skipping Gradle build (--skip-build)."
  exit 0
fi

"$ROOT_DIR/gradlew" "$BUILD_TASK"
