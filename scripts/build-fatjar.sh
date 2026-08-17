#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

echo "🔨 Starting PathFinder fat jar build..."
echo "=========================================="

START_TIME=$(date +%s)

java_major_version() {
    "$1" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
}

find_java_21() {
    local candidate

    if [[ -x /usr/libexec/java_home ]]; then
        candidate="$(/usr/libexec/java_home -v 21 2>/dev/null || true)/bin/java"
        if [[ -x "${candidate}" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    fi

    for candidate in /usr/lib/jvm/*21*/bin/java; do
        if [[ -x "${candidate}" ]] && [[ "$(java_major_version "${candidate}")" == "21" ]]; then
            printf '%s\n' "${candidate}"
            return 0
        fi
    done

    return 1
}

JAVA_BIN="${JAVA_HOME:-}/bin/java"
if [[ ! -x "${JAVA_BIN}" ]] || [[ "$(java_major_version "${JAVA_BIN}")" != "21" ]]; then
    echo "⚠️  Locating a Java 21 runtime for the Gradle build..."
    if JAVA_BIN="$(find_java_21)"; then
        JAVA_HOME="$(dirname "$(dirname "${JAVA_BIN}")")"
        export JAVA_HOME
        echo "✅ Using Java 21: ${JAVA_HOME}"
    else
        echo "❌ Java 21 was not found. Install JDK 21 or set JAVA_HOME to its installation directory."
        exit 1
    fi
else
    echo "✅ Using Java 21 from JAVA_HOME: ${JAVA_HOME}"
fi

echo "🔍 Checking Java version..."
"${JAVA_BIN}" -version 2>&1 | head -1

echo "🧹 Cleaning stray .class files..."
find "${PROJECT_ROOT}" -type f -name "*.class" \
    ! -path "${PROJECT_ROOT}/.gradle/*" \
    ! -path "${PROJECT_ROOT}/.gradle-build/*" \
    ! -path "${PROJECT_ROOT}/target/*" \
    -delete

echo
echo "☕ Running clean + shadowJar ..."
echo "=========================================="
./gradlew clean shadowJar

JAR_FILE="$(find "${PROJECT_ROOT}/.gradle-build/libs" -maxdepth 1 -type f -name '*-all.jar' | sort | tail -n 1)"
if [[ -z "${JAR_FILE}" ]]; then
    echo "❌ Fat jar artifact not found (expected in .gradle-build/libs)"
    exit 1
fi

echo
echo "✅ Fat jar build succeeded"
echo
echo "📊 Verifying build output..."
echo "=========================================="
echo "📦 Plugin file: ${JAR_FILE}"
ls -lh "${JAR_FILE}"

SIZE_BYTES="$(stat -c%s "${JAR_FILE}")"
CLASS_COUNT="$(jar tf "${JAR_FILE}" | grep -c '\.class$' || true)"
echo "✅ File size: $((SIZE_BYTES / 1024))KB"
echo "✅ Java class files: ${CLASS_COUNT}"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
echo "⏱️  Build duration: ${DURATION}s"

if [[ "${RELEASE_COPY:-0}" == "1" ]]; then
    RELEASE_DIR="${PROJECT_ROOT}/release"
    TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
    RELEASE_FILE="${RELEASE_DIR}/$(basename "${JAR_FILE%.jar}")-${TIMESTAMP}.jar"
    mkdir -p "${RELEASE_DIR}"
    cp "${JAR_FILE}" "${RELEASE_FILE}"
    echo "📁 Copied release artifact: ${RELEASE_FILE}"
fi

echo
echo "🎉 Build completed"
echo "=========================================="
echo "📦 Final fat jar: ${JAR_FILE}"
echo "💡 Next build command: ./scripts/build-fatjar.sh"
