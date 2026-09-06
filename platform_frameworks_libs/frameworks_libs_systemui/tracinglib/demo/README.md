Install and open app, start tracing, then click and experiment.

To trace this app, run:

```
$ANDROID_BUILD_TOP/external/perfetto/tools/record_android_trace  \
  -c $ANDROID_BUILD_TOP/frameworks/libs/systemui/tracinglib/demo/assets/coroutine_demo_trace_config.textproto
```
