
package com.android.systemui.animation

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo

/** Stub matching android17 SystemUI animation API (upstream jar is stale). */
interface RemoteTransitionDelegate<T> {
    fun startAnimation(
        transition: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        finishedCallback: T?,
    )
    fun onTransitionConsumed(transition: IBinder?, aborted: Boolean) {}
}
