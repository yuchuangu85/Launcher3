# Coroutine Tracing

This library contains utilities for tracing coroutines. Coroutines cannot be traced using the
`android.os.Trace` APIs normally because suspension points will lead to malformed trace sections.
This is because each `Trace.beginSection()` call must have a matching `Trace.endSection()` call; if
a coroutine suspends before `Trace.endSection()` is called, the trace section will remain open while
other unrelated work executes on the thread.

To address this, we introduce a function `traceCoroutine("name") { ... }` that can be used for
tracing sections of coroutine code. When invoked, a trace section with the given name will start
immediately, and its name will also be written to an object in thread-local storage which is managed
by a `TraceContextElement` in the coroutine's context, making it safe, "coroutine-local" storage.
When the coroutine suspends, all trace sections will end immediately. When resumed, the coroutine
will read the names of the previous sections from coroutine-local storage, and it will begin the
sections again.

For example, the following coroutine code will be traced as follows:

```
traceCoroutine("Slice") {
  println("Start")
  delay(10)
  println("End")
}
```

```
Thread #1 |  [==== Slice ====]          [==== Slice ====]
               ^ "Start" printed          ^ "End" printed
```

If it's run on a multi-threaded dispatcher, it might appear as follows:

```
traceCoroutine("Slice") {
  println("Start")
  delay(10)
  println("End")
}
```

```
Thread #1 |  [==== Slice ====]
          |    ^ "Start" printed
----------+---------------------------------------------------------
Thread #2 |                              [==== Slice ====]
                                           ^ "End" printed
```

This library also provides wrappers for some of the coroutine functions provided in the
`kotlinx.coroutines.*` package.  For example, instead of:
`launch { traceCoroutine("my-launch") { /* block */ } }`, you can instead write:
`launchTraced("my-launch") { /* block */ }`.

It also provides a wrapper for tracing `Flow`. For example,

```
val coldFlow = flow {
  emit(1)
  emit(2)
  emit(3)
}

coldFlow.traceAs("name").collect {
  println(it)
  yield()
}
```

Would be traced as follows:

```
Thread #1 |  [=== name#collect ===]  [== name#collect ==]  [== name#collect ==]
          |       [= emit   =]           [= emit =]           [= emit =]
          |         ^ "1" printed          ^ "2" printed        ^ "3" printed
```

# Building and Running

## Host Tests

Host tests are implemented in `tracinglib-host-test`. To run the host tests:

```
atest tracinglib-host-test
```

## Feature Flag

Coroutine tracing is flagged off by default. To enable coroutine tracing on a device, flip the flag
and restart the user-space system:

```
adb shell aflags enable com.android.systemui.coroutine_tracing
adb shell setprop persist.debug.coroutine_tracing 1
adb shell am restart
```

## Extra Debug Flags

The behavior of coroutine tracing can be further fine-tuned using the following sysprops:

 - `persist.debug.coroutine_tracing.walk_stack`
 - `persist.debug.coroutine_tracing.count_continuations`
 - `persist.debug.coroutine_tracing.inherit_slices`
 - `persist.debug.coroutine_tracing.dump_init_stack`
 - `persist.debug.coroutine_tracing.dump_continuation_stack`

See [`createCoroutineTracingContext()`](core/src/coroutines/TraceContextElement.kt) for
documentation.

## Demo App

Build and install the app using Soong and adevice:

```
adevice track CoroutineTracingDemoApp
m CoroutineTracingDemoApp
adevice update
```

Then, open the app and tap an experiment to run it. The experiments run in the background. To see
the effects of what coroutine tracing is doing, you will need to capture a Perfetto trace. The
[`coroutine_tracing` flag](#feature-flag) will need to be enabled for coroutine trace sections to
work.
