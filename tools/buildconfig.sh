#!/bin/bash
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# NOTE: macOS ships bash 3.2 (no associative arrays), so this script avoids
# declare -A / overrides[...] entirely. Flags are plain variables.

show_help() {
  echo "Usage: buildconfig.sh [options]"
  echo "Generates a BuildConfig.java file with launcher build-time flags."
  echo
  echo "Options:"
  echo "  --pkg <package_name>      Set the package name. Defaults to com.android.launcher3."
  echo "  --appId <app_id>          Set the APPLICATION_ID string. Defaults to the package name."
  echo "  -e, --enable <name>       Enable a boolean flag."
  echo "  -d, --disable <name>      Disable a boolean flag."
  echo "  -h, --help                Show this help message."
}

pkg="com.android.launcher3"
appId=""
IS_STUDIO_BUILD=false
QSB_ON_FIRST_SCREEN=true
IS_DEBUG_DEVICE=false
WIDGETS_ENABLED=true
NOTIFICATION_DOTS_ENABLED=true

set_flag() {  # $1 = value(true|false), $2 = flag name
  case "$2" in
    IS_STUDIO_BUILD|QSB_ON_FIRST_SCREEN|IS_DEBUG_DEVICE|WIDGETS_ENABLED|NOTIFICATION_DOTS_ENABLED)
      eval "$2=$1" ;;
    *) echo "warning: unknown flag '$2' ignored" >&2 ;;
  esac
}

while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    -h|--help)
      show_help
      exit 0
      ;;
    --pkg)
      pkg="$2"
      shift 2
      ;;
    --appId)
      appId="$2"
      shift 2
      ;;
    -e|--enable)
      set_flag true "$2"
      shift 2
      ;;
    -d|--disable)
      set_flag false "$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

echo "
package ${pkg};

public final class BuildConfig {
    public static final String APPLICATION_ID = \"${appId:-${pkg}}\";

    public static final boolean IS_STUDIO_BUILD = ${IS_STUDIO_BUILD};
    public static final boolean QSB_ON_FIRST_SCREEN = ${QSB_ON_FIRST_SCREEN};
    public static final boolean IS_DEBUG_DEVICE = ${IS_DEBUG_DEVICE};
    public static final boolean WIDGETS_ENABLED = ${WIDGETS_ENABLED};
    public static final boolean NOTIFICATION_DOTS_ENABLED = ${NOTIFICATION_DOTS_ENABLED};
}
"
