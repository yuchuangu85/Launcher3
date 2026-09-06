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
package com.android.quickstep.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.RectEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils.TruncateAt
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.addListener
import androidx.core.view.updateLayoutParams
import com.android.app.animation.Interpolators
import com.android.launcher3.Flags
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X
import com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y
import com.android.launcher3.LauncherAnimUtils.getScaleProperty
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.MultiPropertyDelegate
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.MultiPropertyFactory.FloatBiFunction
import com.android.launcher3.util.MultiValueAlpha
import com.android.quickstep.util.BorderAnimator
import com.android.quickstep.util.BorderAnimator.Companion.createSimpleBorderAnimator
import com.android.quickstep.util.RecentsOrientedState
import com.google.android.msdl.data.model.MSDLToken
import kotlin.math.max
import kotlin.math.min

/** An icon app menu view which can be used in place of an IconView in overview TaskViews. */
class IconAppChipView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private var iconView: IconView? = null
    private var iconArrowView: ImageView? = null
    private var menuAnchorView: View? = null

    // Two textview so we can ellipsize the collapsed view and crossfade on expand to the full name.
    private var appTitle: TextView? = null
    private var isLayoutNaturalToLauncher = true

    private val backgroundRelativeLtrLocation = Rect()
    private val backgroundAnimationRectEvaluator = RectEvaluator(backgroundRelativeLtrLocation)

    // Menu dimensions
    private val collapsedMenuDefaultWidth: Int =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_collapsed_width)
    private val expandedMenuDefaultWidth: Int =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_width)
    private val collapsedMenuDefaultHeight =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_collapsed_height)
    private val expandedMenuDefaultHeight =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_height)
    private val iconMenuMarginTopStart =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_top_start_margin)
    private val menuToChipGap: Int =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_expanded_gap)

    // Background dimensions
    val backgroundMarginTopStart: Int =
        resources.getDimensionPixelSize(
            R.dimen.task_thumbnail_icon_menu_background_margin_top_start
        )

    // Contents dimensions
    private val appNameHorizontalMarginCollapsed =
        resources.getDimensionPixelSize(
            R.dimen.task_thumbnail_icon_menu_app_name_margin_horizontal_collapsed
        )
    private val appNameHorizontalMarginExpanded =
        resources.getDimensionPixelSize(
            R.dimen.task_thumbnail_icon_menu_app_name_margin_horizontal_expanded
        )
    private val arrowMarginEnd =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_arrow_margin)
    private val iconViewMarginStart =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_view_start_margin)
    private val appIconSize =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_app_icon_collapsed_size)
    private val iconMenuElevation =
        resources.getDimension(R.dimen.task_thumbnail_icon_menu_elevation)
    private val arrowSize =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_arrow_size)
    private val iconViewDrawableExpandedSize =
        resources.getDimensionPixelSize(R.dimen.task_thumbnail_icon_menu_app_icon_expanded_size)
    private val focusBorderWidth =
        resources.getDimensionPixelSize(R.dimen.app_chip_keyboard_border_width)
    private val cornerRadius = resources.getDimensionPixelSize(R.dimen.app_chip_round_corner_radius)

    private var animator: AnimatorSet? = null

    private val multiValueAlpha: MultiValueAlpha =
        MultiValueAlpha(this, Alpha.entries.size).apply { setUpdateVisibility(true) }
    var contentAlpha by MultiPropertyDelegate(multiValueAlpha, Alpha.Content)
    var colorTintAlpha by MultiPropertyDelegate(multiValueAlpha, Alpha.ColorTint)
    var modalAlpha by MultiPropertyDelegate(multiValueAlpha, Alpha.Modal)
    var flexSplitAlpha by MultiPropertyDelegate(multiValueAlpha, Alpha.FlexSplit)
    var settledProgressAlpha by MultiPropertyDelegate(multiValueAlpha, Alpha.SettledProgress)

    private val viewTranslationX: MultiPropertyFactory<View> =
        MultiPropertyFactory(this, VIEW_TRANSLATE_X, INDEX_COUNT_TRANSLATION, SUM_AGGREGATOR)

    private val viewTranslationY: MultiPropertyFactory<View> =
        MultiPropertyFactory(this, VIEW_TRANSLATE_Y, INDEX_COUNT_TRANSLATION, SUM_AGGREGATOR)

    // Width showing only the app icon and arrow. Max width should not be set to less than
    // this.
    private val minWidthAllowed = iconViewMarginStart + appIconSize + arrowSize + arrowMarginEnd
    var maxWidth = Integer.MAX_VALUE
        /**
         * Sets the maximum width of this Icon Menu. This is usually used when space is limited for
         * split screen.
         */
        set(value) {
            // Width showing only the app icon and arrow. Max width should not be set to less than
            // this.
            field = max(value, minWidthAllowed)
        }

    var status: AppChipStatus = AppChipStatus.Collapsed
        private set

    val menuToCollapsedChipGap: Int =
        getExpandedBackgroundLtrBounds().bottom -
            getCollapsedBackgroundLtrBounds().bottom -
            menuToChipGap

    val msdlPlayerWrapper: MSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)

    private val focusBorderAnimator: BorderAnimator =
        createSimpleBorderAnimator(
            borderRadiusPx = cornerRadius,
            borderWidthPx = focusBorderWidth,
            boundsBuilder = { bounds ->
                bounds.set(backgroundRelativeLtrLocation)
                if (status == AppChipStatus.Expanded) {
                    // Draws the border inside the chip to avoid overlap with the task menu.
                    val inset = focusBorderWidth - 1
                    bounds.inset(inset, inset)
                }
            },
            targetView = this,
            borderColor =
                context
                    .obtainStyledAttributes(attrs, R.styleable.IconAppChip)
                    .getColor(
                        R.styleable.IconAppChip_focusBorderColor,
                        BorderAnimator.DEFAULT_BORDER_COLOR,
                    ),
        )

    private var focusAnimator: AnimatorSet? = null

    init {
        isHapticFeedbackEnabled = !Flags.msdlFeedback()
    }

    private fun animateFocusBorder(isAppearing: Boolean) {
        focusAnimator?.cancel()
        focusAnimator = null
        val borderAnimator = focusBorderAnimator.buildAnimator(isAppearing)

        val initialBackground = Rect(backgroundRelativeLtrLocation)
        val targetBackground: Rect =
            when {
                // Background animator to increase the clipping size to show the focus border.
                isAppearing ->
                    Rect(backgroundRelativeLtrLocation).apply {
                        if (status == AppChipStatus.Collapsed)
                            inset(-focusBorderWidth + 1, -focusBorderWidth + 1)
                    }
                // Background animator to restore the outline size to hide the focus border
                status == AppChipStatus.Expanded -> getExpandedBackgroundLtrBounds()
                else -> getCollapsedBackgroundLtrBounds()
            }
        val backgroundAnimator =
            ValueAnimator.ofObject(
                    backgroundAnimationRectEvaluator,
                    initialBackground,
                    targetBackground,
                )
                .apply { addUpdateListener { invalidateOutline() } }

        focusAnimator =
            AnimatorSet().apply {
                playTogether(borderAnimator, backgroundAnimator)
                duration = borderAnimator.duration
                interpolator = borderAnimator.interpolator
                start()
            }
    }

    public override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocusBorder(isAppearing = gainFocus)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        focusBorderAnimator.drawBorder(canvas)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        iconView = findViewById(R.id.icon_view)
        appTitle = findViewById(R.id.icon_title)
        iconArrowView = findViewById(R.id.icon_arrow)
        menuAnchorView = findViewById(R.id.icon_view_menu_anchor)
    }

    fun setText(text: CharSequence?) {
        if (text == appTitle?.text) return
        appTitle?.text = text
    }

    fun getDrawable(): Drawable? = iconView?.drawable

    private var currentIconDrawableHash: Int = 0

    fun setDrawable(icon: Drawable?) {
        if (icon.hashCode() == currentIconDrawableHash) return
        iconView?.setDrawable(icon)
        currentIconDrawableHash = icon.hashCode()
    }

    override fun getMinimumWidth(): Int = min(maxWidth, collapsedMenuDefaultWidth)

    fun setIconOrientation(orientationState: RecentsOrientedState) {
        val orientationHandler = orientationState.orientationHandler
        isLayoutNaturalToLauncher = orientationHandler.isLayoutNaturalToLauncher
        // Layout params for anchor view
        val anchorLayoutParams = menuAnchorView!!.layoutParams as LayoutParams
        if (orientationHandler.isLayoutNaturalToLauncher) {
            anchorLayoutParams.gravity = Gravity.START
            anchorLayoutParams.marginStart = backgroundMarginTopStart
        } else {
            anchorLayoutParams.gravity = Gravity.LEFT
            anchorLayoutParams.marginStart = 0
        }
        anchorLayoutParams.topMargin = expandedMenuDefaultHeight + menuToChipGap
        menuAnchorView!!.layoutParams = anchorLayoutParams

        // Layout Params for the Menu View (this)
        val iconMenuParams = layoutParams as LayoutParams
        iconMenuParams.width = getChipWidth()
        iconMenuParams.height = expandedMenuDefaultHeight
        orientationHandler.setIconAppChipMenuParams(
            this,
            iconMenuParams,
            iconMenuMarginTopStart,
            iconMenuMarginTopStart,
        )
        layoutParams = iconMenuParams

        // Layout params for the background
        val collapsedBackgroundBounds = getCollapsedBackgroundLtrBounds()
        backgroundRelativeLtrLocation.set(collapsedBackgroundBounds)
        outlineProvider =
            object : ViewOutlineProvider() {
                val mRtlAppliedOutlineBounds: Rect = Rect()

                override fun getOutline(view: View, outline: Outline) {
                    mRtlAppliedOutlineBounds.set(backgroundRelativeLtrLocation)
                    if (isLayoutRtl) {
                        val width = width
                        mRtlAppliedOutlineBounds.left = width - backgroundRelativeLtrLocation.right
                        mRtlAppliedOutlineBounds.right = width - backgroundRelativeLtrLocation.left
                    }
                    outline.setRoundRect(
                        mRtlAppliedOutlineBounds,
                        resources.getDimension(R.dimen.app_chip_round_corner_radius),
                    )
                }
            }

        // Layout Params for the Icon View
        val iconParams = iconView!!.layoutParams as LayoutParams
        val iconMarginStartRelativeToParent = iconViewMarginStart + backgroundMarginTopStart
        orientationHandler.setIconAppChipChildrenParams(iconParams, iconMarginStartRelativeToParent)

        iconView!!.layoutParams = iconParams
        iconView!!.setDrawableSize(appIconSize, appIconSize)

        // Layout Params for the collapsed Icon Text View
        val textMarginStart =
            iconMarginStartRelativeToParent + appIconSize + appNameHorizontalMarginCollapsed
        val iconTextCollapsedParams = appTitle!!.layoutParams as LayoutParams
        orientationHandler.setIconAppChipChildrenParams(iconTextCollapsedParams, textMarginStart)
        iconTextCollapsedParams.width =
            calculateCollapsedTextWidth(collapsedBackgroundBounds.width())
        appTitle?.layoutParams = iconTextCollapsedParams

        // Layout Params for the Icon Arrow View
        val iconArrowParams = iconArrowView!!.layoutParams as LayoutParams
        val arrowMarginStart = collapsedBackgroundBounds.right - arrowMarginEnd - arrowSize
        orientationHandler.setIconAppChipChildrenParams(iconArrowParams, arrowMarginStart)
        iconArrowView!!.pivotY = iconArrowParams.height / 2f
        iconArrowView!!.layoutParams = iconArrowParams

        // This method is called twice sometimes (like when rotating split tasks). It is called
        // once before onMeasure and onLayout, and again after onMeasure but before onLayout with
        // a new width. This happens because we update widths on rotation and on measure of
        // grouped task views. Calling requestLayout() does not guarantee a call to onMeasure if
        // it has just measured, so we explicitly call it here.
        measure(
            MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY),
        )
    }

    private fun enableMarquee(isEnabled: Boolean) {
        // Marquee should not be enabled when is running test harness.
        val isMarqueeEnabled = isEnabled && !Utilities.isRunningInTestHarness()
        appTitle?.let {
            it.ellipsize = if (isMarqueeEnabled) TruncateAt.MARQUEE else null
            it.isSelected = isMarqueeEnabled
        }
    }

    /**
     * Calculates the width available for the collapsed text (app name) within the view.
     *
     * This function determines the maximum width that the app name can occupy when the view is in
     * its collapsed state. It considers various factors such as the maximum allowed width, the
     * bounds of the collapsed background, the size of the app icon, the arrow, and the margins
     * around these elements.
     *
     * @return The calculated width available for the collapsed text (app name).
     */
    private fun calculateCollapsedTextWidth(width: Int): Int {
        val collapsedTextWidth =
            (width -
                iconViewMarginStart -
                appIconSize -
                arrowSize -
                appNameHorizontalMarginCollapsed -
                arrowMarginEnd)

        val spaceLeftForText = maxWidth - minWidthAllowed
        return minOf(collapsedTextWidth, spaceLeftForText).coerceAtLeast(0)
    }

    private fun calculateExpandedTextWidth(width: Int): Int =
        width -
            iconViewMarginStart -
            iconViewDrawableExpandedSize -
            arrowSize -
            appNameHorizontalMarginExpanded -
            arrowMarginEnd

    /** Gets the view split x-axis translation */
    fun getSplitTranslationX(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationX.get(INDEX_SPLIT_TRANSLATION)

    /**
     * Sets the view split x-axis translation
     *
     * @param value x-axis translation
     */
    fun setSplitTranslationX(value: Float) {
        getSplitTranslationX().value = value
    }

    /** Gets the view split y-axis translation */
    fun getSplitTranslationY(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationY[INDEX_SPLIT_TRANSLATION]

    /**
     * Sets the view split y-axis translation
     *
     * @param value y-axis translation
     */
    fun setSplitTranslationY(value: Float) {
        getSplitTranslationY().value = value
    }

    /** Gets the menu x-axis translation for split task */
    fun getMenuTranslationX(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationX[INDEX_MENU_TRANSLATION]

    /** Gets the menu y-axis translation for split task */
    fun getMenuTranslationY(): MultiPropertyFactory<View>.MultiProperty =
        viewTranslationY[INDEX_MENU_TRANSLATION]

    internal fun revealAnim(isRevealing: Boolean, animated: Boolean = true): AnimatorSet {
        cancelInProgressAnimations()
        val collapsedBackgroundBounds = getCollapsedBackgroundLtrBounds()
        val expandedBackgroundBounds = getExpandedBackgroundLtrBounds()
        val initialBackground = Rect(backgroundRelativeLtrLocation)
        animator = AnimatorSet()

        val isRtl = isLayoutRtl
        if (isRevealing) {
            bringToFront()
            // Animate background clipping
            val backgroundAnimator =
                ValueAnimator.ofObject(
                    backgroundAnimationRectEvaluator,
                    initialBackground,
                    expandedBackgroundBounds,
                )
            backgroundAnimator.addUpdateListener { invalidateOutline() }

            val iconViewScaling = iconViewDrawableExpandedSize / appIconSize.toFloat()
            val arrowTranslationX =
                (expandedBackgroundBounds.right - collapsedBackgroundBounds.right).toFloat()
            val iconCenterToTextCollapsed = appIconSize / 2f + appNameHorizontalMarginCollapsed
            val iconCenterToTextExpanded =
                iconViewDrawableExpandedSize / 2f + appNameHorizontalMarginCollapsed
            val textTranslationX = iconCenterToTextExpanded - iconCenterToTextCollapsed

            val textTranslationXWithRtl = if (isRtl) -textTranslationX else textTranslationX
            val arrowTranslationWithRtl = if (isRtl) -arrowTranslationX else arrowTranslationX

            animator!!.playTogether(
                backgroundAnimator,
                ObjectAnimator.ofFloat(iconView, SCALE_X, iconViewScaling),
                ObjectAnimator.ofFloat(iconView, SCALE_Y, iconViewScaling),
                ObjectAnimator.ofFloat(appTitle, TRANSLATION_X, textTranslationXWithRtl),
                ObjectAnimator.ofFloat(iconArrowView, TRANSLATION_X, arrowTranslationWithRtl),
                ObjectAnimator.ofFloat(iconArrowView, SCALE_Y, -1f),
            )
            status = AppChipStatus.Expanded
        } else {
            // Clip expanded text with reveal animation so it doesn't go beyond the edge of the menu
            val expandedTextClipAnim =
                ViewAnimationUtils.createCircularReveal(
                    appTitle,
                    if (isRtl) appTitle!!.width else 0,
                    appTitle!!.height / 2,
                    appTitle!!.width.toFloat(),
                    calculateCollapsedTextWidth(collapsedBackgroundBounds.width()).toFloat(),
                )

            // Animate background clipping
            val backgroundAnimator =
                ValueAnimator.ofObject(
                    backgroundAnimationRectEvaluator,
                    initialBackground,
                    collapsedBackgroundBounds,
                )
            backgroundAnimator.addUpdateListener { invalidateOutline() }

            animator!!.playTogether(
                expandedTextClipAnim,
                backgroundAnimator,
                ObjectAnimator.ofFloat(iconView, getScaleProperty(), 1f),
                ObjectAnimator.ofFloat(appTitle, TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat(iconArrowView, TRANSLATION_X, 0f),
                ObjectAnimator.ofFloat(iconArrowView, SCALE_Y, 1f),
            )
            status = AppChipStatus.Collapsed
            sendToBack()
        }

        animator!!.interpolator = Interpolators.EMPHASIZED

        // Increase the chip and appTitle size before the animation starts when it's expanding.
        // And decrease the size after the animation when is collapsing.
        animator!!.addListener(
            onStart = {
                // Hide focused border during expanding/collapsing animation
                if (isFocused) {
                    focusBorderAnimator.setBorderVisibility(visible = false, animated = false)
                }
                when (status) {
                    AppChipStatus.Expanded -> updateChipSize()
                    // Disable marquee before chip is collapsed
                    AppChipStatus.Collapsed -> enableMarquee(false)
                }
            },
            onEnd = {
                if (isFocused) animateFocusBorder(isAppearing = true)
                when (status) {
                    AppChipStatus.Collapsed -> updateChipSize()
                    // Enable marquee after chip is fully expanded
                    AppChipStatus.Expanded -> enableMarquee(true)
                }
            },
        )
        return animator!!
    }

    /**
     * Updates the width of the app title based on the current [AppChipStatus].
     *
     * This function dynamically adjusts the width of the `appTitle` TextView depending on whether
     * the app chip is in an expanded or collapsed state.
     * - When the chip is [AppChipStatus.Expanded], the title width is set to
     *   [expandedMaxTextWidth], allowing the title to potentially take up more space.
     * - When the chip is [AppChipStatus.Collapsed], the title width is calculated based on the
     *   width of the collapsed background. This ensures the title fits within the smaller,
     *   collapsed chip boundaries. The width is then determined by calling
     *   [calculateCollapsedTextWidth].
     */
    private fun updateChipSize() {
        val chipWidth = getChipWidth()
        when (status) {
            AppChipStatus.Expanded -> {
                updateLayoutParams { width = chipWidth }
                appTitle!!.updateLayoutParams { width = calculateExpandedTextWidth(chipWidth) }
            }
            AppChipStatus.Collapsed -> {
                appTitle!!.updateLayoutParams {
                    val collapsedBackgroundWidth = getCollapsedBackgroundLtrBounds().width()
                    width = calculateCollapsedTextWidth(collapsedBackgroundWidth)
                }
                updateLayoutParams { width = chipWidth }
            }
        }
    }

    private fun getCollapsedBackgroundLtrBounds(): Rect {
        val bounds = Rect(0, 0, minimumWidth, collapsedMenuDefaultHeight)
        bounds.offset(backgroundMarginTopStart, backgroundMarginTopStart)
        return bounds
    }

    private fun getExpandedBackgroundLtrBounds() =
        Rect(0, 0, expandedMenuDefaultWidth, expandedMenuDefaultHeight)

    private fun getCollapsedBackgroundWidth() = getCollapsedBackgroundLtrBounds().right

    private fun getChipWidth(): Int {
        // TODO(b/292269949): When in fake orientation, the width of the chip remains expanded
        //  to prevent wrong translation due to chip rotation and anchor.
        if (!isLayoutNaturalToLauncher) return expandedMenuDefaultWidth
        return when (status) {
            AppChipStatus.Expanded -> expandedMenuDefaultWidth
            AppChipStatus.Collapsed -> getCollapsedBackgroundWidth()
        }
    }

    private fun cancelInProgressAnimations() {
        // We null the `AnimatorSet` because it holds references to the `Animators` which aren't
        // expecting to be mutable and will cause a crash if they are re-used.
        if (animator != null && animator!!.isStarted) {
            animator!!.cancel()
            animator = null
        }
    }

    override fun bringToFront() {
        super.bringToFront()
        z = iconMenuElevation + Z_INDEX_FRONT
        updateParentZIndex(Z_INDEX_FRONT)
    }

    private fun sendToBack() {
        z = iconMenuElevation
        updateParentZIndex(0f)
    }

    private fun updateParentZIndex(zIndex: Float) {
        val parentView = parent as? TaskView
        if (parentView?.isOnGridBottomRow == true) {
            parentView.z = zIndex
        }
    }

    override fun focusSearch(direction: Int): View? {
        if (mParent == null) return null
        return when (direction) {
            FOCUS_RIGHT,
            FOCUS_DOWN -> mParent.focusSearch(this, FOCUS_FORWARD)
            FOCUS_UP,
            FOCUS_LEFT -> mParent.focusSearch(this, FOCUS_BACKWARD)
            else -> super.focusSearch(direction)
        }
    }

    /**
     * We need to over-ride here due to liveTile mode, the [OverviewInputConsumer] is added, which
     * consumes all [InputEvent]'s and focus isn't moved correctly.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        val currentFocus = findFocus() ?: return super.dispatchKeyEvent(event)

        val nextFocus =
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> focusSearch(currentFocus, FOCUS_BACKWARD)
                KeyEvent.KEYCODE_DPAD_DOWN -> focusSearch(currentFocus, FOCUS_FORWARD)
                KeyEvent.KEYCODE_TAB ->
                    focusSearch(
                        currentFocus,
                        if (event.isShiftPressed) FOCUS_BACKWARD else FOCUS_FORWARD,
                    )
                else -> null
            }

        return nextFocus?.requestFocus() ?: super.dispatchKeyEvent(event)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        super.setOnLongClickListener(l?.withFeedback())
    }

    fun reset() {
        setText(null)
        setDrawable(null)
    }

    enum class AppChipStatus {
        Expanded,
        Collapsed,
    }

    private fun OnLongClickListener.withFeedback(): OnLongClickListener {
        val delegate = this
        return OnLongClickListener { v: View ->
            if (Flags.msdlFeedback()) {
                msdlPlayerWrapper.playToken(MSDLToken.LONG_PRESS)
            }
            delegate.onLongClick(v)
        }
    }

    private companion object {
        private val SUM_AGGREGATOR = FloatBiFunction { a: Float, b: Float -> a + b }

        private const val Z_INDEX_FRONT = 10f

        private enum class Alpha {
            Content,
            ColorTint,
            Modal,
            /** Used to hide the app chip for 90:10 flex split. */
            FlexSplit,
            SettledProgress,
        }

        private const val INDEX_SPLIT_TRANSLATION = 0
        private const val INDEX_MENU_TRANSLATION = 1
        private const val INDEX_COUNT_TRANSLATION = 2
    }
}
