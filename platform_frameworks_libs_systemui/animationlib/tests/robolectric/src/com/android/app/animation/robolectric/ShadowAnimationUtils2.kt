package com.android.app.animation.robolectric

import android.view.animation.AnimationUtils
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAnimationUtils

/**
 * This shadow overwrites [ShadowAnimationUtils] and ensures that the real implementation of
 * [AnimationUtils] is used in tests.
 */
@Implements(AnimationUtils::class)
class ShadowAnimationUtils2
