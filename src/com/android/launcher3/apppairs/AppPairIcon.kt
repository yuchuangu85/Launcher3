/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.apppairs

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.util.FloatProperty
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.UtilitiesKt.drawWorkspaceItemSelectionHighlight
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.IconViewController
import com.android.launcher3.popup.Poppable
import com.android.launcher3.popup.PoppableType
import com.android.launcher3.touch.CustomActionsListener
import com.android.launcher3.touch.CustomEventsTouchHandler
import com.android.launcher3.touch.CustomTouchDelegate
import com.android.launcher3.touch.WorkspaceItemCustomActionsListener
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.views.ActivityContext
import java.util.function.Predicate
import kotlin.math.ceil

/**
 * A [android.widget.FrameLayout] used to represent an app pair icon on the workspace. <br></br> The
 * app pair icon is two parallel background rectangles with rounded corners. Icons of the two member
 * apps are set into these rectangles.
 */
class AppPairIcon :
    FrameLayout, DraggableView, Reorderable, Poppable, IconViewController, CustomTouchDelegate {
    // A view that holds the app pair icon graphic.
    lateinit var iconDrawableArea: AppPairIconGraphic
        private set

    // A view that holds the app pair's title.
    lateinit var titleTextView: BubbleTextView
        private set

    // The underlying ItemInfo that stores info about the app pair members, etc.
    lateinit var info: AppPairInfo
        private set

    // The containing element that holds this icon: workspace, taskbar, folder, etc. Affects certain
    // aspects of how the icon is drawn.
    var container: Int = 0
        private set

    // TODO(b/465247812): Remove this and overridden functions in favor of Kotlin interface
    //  delegation, upon file conversion to Kotlin.
    private val mCustomEventsTouchHandler =
        CustomEventsTouchHandler(
            view = this,
            defaultTouchHandler = { motionEvent: MotionEvent? -> super.onTouchEvent(motionEvent) },
            shouldIgnoreTouchDown = { event: MotionEvent? -> false },
        )

    // Required for Reorderable -- handles translation and bouncing movements
    private val mTranslateDelegate = MultiTranslateDelegate(this)
    private var mScaleForReorderBounce = 1f

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onFinishInflate() {
        super.onFinishInflate()
        iconDrawableArea = findViewById(R.id.app_pair_icon_graphic)
        titleTextView = findViewById(R.id.app_pair_icon_name)
    }

    /** Updates icon based on new info. */
    fun updateInfo(info: AppPairInfo) {
        this.info = info
        iconDrawableArea.init(this, container)
        updateTitleAndA11yTitle()
    }

    /**
     * Updates the title and a11y title of the app pair. Called on creation and when packages
     * change, to reflect app name changes or user language changes.
     */
    fun updateTitleAndA11yTitle() {
        updateTitleAndTextView()
        updateAccessibilityTitle()
    }

    /** Updates AppPairInfo with a formatted app pair title, and sets it on the BubbleTextView. */
    fun updateTitleAndTextView() {
        val newTitle = info.generateTitle(context)
        titleTextView.applyLabel(newTitle)
    }

    /** Updates the accessibility title with a formatted string template. */
    fun updateAccessibilityTitle() {
        val app1 = info.getFirstApp().title
        val app2 = info.getSecondApp().title
        val a11yTitle = context.getString(R.string.app_pair_name_format, app1, app2)
        contentDescription =
            if (info.shouldReportDisabled(context)) {
                context.getString(R.string.disabled_app_label, a11yTitle)
            } else {
                a11yTitle
            }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return onDelegateTouchEvent(event)
    }

    // Required for DraggableView
    override fun getViewType(): Int {
        return DraggableView.DRAGGABLE_ICON
    }

    // Required for DraggableView
    override fun getWorkspaceVisualDragBounds(outBounds: Rect) {
        iconDrawableArea.getIconBounds(outBounds)
    }

    override fun setIconVisible(visible: Boolean) {
        if (visible) {
            iconDrawableArea.visibility = VISIBLE
        } else {
            iconDrawableArea.visibility = INVISIBLE
        }
    }

    override fun getIconHeight(): Int {
        return iconDrawableArea.drawable.bounds.height()
    }

    // Required for Reorderable
    override fun getTranslateDelegate(): MultiTranslateDelegate {
        return mTranslateDelegate
    }

    // Required for Reorderable
    override fun setReorderBounceScale(scale: Float) {
        mScaleForReorderBounce = scale
        super.setScaleX(scale)
        super.setScaleY(scale)
    }

    // Required for Reorderable
    override fun getReorderBounceScale(): Float {
        return mScaleForReorderBounce
    }

    /** Ensures that both app icons in the pair are loaded in high resolution. */
    fun verifyHighRes() {
        val iconCache = getInstance(context).iconCache
        info.fetchHiResIconsIfNeeded(iconCache)
    }

    /** Called when WorkspaceItemInfos get updated, and the app pair icon may need to be redrawn. */
    fun maybeRedrawForWorkspaceUpdate(itemCheck: Predicate<ItemInfo>) {
        // If either of the app pair icons return true on the predicate (i.e. in the list of
        // updated apps), redraw the icon graphic (icon background and both icons).
        if (info.anyMatch(itemCheck)) {
            updateTitleAndA11yTitle()
            iconDrawableArea.redraw()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Draw selection highlight before super.dispatchDraw() so that it appears behind the title
        // text.
        if (isSelected) {
            drawWorkspaceItemSelectionHighlight(canvas, this)
        }
        super.dispatchDraw(canvas)
    }

    /**
     * Inside folders, icons are vertically centered in their rows. See [BubbleTextView] for
     * comparison.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (container == BubbleTextView.DISPLAY_FOLDER) {
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val activity: ActivityContext = ActivityContext.lookupContext(context)
            val fm = titleTextView.paint.fontMetrics
            val cellHeightPx =
                (activity.deviceProfile.folderProfile.childIconSizePx +
                    activity.deviceProfile.folderProfile.childDrawablePaddingPx +
                    ceil((fm.bottom - fm.top).toDouble()).toInt())
            setPadding(paddingLeft, (height - cellHeightPx) / 2, paddingRight, paddingBottom)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onHoverChanged(hovered: Boolean) {
        super.onHoverChanged(hovered)
        ObjectAnimator.ofFloat(
                this,
                HOVER_SCALE_PROPERTY,
                if (hovered) HOVER_SCALE_MAX else HOVER_SCALE_DEFAULT,
            )
            .setDuration(HOVER_SCALE_DURATION.toLong())
            .start()
    }

    override fun getPoppableType(): PoppableType {
        return PoppableType.APP_PAIR
    }

    // No-op since we don't show notification dot for app pairs.
    override fun setForceHideDot(forceHideDot: Boolean) {}

    override fun getFloatingViewTextAlpha(): MultiPropertyFactory<AnimatedFloat>.MultiProperty? {
        return titleTextView.getFloatingViewTextAlpha()
    }

    override fun onDelegateTouchEvent(event: MotionEvent): Boolean {
        return mCustomEventsTouchHandler.onDelegateTouchEvent(event)
    }

    override var customActionsListener: CustomActionsListener?
        get() = mCustomEventsTouchHandler.customActionsListener
        set(customActionsListener) {
            mCustomEventsTouchHandler.customActionsListener = customActionsListener
        }

    companion object {
        private const val TAG = "AppPairIcon"

        // The duration of the scaling animation on hover enter/exit.
        private const val HOVER_SCALE_DURATION = 150

        // The default scale of the icon when not hovered.
        private const val HOVER_SCALE_DEFAULT = 1f

        // The max scale of the icon when hovered.
        private const val HOVER_SCALE_MAX = 1.1f

        // Animates the scale of the icon background on hover.
        private val HOVER_SCALE_PROPERTY: FloatProperty<AppPairIcon> =
            object : FloatProperty<AppPairIcon>("hoverScale") {
                override fun setValue(view: AppPairIcon, scale: Float) {
                    view.iconDrawableArea.setHoverScale(scale)
                }

                override fun get(view: AppPairIcon): Float {
                    return view.iconDrawableArea.getHoverScale()
                }
            }

        /** Builds an AppPairIcon to be added to the Launcher. */
        @JvmStatic
        fun inflateIcon(
            resId: Int,
            activity: ActivityContext,
            group: ViewGroup?,
            appPairInfo: AppPairInfo,
            container: Int,
        ): AppPairIcon {
            val grid = activity.deviceProfile
            val inflater =
                if (group != null) {
                    LayoutInflater.from(group.context)
                } else {
                    activity.layoutInflater
                }
            val icon = inflater.inflate(resId, group, false) as AppPairIcon

            if (Flags.enableFocusOutline() && activity is Launcher) {
                icon.onFocusChangeListener = activity.focusHandler
                icon.defaultFocusHighlightEnabled = false
            }

            // Sort contents, so that left-hand app comes first
            (appPairInfo.getAppContents() as MutableList).sortBy { it.rank }

            icon.tag = appPairInfo
            icon.setOnClickListener(activity.itemOnClickListener)
            icon.customActionsListener = WorkspaceItemCustomActionsListener
            icon.info = appPairInfo
            icon.container = container

            // Set up icon drawable area
            icon.iconDrawableArea.init(icon, container)

            // Set up app pair title
            val lp = icon.titleTextView.getLayoutParams() as LayoutParams
            // Shift the title text down to leave room for the icon graphic. Since the icon graphic
            // is a separate element (and not set as a CompoundDrawable on the BubbleTextView), we
            // need to shift the text down manually.
            lp.topMargin =
                if (container == BubbleTextView.DISPLAY_FOLDER) {
                    (grid.folderProfile.childIconSizePx + grid.folderProfile.childDrawablePaddingPx)
                } else {
                    (grid.workspaceProfile.iconSizePx + grid.workspaceProfile.iconDrawablePaddingPx)
                }
            // For some reason, app icons have setIncludeFontPadding(false) inside folders, so we
            // set it here to match that.
            icon.titleTextView.setIncludeFontPadding(container != BubbleTextView.DISPLAY_FOLDER)
            // Set title text and accessibility title text.
            icon.updateTitleAndA11yTitle()

            icon.setAccessibilityDelegate(activity.accessibilityDelegate)

            return icon
        }
    }
}
