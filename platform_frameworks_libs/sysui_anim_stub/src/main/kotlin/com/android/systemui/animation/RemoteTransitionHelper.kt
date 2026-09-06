
package com.android.systemui.animation

import android.os.IBinder
import android.view.SurfaceControl
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo

/** Stub matching android17 SystemUI animation API. */
interface RemoteTransitionHelper {
    fun setUpAnimation(
        token: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        finishCallback: IRemoteTransitionFinishedCallback?,
    )
    fun cleanUpAnimation(transition: IBinder?, transaction: SurfaceControl.Transaction?)
    fun mergeAnimation(info: TransitionInfo?, transaction: SurfaceControl.Transaction?, mergeTarget: IBinder?)
    fun onTransitionConsumed(transition: IBinder?)
}
