
package com.android.systemui.animation

/** Stub matching android17 SystemUI animation API. */
class DefaultTransitionHelper private constructor() : RemoteTransitionHelper {
    override fun setUpAnimation(token: android.os.IBinder?, info: android.window.TransitionInfo?,
        transaction: android.view.SurfaceControl.Transaction?, finishCallback: android.window.IRemoteTransitionFinishedCallback?) {}
    override fun cleanUpAnimation(transition: android.os.IBinder?, transaction: android.view.SurfaceControl.Transaction?) {}
    override fun mergeAnimation(info: android.window.TransitionInfo?, transaction: android.view.SurfaceControl.Transaction?, mergeTarget: android.os.IBinder?) {}
    override fun onTransitionConsumed(transition: android.os.IBinder?) {}

    companion object {
        @JvmStatic
        operator fun invoke(): RemoteTransitionHelper = DefaultTransitionHelper()
    }
}
