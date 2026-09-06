/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.personalcontext.ace.client.clientsdk.compat

import android.animation.LayoutTransition
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.service.personalcontext.hint.ContextHint
import android.service.personalcontext.insight.ContextInsight
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.ViewCompat.NestedScrollType
import androidx.core.view.ViewCompat.ScrollAxis
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.personalcontext.ace.client.R
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedProvider
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedProviderImpl
import com.android.personalcontext.ace.client.clientlib.AceEmbeddedUiSize
import com.android.personalcontext.ace.client.clientsdk.compat.observable.DistinctObservableDelegates
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehavior
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedOverlapBehaviorEnum
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionState
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedSessionStateImpl
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiState
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Error
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Hidden
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Pending
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Retryable
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedUiVisibility.Shown
import com.android.personalcontext.ace.client.clientsdk.state.AceEmbeddedZOrderOverrideValue
import com.android.personalcontext.ace.client.clientsdk.state.NullableRect
import com.android.personalcontext.ace.client.prototype.PrototypeInsightUtils.toPrototypeInsight
import com.android.personalcontext.ace.client.prototype.embeddedscroll.EmbeddedScrollInsight
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * [AceEmbeddedSurfaceViewCompat] is the main interface for using ACE embedded to securely render
 * remote UI in an Android View hierarchy. To instantiate this view, you must set [hints] in code
 * before this view is attached to the window. You may optionally provide the
 * [R.attr.backgroundSurfaceColor] in XML, or set [backgroundSurfaceColor] in code.
 *
 * Each instance of this view remotely renders one instance of a ACE embedded session. This view
 * acts as a wrapper around
 * [AceEmbeddedProvider][com.android.personalcontext.ace.client.clientlib.AceEmbeddedProvider],
 * fully managing the lifecycle of a ACE embedded session by invoking the appropriate
 * [com.android.personalcontext.ace.client.clientlib.AceEmbeddedProvider.connect] and
 * [com.android.personalcontext.ace.client.clientlib.AceEmbeddedSession.update] operations behind
 * the scenes.
 *
 * This view is designed to be self-contained, following all common custom view rules and
 * expectations. This way, callers can mostly treat this as a locally rendered view.
 *
 * Be aware that this view observes its own visibility and attach state in order to automatically
 * connect to and disconnect from its ACE embedded session. That means that if this view is a child
 * of a RecyclerView, you must enable
 * [setHasStableIds][androidx.recyclerview.widget.RecyclerView.Adapter.setHasStableIds] to prevent
 * this view from being detached unnecessarily.
 *
 * Click handling in ACE embedded sessions is modeled as a data egress, which you can access by
 * setting a [AceEmbeddedSessionListener].
 *
 * Built-in behaviors:
 * * To render the ACE embedded session, attach this view onto your view hierarchy and ensure that
 *   it is [View.VISIBLE].
 * * To close the ACE embedded session, detach this view from your view hierarchy or set it to
 *   [View.GONE].
 * * Set a [AceEmbeddedSessionListener] to observe the state of the ACE embedded session.
 * * Use [setOnClickWhenBehindListener] to handle click events when the z-order is [Behind].
 * * When properties like [hints] and [backgroundSurfaceColor] are updated, or when [onMeasure]
 *   detects new measurement constraints, this view will correctly [AceEmbeddedSessionState.update]
 *   the ACE embedded session.
 * * Use [overlapBehavior], [overlapInsets], and [zOrderOverrides] to define how this composable
 *   interops with your native ui. For example, you can define the [AceEmbeddedOverlapBehavior] of
 *   the SurfaceView so that it draws behind any native UI when it intersects any overlap zones.
 * * This composable dispatches nested scroll events up the view hierarchy when touch gestures
 *   originate from the ACE embedded session. To take advantage of this behavior, ensure this view
 *   has an ancestor that implements NestedScrollingParent, like
 *   [com.android.personalcontext.ace.client.clientsdk.compat.recyclerview.NestedScrollingParentRecyclerView].
 *
 * Optional attributes you can define in XML (each attribute also has a corresponding setter):
 * * [R.attr.compat_backgroundSurfaceColor] - The background color that matches the color of the
 *   surface under the [AceEmbeddedSurfaceViewCompat]. This is required to support
 *   [AceEmbeddedOverlapBehavior.SetZOrderBehind.WithFade].
 * * [R.attr.compat_overlapBehavior] - The overlap behavior of the [AceEmbeddedSurfaceViewCompat],
 *   defining how the surface behaves when the bounds of the composable intersect with any overlap
 *   zones.
 * * [R.attr.compat_overlapInsetsLeft], [R.attr.compat_overlapInsetsTop],
 *   [R.attr.compat_overlapInsetsRight], [R.attr.compat_overlapInsetsBottom] - The insets relative
 *   to each edge of the [window][getLocationInWindow] where the [AceEmbeddedSurfaceViewCompat]
 *   should not overlap. To disable overlap detection for a specific edge, assign the semantic value
 *   [Float.NEGATIVE_INFINITY] to that edge's inset.
 * * [R.attr.compat_shouldHideOnUpdating] - Whether the [AceEmbeddedSurfaceViewCompat] should hide
 *   on the [Shown.Updating] state.
 * * [R.attr.compat_shouldBlur] Whether the remote UI should have a blur effect applied to the
 *   window.
 * * [R.attr.compat_animateStateChanges] - The [LayoutTransition] for the optional contents, which
 *   can be animated when the ACE embedded session state changes.
 * * [R.attr.compat_debugBorderEnabled], [R.attr.debugBorderWidth] - Whether to draw a debug border
 *   inside the bounds of the view, showing z-order and overlap.
 *
 * You may define child views in XML, with ids [R.id.ace_embedded_surface_view_hidden_content],
 * [R.id.ace_embedded_surface_view_pending_content], and
 * [R.id.ace_embedded_surface_view_error_content]. These child views will be displayed when the DUI
 * session state is [Hidden], [Pending], and [Error] respectively.
 */
class AceEmbeddedSurfaceViewCompat : FrameLayout, NestedScrollingChild3 {

    /**
     * The provider used to connect to the ACE embedded session.
     *
     * You can customize this to provide different timeout values, background scopes, or mock
     * implementations for testing. This property must be set before the view is attached to the
     * window.
     */
    var aceEmbeddedProvider: AceEmbeddedProvider = AceEmbeddedProviderImpl()
        set(value) {
            check(!::sessionState.isInitialized) {
                "aceEmbeddedProvider must be set before the view is attached to the window."
            }
            field = value
        }

    /**
     * The ACE embedded session state of a [AceEmbeddedSurfaceViewCompat], allows for observing and
     * controlling the ACE embedded session.
     */
    lateinit var sessionState: AceEmbeddedSessionState
        @VisibleForTesting set

    internal lateinit var coroutineScope: CoroutineScope
        @VisibleForTesting set

    /**
     * A container for optional child content views that are displayed based on the ACE embedded
     * session state.
     *
     * This FrameLayout serves two primary purposes:
     * 1. **State-based Content**: It holds child views that are only visible during specific
     *    session states, such as a loading indicator (`pendingContent`) or an error message
     *    (`errorContent`).
     * 2. **Animation Isolation**: It allows for `LayoutTransition` animations to run on its
     *    children when the UI state changes. This is critical because the sibling `SurfaceView`
     *    does not correctly handle `LayoutTransition`, so animations must occur in a separate
     *    container.
     */
    private val optionalContentContainer =
        FrameLayout(context).also { addView(it, LayoutParams(MATCH_PARENT, MATCH_PARENT)) }

    /**
     * The child view that's displayed when the ACE embedded session state is
     * [AceEmbeddedUiVisibility.Hidden].
     */
    private var hiddenContent: View? = null

    /**
     * The child view that's displayed when the ACE embedded session state is
     * [AceEmbeddedUiVisibility.Pending].
     */
    private var pendingContent: View? = null

    /** A sibling view of [pendingContent], used for giving it a size. */
    private var pendingContentSpacer: View? = null

    /**
     * The child view that's displayed when the ACE embedded session state is
     * [AceEmbeddedUiVisibility.Error].
     */
    private var errorContent: View? = null

    internal val surfaceView =
        InternalSurfaceView(this).apply {
            isVisible = true
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.setFixedSize(0, 0)
        }

    /** The listener for ACE embedded session events. */
    var listener: AceEmbeddedSessionListener? = null

    /**
     * Sets a listener that will be invoked when the view is clicked, but only when not
     * [zOrderOnTop].
     *
     * When not [zOrderOnTop], the remote UI does not receive the user's touches, so no egress can
     * occur. This helps to optionally cover that case, so the touch isn't discarded.
     */
    fun setOnClickWhenBehindListener(l: OnClickListener?) {
        surfaceView.setOnClickListener(l)
        zOrderUpdateEffectCompat()
    }

    internal var sessionVisibility: AceEmbeddedUiVisibility by
        DistinctObservableDelegates.observable(Hidden.Uninitialized) { overlapFadeEffectCompat() }

    /**
     * The hints passed from the client as input to the ACE embedded data service.
     *
     * Must be set before [onAttachedToWindow].
     */
    var hints: Set<ContextHint> by
        DistinctObservableDelegates.observable { inputsUpdateEffectCompat() }

    /**
     * The background color that matches the color of the surface under the
     * [AceEmbeddedSurfaceViewCompat].
     */
    @delegate:ColorInt
    var backgroundSurfaceColor: Int by
        DistinctObservableDelegates.observable(Color.TRANSPARENT) { inputsUpdateEffectCompat() }

    /**
     * The overlap behavior of the [AceEmbeddedSurfaceViewCompat], defining how the view behaves
     * when the bounds of the composable intersect with any overlap zones.
     */
    var overlapBehavior: AceEmbeddedOverlapBehavior by
        DistinctObservableDelegates.observable(AceEmbeddedOverlapBehavior.KeepZOrderOnTop) {
            overlapDetectionEffectCompat()
            overlapFadeEffectCompat()
            inputsUpdateEffectCompat()
        }

    /**
     * The insets relative to each edge of the [window][View.getLocationInWindow] where the
     * [AceEmbeddedSurfaceViewCompat] should not overlap.
     *
     * To disable overlap detection for a specific edge, assign `null` to that edge's inset.
     */
    var overlapInsets: NullableRect by
        DistinctObservableDelegates.observable(NullableRect.Empty) {
            overlapDetectionEffectCompat()
        }

    /**
     * The bitmasked nested scroll axes supported by the client. This ensures that the ACE embedded
     * session will only send these nested scroll events back to the client.
     */
    @delegate:ScrollAxis
    var clientNestedScrollAxes: Int by
        DistinctObservableDelegates.observable(SCROLL_AXIS_HORIZONTAL or SCROLL_AXIS_VERTICAL) {
            inputsUpdateEffectCompat()
        }

    /**
     * Whether the ACE embedded session should report a specific axis when a nested scroll gesture
     * is detected, and whether that axis should be locked such that subsequent nested scroll events
     * are only reported for that axis. A value of `true` is typical for Android UIs where scroll
     * axes are locked during a gesture, while a value of `false` can be used to give the illusion
     * of a 2D canvas. Only applicable when [clientNestedScrollAxes] is set to
     * `SCROLL_AXIS_HORIZONTAL or SCROLL_AXIS_VERTICAL`.
     */
    var clientNestedScrollAxisLocked: Boolean by
        DistinctObservableDelegates.observable(true) { inputsUpdateEffectCompat() }

    /**
     * Whether the remote UI should have a blur effect applied to the window. Change this value to
     * trigger the remote UI to animate to the new state.
     */
    var shouldBlur: Boolean by
        DistinctObservableDelegates.observable(false) { inputsUpdateEffectCompat() }

    /**
     * The custom [android.R.styleable#PersonalContextTheme] to be passed to a connected visualizer.
     * A visualizer can use this name to look up the theme resource in the client's resources, which
     * can then be used when creating an embedded surface for the client.
     */
    @delegate:StyleRes
    var themeResourceId: Int by
        DistinctObservableDelegates.observable(0) { inputsUpdateEffectCompat() }

    /** Whether the [AceEmbeddedSurfaceViewCompat] should hide on the [Shown.Updating] state. */
    var shouldHideOnUpdating: Boolean by
        DistinctObservableDelegates.observable(false) { overlapFadeEffectCompat() }

    /**
     * Transient states should set a key/value pair to override the z-order of the
     * [AceEmbeddedSurfaceViewCompat], taking precedence over the behavior of overlap detection.
     *
     * If multiple [key]s are set with conflicting values, the order of precedence is
     * [ZOrderOverrideValue.ForceBehind], then [ZOrderOverrideValue.ForceOnTop], then
     * [ZOrderOverrideValue.None].
     */
    fun setZOrderOverride(key: String, value: () -> AceEmbeddedZOrderOverrideValue) {
        zOrderOverrides += key to value()
    }

    /** The backing map for z-order overrides. */
    internal var zOrderOverrides: Map<String, AceEmbeddedZOrderOverrideValue> by
        DistinctObservableDelegates.observable(emptyMap()) {
            zOrderUpdateEffectCompat()
            inputsUpdateEffectCompat()
        }

    /** Whether the z-order is currently on top. */
    val zOrderOnTop: Boolean
        get() = zOrderUpdateEffectCompat.zOrderOnTop

    /**
     * Whether the [AceEmbeddedSurfaceViewCompat] is currently overlapping with any overlap zones
     * defined by [overlapInsets].
     */
    val isOverlapping: Boolean
        get() = overlapDetectionEffectCompat.isOverlapping

    /**
     * Whether to draw a debug border outside the bounds of the composable, showing z-order and
     * overlap.
     */
    var debugBorderEnabled: Boolean by
        DistinctObservableDelegates.observable(false) { enabled ->
            setPadding(if (enabled) ceil(debugBorderWidthPx).toInt() else 0)
            invalidate()
        }

    /** Stroke width of the debug border. */
    var debugBorderWidthPx: Float by
        DistinctObservableDelegates.observable(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics)
        ) { widthPx ->
            setPadding(if (debugBorderEnabled) ceil(widthPx).toInt() else 0)
            invalidate()
        }

    private var isFullyInitialized = false
    internal val inputsUpdateEffectCompat = AceEmbeddedInputsUpdateEffectCompat(this)
    internal val nestedScrollDispatchEffectCompat =
        AceEmbeddedNestedScrollDispatchEffectCompat(this)
    internal val overlapDetectionEffectCompat = AceEmbeddedOverlapDetectionEffectCompat(this)
    internal val overlapFadeEffectCompat = AceEmbeddedOverlapFadeEffectCompat(this)
    internal val zOrderUpdateEffectCompat = AceEmbeddedZOrderUpdateEffectCompat(this)

    /** Constructor for XML inflation. */
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : super(context, attrs, defStyleAttr) {
        context
            .obtainStyledAttributes(
                attrs,
                R.styleable.AceEmbeddedSurfaceViewCompat,
                defStyleAttr,
                0,
            )
            .use { typedArray ->
                this.backgroundSurfaceColor =
                    typedArray.getColor(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_backgroundSurfaceColor,
                        this.backgroundSurfaceColor,
                    )

                val overlapBehaviorOrdinal =
                    typedArray.getInt(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_overlapBehavior,
                        -1,
                    )
                this.overlapBehavior =
                    AceEmbeddedOverlapBehaviorEnum.entries.getOrNull(overlapBehaviorOrdinal)
                        ?: this.overlapBehavior

                this.overlapInsets =
                    this.overlapInsets.copy(
                        left =
                            typedArray.getNullableDimension(
                                R.styleable.AceEmbeddedSurfaceViewCompat_compat_overlapInsetsLeft,
                                this.overlapInsets.left,
                            ),
                        top =
                            typedArray.getNullableDimension(
                                R.styleable.AceEmbeddedSurfaceViewCompat_compat_overlapInsetsTop,
                                this.overlapInsets.top,
                            ),
                        right =
                            typedArray.getNullableDimension(
                                R.styleable.AceEmbeddedSurfaceViewCompat_compat_overlapInsetsRight,
                                this.overlapInsets.right,
                            ),
                        bottom =
                            typedArray.getNullableDimension(
                                R.styleable.AceEmbeddedSurfaceViewCompat_compat_overlapInsetsBottom,
                                this.overlapInsets.bottom,
                            ),
                    )

                this.clientNestedScrollAxes =
                    typedArray.getInt(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_clientNestedScrollAxes,
                        this.clientNestedScrollAxes,
                    )
                this.clientNestedScrollAxisLocked =
                    typedArray.getBoolean(
                        R.styleable
                            .AceEmbeddedSurfaceViewCompat_compat_clientNestedScrollAxisLocked,
                        this.clientNestedScrollAxisLocked,
                    )

                this.shouldBlur =
                    typedArray.getBoolean(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_shouldBlur,
                        this.shouldBlur,
                    )

                this.themeResourceId =
                    typedArray.getResourceId(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_themeResourceId,
                        this.themeResourceId,
                    )

                this.shouldHideOnUpdating =
                    typedArray.getBoolean(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_shouldHideOnUpdating,
                        this.shouldHideOnUpdating,
                    )

                val animateStateChanges =
                    typedArray.getBoolean(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_animateStateChanges,
                        false,
                    )
                if (animateStateChanges) layoutTransition = LayoutTransition()

                this.debugBorderEnabled =
                    typedArray.getBoolean(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_debugBorderEnabled,
                        this.debugBorderEnabled,
                    )

                this.debugBorderWidthPx =
                    typedArray.getDimension(
                        R.styleable.AceEmbeddedSurfaceViewCompat_compat_debugBorderWidth,
                        this.debugBorderWidthPx,
                    )
            }
    }

    init {
        isNestedScrollingEnabled = true
        isFullyInitialized = true
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        hiddenContent = findViewById(R.id.ace_embedded_surface_view_hidden_content)
        pendingContent = findViewById(R.id.ace_embedded_surface_view_pending_content)
        errorContent = findViewById(R.id.ace_embedded_surface_view_error_content)

        // Move optional content views into optionalContentContainer.
        for (content in listOfNotNull(hiddenContent, pendingContent, errorContent)) {
            removeView(content)
            optionalContentContainer.addView(content)
        }

        pendingContentSpacer = FrameLayout(context).also { optionalContentContainer.addView(it) }
    }

    private var attachedJob: Job? = null

    /* This view is only fully initialized once it is attached to the view hierarchy. */
    @Suppress("GlobalCoroutineDispatchers")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!::coroutineScope.isInitialized) {
            val parentContext =
                findViewTreeLifecycleOwner()?.lifecycleScope?.coroutineContext
                    ?: EmptyCoroutineContext

            this.coroutineScope = CoroutineScope(parentContext + SupervisorJob(parentContext[Job]))
        }

        if (!::sessionState.isInitialized) {
            this.sessionState = AceEmbeddedSessionStateImpl(coroutineScope, aceEmbeddedProvider)
        }

        attachedJob =
            coroutineScope.launch {

                // Handle ACE embedded session visibility changes.
                launch {
                    sessionState.uiStateFlow
                        .map { it.visibility }
                        .distinctUntilChanged()
                        .collect { visibility ->
                            Log.d(TAG, String.format("ACE embedded visibility: %s", visibility))

                            sessionVisibility = visibility

                            hiddenContent?.isVisible = visibility is Hidden
                            pendingContent?.isVisible = visibility is Pending
                            pendingContentSpacer?.isVisible = visibility is Pending
                            errorContent?.isVisible = visibility is Error

                            surfaceView.isAttached = visibility is Shown

                            if (visibility is Retryable) {
                                sessionState.connect(context)
                            }
                        }
                }

                // Handle ACE embedded session remote size updates.
                launch {
                    sessionState.uiStateFlow.map { it.size }.collect { onRemoteSizeChange(it) }
                }

                // Handle ACE embedded session nested scroll updates.
                launch {
                    sessionState.insightFlow
                        .mapNotNull { it.toPrototypeInsight<EmbeddedScrollInsight>() }
                        .collect { nestedScrollDispatchEffectCompat.onNestedScrollEvent(it) }
                }

                // Notify listeners.
                launch { sessionState.uiStateFlow.collect { listener?.onAceEmbeddedUiState(it) } }
                launch { sessionState.insightFlow.collect { listener?.onAceEmbeddedInsight(it) } }
            }

        overlapDetectionEffectCompat.onAttachedToWindow()
    }

    private var lastSizePx = Size(0, 0)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        lastSizePx = Size(w, h)
    }

    private fun onRemoteSizeChange(size: AceEmbeddedUiSize? = sessionState.uiStateFlow.value.size) {
        val remoteWidthPx = size?.widthPx ?: lastSizePx.width
        val remoteHeightPx = size?.heightPx ?: lastSizePx.height

        surfaceView.holder.setFixedSize(
            remoteWidthPx.coerceAtLeast(1),
            remoteHeightPx.coerceAtLeast(1),
        )

        pendingContentSpacer?.layoutParams?.apply {
            width = remoteWidthPx
            height = remoteHeightPx
        }

        requestLayout()
    }

    override fun onDetachedFromWindow() {
        attachedJob?.cancel()
        attachedJob = null

        super.onDetachedFromWindow()
        inputsUpdateEffectCompat.onDetachedFromWindow()
        nestedScrollDispatchEffectCompat.onDetachedFromWindow()
        overlapDetectionEffectCompat.onDetachedFromWindow()
    }

    /** Gets the [LayoutTransition] for the optional contents. */
    override fun getLayoutTransition(): LayoutTransition? {
        return optionalContentContainer.layoutTransition
    }

    /**
     * Sets the [LayoutTransition] for the optional contents, which can be animated when the DUI
     * session state changes.
     *
     * This [ViewGroup] is not allowed to have a [LayoutTransition] defined, since it is not
     * compatible with [SurfaceView].
     */
    override fun setLayoutTransition(transition: LayoutTransition?) {
        checkNotNull(optionalContentContainer) {
                "You must use app:animateLayoutChanges instead of android:animateLayoutChanges"
            }
            .layoutTransition = transition
    }

    /** Whether the child [SurfaceView] is attached to this [AceEmbeddedSurfaceViewCompat]. */
    private var InternalSurfaceView.isAttached
        get() = parent != null
        set(attach) {
            if (attach) {
                if (parent == null)
                    this@AceEmbeddedSurfaceViewCompat.addView(/* child= */ this, /* index= */ 0)
            } else {
                this@AceEmbeddedSurfaceViewCompat.removeView(this)
            }
        }

    private fun TypedArray.getNullableDimension(index: Int, current: Float?): Float? {
        // Use NaN as the sentinel for "null" because getDimension requires a primitive.
        val defaultValue = current ?: Float.NaN
        val value = getDimension(index, defaultValue)
        return if (value.isNaN()) null else value
    }

    // region InputsUpdateEffect
    // -----------------------------------------------------------------------------------------------

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this) {
            inputsUpdateEffectCompat.onVisibilityChanged(visibility == VISIBLE)
        }
    }

    override fun requestLayout() {
        super.requestLayout()
        if (isFullyInitialized) inputsUpdateEffectCompat.requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        inputsUpdateEffectCompat.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    // -----------------------------------------------------------------------------------------------
    // endregion

    // region Debug border and fade drawing
    // -----------------------------------------------------------------------------------------------
    override fun invalidate() {
        super.invalidate()
        surfaceView.invalidate()
    }

    private val debugBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    @Suppress("UnnecessaryVariable")
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        // Draw the debug border.
        if (!debugBorderEnabled || debugBorderWidthPx <= 0) return
        val halfStroke = debugBorderWidthPx / 2f
        val left = halfStroke
        val top = halfStroke
        val right = width - halfStroke
        val bottom = height - halfStroke

        if (right <= left || bottom <= top) return
        debugBorderPaint.strokeWidth = debugBorderWidthPx
        debugBorderPaint.color = if (zOrderOnTop) Color.GREEN else Color.RED
        canvas.drawRect(left, top, right, bottom, debugBorderPaint)
    }

    // -----------------------------------------------------------------------------------------------
    // endregion

    // region NestedScrollDispatchEffect
    // -----------------------------------------------------------------------------------------------

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        return nestedScrollDispatchEffectCompat.setNestedScrollingEnabled(enabled)
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return nestedScrollDispatchEffectCompat.isNestedScrollingEnabled
    }

    override fun hasNestedScrollingParent(): Boolean {
        return nestedScrollDispatchEffectCompat.hasNestedScrollingParent()
    }

    override fun hasNestedScrollingParent(@NestedScrollType type: Int): Boolean {
        return nestedScrollDispatchEffectCompat.hasNestedScrollingParent(type)
    }

    override fun startNestedScroll(axes: Int): Boolean {
        return nestedScrollDispatchEffectCompat.startNestedScroll(axes)
    }

    override fun startNestedScroll(@ScrollAxis axes: Int, @NestedScrollType type: Int): Boolean {
        return nestedScrollDispatchEffectCompat.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll() {
        return nestedScrollDispatchEffectCompat.stopNestedScroll()
    }

    override fun stopNestedScroll(@NestedScrollType type: Int) {
        return nestedScrollDispatchEffectCompat.stopNestedScroll(type)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean {
        return nestedScrollDispatchEffectCompat.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
        )
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        @NestedScrollType type: Int,
    ): Boolean {
        return nestedScrollDispatchEffectCompat.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
        )
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        @NestedScrollType type: Int,
        consumed: IntArray,
    ) {
        return nestedScrollDispatchEffectCompat.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
            type,
            consumed,
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean {
        return nestedScrollDispatchEffectCompat.dispatchNestedPreScroll(
            dx,
            dy,
            consumed,
            offsetInWindow,
        )
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        @NestedScrollType type: Int,
    ): Boolean {
        return nestedScrollDispatchEffectCompat.dispatchNestedPreScroll(
            dx,
            dy,
            consumed,
            offsetInWindow,
            type,
        )
    }

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean {
        return nestedScrollDispatchEffectCompat.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return nestedScrollDispatchEffectCompat.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun onStopNestedScroll(child: View) {
        return nestedScrollDispatchEffectCompat.onStopNestedScroll(child)
    }

    // -----------------------------------------------------------------------------------------------
    // endregion

    /** Listener for ACE embedded session events. */
    interface AceEmbeddedSessionListener {

        /** Ui state of the ACE embedded session. */
        fun onAceEmbeddedUiState(uiState: AceEmbeddedUiState) = Unit

        /** Nested scroll events. */
        fun onAceEmbeddedInsight(insight: ContextInsight) = Unit
    }

    /**
     * Represents an abstraction around some kind of effect or behavior. Mainly for readability and
     * clarity.
     */
    internal interface Effect {

        val view: AceEmbeddedSurfaceViewCompat
    }

    /**
     * A [SurfaceView] that works with [AceEmbeddedOverlapFadeEffectCompat] to support a transient
     * fade effect.
     */
    @Suppress("TikTok.AndroidFrameworkClassVisibility")
    internal class InternalSurfaceView
    @JvmOverloads
    constructor(
        private val view: AceEmbeddedSurfaceViewCompat,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : SurfaceView(view.context, attrs, defStyleAttr) {

        init {
            setWillNotDraw(false)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()

            val sessionVisibility = view.sessionVisibility as? Shown
            if (childSurfacePackage == null && sessionVisibility is Shown) {
                setChildSurfacePackage(sessionVisibility.surfacePackage)
            }
        }

        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val alpha = view.overlapFadeEffectCompat.animatedAlpha.value
            if (alpha > 0f) {
                // Fade the view by drawing a background overlay on top of the SurfaceView. Any fade
                // is
                // transient, and throughout that duration, OverlapFadeEffect ensures that
                // SurfaceView has a
                // z-order of [Behind].
                backgroundPaint.color = view.backgroundSurfaceColor
                backgroundPaint.alpha = (alpha * 255).toInt()

                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            }
        }
    }

    companion object {
        internal const val TAG = "AceEmbeddedSurfaceViewCompat"
    }
}
