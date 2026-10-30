#!/usr/bin/env bash
#
# Copies the Rust core build outputs into the Android jniLibs directory
# before the Gradle build runs. Invoked from a Gradle task or CI step.
#
# Usage:
#   ./tools/copy_native_libs.sh [--profile release|debug]
#
set -euo pipefail

PROFILE="${1:-release}"
if [[ "$PROFILE" == "--profile" ]]; then
  PROFILE="${2:-release}"
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_DIR="${ROOT_DIR}/core"
JNI_LIBS_DIR="${ROOT_DIR}/android/app/src/main/jniLibs"

TARGETS=(
  "aarch64-linux-android:arm64-v8a"
  "armv7-linux-androideabi:armeabi-v7a"
)

echo "Building PRISM native core (profile: ${PROFILE})…"
pushd "${CORE_DIR}" >/dev/null
for entry in "${TARGETS[@]}"; do
  rust_target="${entry%%:*}"
  abi="${entry##*:}"
  echo "  → ${rust_target}"
  cargo build --profile "${PROFILE}" --target "${rust_target}"
done
popd >/dev/null

echo "Copying libprism_core.so into jniLibs…"
for entry in "${TARGETS[@]}"; do
  rust_target="${entry%%:*}"
  abi="${entry##*:}"
  src="${CORE_DIR}/target/${rust_target}/${PROFILE}/libprism_core.so"
  dst="${JNI_LIBS_DIR}/${abi}/libprism_core.so"
  if [[ ! -f "${src}" ]]; then
    echo "ERROR: missing ${src}" >&2
    exit 1
  fi
  mkdir -p "$(dirname "${dst}")"
  cp -f "${src}" "${dst}"
  echo "  ${abi}: $(du -h "${dst}" | cut -f1)"
done

echo "Done."
