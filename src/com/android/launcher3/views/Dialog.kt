/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.views

import android.app.Dialog
import android.graphics.Outline
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.view.Window.FEATURE_NO_TITLE
import android.view.WindowManager.LayoutParams
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.R
import com.android.launcher3.util.Themes

/** Type alias for a simple dialog. */
typealias SimpleDialog = com.android.launcher3.views.Dialog<SimpleDialogViewModel>

/**
 * Dialog which, if the [android.Manifest.permission.SYSTEM_ALERT_WINDOW] permission is held, is
 * rendered above all application windows.
 */
@Suppress("IllegalUseOfCustomDialog")
class Dialog<T : DialogViewModel<T>>(
    val activityContext: ActivityContext,
    @get:VisibleForTesting val viewModel: T,
) : DialogScope {
    @VisibleForTesting var dialog: Dialog? = null
    @VisibleForTesting var content: ComposeView? = null
    private var listener: ListenerView? = null

    /** Returns whether the dialog is currently showing. */
    fun isShowing() = dialog?.isShowing == true

    // TODO(b/489770998): Implement animation.
    /** Dismisses the dialog with an optional animation. */
    override fun dismiss(animate: Boolean) {
        dialog?.dismiss()
    }

    // TODO(b/489770998): Implement animation.
    /** Shows the dialog with an animation. */
    fun show() {
        if (dialog != null) {
            return
        }

        val context = activityContext.asContext()

        val dialog =
            object : Dialog(context) {
                    override fun onStart() {
                        super.onStart()
                        window?.setWindowAnimations(0)
                    }

                    override fun onWindowFocusChanged(hasFocus: Boolean) {
                        super.onWindowFocusChanged(hasFocus)
                        if (!hasFocus) {
                            dismiss(animate = true)
                        }
                    }
                }
                .also(::dialog::set)

        with(dialog.window!!) {
            requestFeature(FEATURE_NO_TITLE)
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            with(decorView) {
                clipToOutline = true
                elevation = context.resources.getDimension(R.dimen.dialog_elevation)
                outlineProvider =
                    object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            val cornerRadius = Themes.getDialogCornerRadius(context)
                            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                        }
                    }

                setViewTreeLifecycleOwner(activityContext)
                setViewTreeSavedStateRegistryOwner(activityContext)
            }

            with(attributes) {
                flags = flags and LayoutParams.FLAG_DIM_BEHIND.inv()
                if (Settings.canDrawOverlays(context)) {
                    type = LayoutParams.TYPE_APPLICATION_OVERLAY
                }
            }
        }

        content =
            ComposeView(context)
                .apply {
                    // NOTE: Using the default view composition strategy results in immediate
                    // disposal during window detachment, but that sometimes results in a crash when
                    // [dismiss()] is called from a composable's event loop. To handle that case,
                    // bind the view's composition to the [activityContext] lifecycle and then
                    // dispose of it explicitly from [onDismiss()] to prevent memory leaks.
                    setViewCompositionStrategy(DisposeOnViewTreeLifecycleDestroyed)
                    setContent { DialogView(viewModel) }
                }
                .also(dialog::setContentView)

        dialog.setOnDismissListener { onDismiss() }
        dialog.setOnShowListener { onShow() }
        dialog.show()
    }

    private fun onDismiss() {
        listener = listener?.run(activityContext.dragLayer::removeView).let { null }
        content = content?.setViewCompositionStrategy(DestroyNow).let { null }
        dialog = null
    }

    private fun onShow() {
        listener =
            ListenerView(activityContext.asContext(), AbstractFloatingView.TYPE_DIALOG_LISTENER)
                .apply { setListener { dismiss(animate = true) } }
                .also(activityContext.dragLayer::addView)
    }
}

/**
 * [ViewCompositionStrategy] which immediately disposes of the view composition. Using this strategy
 * results in uninstallation of the previous strategy, thereby cleaning up any lingering memory
 * references that might otherwise leak.
 */
private object DestroyNow : ViewCompositionStrategy {
    override fun installFor(view: AbstractComposeView): () -> Unit {
        view.disposeComposition()
        return {}
    }
}
