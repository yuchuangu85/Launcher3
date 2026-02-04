# Lawnchair Platform Frameworks Library SystemUI

This repository contains multiple libraries in SystemUI used by Lawnchair.

A brief explanation of what each library does:
* `animationlib`: Handling playback and interpolator of the animations
* `contextualeducationlib`: Store "education" type
* `displaylib`: Handling presumably desktop displays
* `iconloaderlib`: Handling all of Launcher3 and Lawnchair icons
* `mechanics`: Complement the `animationlib`
* `msdllib`: Multi-Sensory-Design-Language, handling all new vibrations in Launcher3 Android 16
* `searchuilib`: Store search-related layout type
  * See [AOSP Commit][searchuilib-url] instead because it's gone private after U
* `viewcapturelib`: Capture views... yep that's really that it

[searchuilib-url]: https://cs.android.com/android/_/android/platform/frameworks/libs/systemui/+/main:searchuilib/src/com/android/app/search/;drc=ace90b2ec32d3730141387c56e8abc761c380550;bpv=1;bpt=0
