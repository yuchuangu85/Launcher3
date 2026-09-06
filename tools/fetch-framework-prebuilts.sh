#!/usr/bin/env bash
# Re-fetch framework prebuilt jars from the OrbStack AOSP VM (Yuchuan) into prebuilts/framework/.
#
# Source: ~/aosp/out/soong/.intermediates of the android17-release build inside the VM.
# Jar set (12) recovered from .gradle/9.7.1/executionHistory/executionHistory.bin (Sep 2026):
# framework-android17.jar + SystemUI.jar + the platform module jars the root build.gradle
# auto-adds via addFrameworkJar / addPlatformJarsFor / addSystemUIJar.
#
# Usage:  tools/fetch-framework-prebuilts.sh   (run from repo root on macOS host)
# Verifies byte-identity via sha256 against the VM sources; exits non-zero on mismatch.
set -euo pipefail

VM=Yuchuan
INT=~/aosp/out/soong/.intermediates
DEST="$(cd "$(dirname "$0")/.." && pwd)/prebuilts/framework"

# <vm source path under $INT> <dest file name>
JARS=(
  "frameworks/base/framework/android_common/turbine-combined/framework.jar framework-android17.jar"
  "frameworks/base/packages/SystemUI/SystemUI/android_common/withres/SystemUI.jar SystemUI.jar"
  "frameworks/base/packages/SystemUI/shared/SystemUISharedLib/android_common/javac/SystemUISharedLib.jar SystemUISharedLib.jar"
  "frameworks/base/packages/SystemUI/shared/SystemUI-statsd/android_common/javac/SystemUI-statsd.jar SystemUI-statsd.jar"
  "packages/modules/StatsD/framework/framework-statsd.stubs.module_lib/android_common/turbine-combined/framework-statsd.stubs.module_lib.jar framework-statsd.stubs.module_lib.jar"
  "frameworks/libs/systemui/viewcapturelib/view_capture/android_common/javac/view_capture.jar view_capture.jar"
  "packages/apps/Launcher3/aconfig/com_android_launcher3_flags_lib/android_common/javac/com_android_launcher3_flags_lib.jar com_android_launcher3_flags_lib.jar"
  "frameworks/libs/systemui/aconfig/com_android_systemui_shared_flags_lib/android_common/javac/com_android_systemui_shared_flags_lib.jar com_android_systemui_shared_flags_lib.jar"
  "frameworks/base/libs/WindowManager/Shell/aconfig/com_android_wm_shell_flags_lib/android_common/javac/com_android_wm_shell_flags_lib.jar com_android_wm_shell_flags_lib.jar"
  "frameworks/base/libs/WindowManager/Shell/WindowManager-Shell/android_common/javac/WindowManager-Shell.jar WindowManager-Shell.jar"
  "frameworks/base/libs/WindowManager/Shell/shared/WindowManager-Shell-shared/android_common/javac/WindowManager-Shell-shared.jar WindowManager-Shell-shared.jar"
  "frameworks/base/libs/WindowManager/Shell/shared/WindowManager-Shell-shared-AOSP/android_common/javac/WindowManager-Shell-shared-AOSP.jar WindowManager-Shell-shared-AOSP.jar"
)

mkdir -p "$DEST"
# VM-side hashes first (one ssh round-trip)
remote_hashes="$(
  orb -m "$VM" bash -lc "cd $INT && sha256sum $(printf '%q ' "${JARS[@]%% *}")" < /dev/null
)"

fail=0
for entry in "${JARS[@]}"; do
  src="${entry%% *}"; dst="${entry##* }"
  rhash=$(printf '%s\n' "$remote_hashes" | awk -v s="$src" '$2==s {print $1}')
  if [ -z "$rhash" ]; then echo "SKIP (no VM hash for $src)"; fail=1; continue; fi
  # NOTE: stdin must be /dev/null — orb's ssh would otherwise swallow this loop's stdin
  orb -m "$VM" bash -lc "cat $INT/$src" < /dev/null > "$DEST/$dst"
  lhash=$(shasum -a 256 "$DEST/$dst" | awk '{print $1}')
  if [ "$lhash" = "$rhash" ]; then
    echo "OK   $dst"
  else
    echo "FAIL $dst (vm=$rhash local=$lhash)"; fail=1
  fi
done
echo "---"
[ "$fail" = 0 ] && echo "All ${#JARS[@]} jars restored and verified in $DEST" || { echo "Errors above"; exit 1; }
