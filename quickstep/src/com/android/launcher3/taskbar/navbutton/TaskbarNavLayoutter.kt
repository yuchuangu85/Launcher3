/*
 * Copyright (C) 2022 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.launcher3.taskbar.navbutton

import android.content.res.ColorStateList
import android.content.res.Resources
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.util.Themes

/** Layoutter for rendering task bar in large screen, both in 3-button and gesture nav mode. */
class TaskbarNavLayoutter(
    resources: Resources,
    navButtonContainer: LinearLayout,
    endContextualContainer: ViewGroup,
    startContextualContainer: ViewGroup,
    imeSwitcher: ImageView?,
    a11yButton: ImageView?,
    moreOptionsButton: ImageView?,
    space: Space?,
    backButton: ImageView?,
    homeButton: ImageView?,
    recentsButton: ImageView?,
) :
    AbstractNavButtonLayoutter(
        resources,
        navButtonContainer,
        endContextualContainer,
        startContextualContainer,
        imeSwitcher,
        a11yButton,
        moreOptionsButton,
        space,
        backButton,
        homeButton,
        recentsButton,
    ) {

    override fun layoutButtons(context: TaskbarActivityContext, isA11yButtonPersistent: Boolean) {
        layoutButtons(
            context,
            isA11yButtonPersistent,
            isA11yVisible = false,
            isMoreOptionsVisible = false,
        )
    }

    override fun layoutButtons(
        context: TaskbarActivityContext,
        isA11yButtonPersistent: Boolean,
        isA11yVisible: Boolean,
        isMoreOptionsVisible: Boolean,
    ) {
        clearContextualContainers()

        setupNavButtonContainer(context, isA11yButtonPersistent)
        distributeNavButtonSpacing()

        setupStartContextualButtons(context)
        setupEndContextualButtons(context, isA11yVisible, isMoreOptionsVisible)
    }

    private fun clearContextualContainers() {
        startContextualContainer.removeAllViews()
        endContextualContainer.removeAllViews()
    }

    private fun setupNavButtonContainer(
        context: TaskbarActivityContext,
        isA11yButtonPersistent: Boolean,
    ) {
        val navMarginEnd = calculateNavMarginEnd(context, isA11yButtonPersistent)

        val navButtonParams =
            FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                .apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = navMarginEnd
                }

        navButtonContainer.orientation = LinearLayout.HORIZONTAL
        navButtonContainer.layoutParams = navButtonParams

        addThreeButtons()
    }

    private fun calculateNavMarginEnd(
        context: TaskbarActivityContext,
        isA11yButtonPersistent: Boolean,
    ): Int {
        var marginEnd =
            resources.getDimension(context.deviceProfile.inv.inlineNavButtonsEndSpacing).toInt()

        val bottomRect = context.display.cutout?.boundingRectBottom
        if (bottomRect != null && !bottomRect.isEmpty) {
            marginEnd = bottomRect.width()
        }

        if (isA11yButtonPersistent && marginEnd < endContextualContainer.width) {
            marginEnd += resources.getDimensionPixelSize(R.dimen.taskbar_hotseat_nav_spacing) / 2
        }

        return marginEnd
    }

    private fun distributeNavButtonSpacing() {
        val spaceInBetween =
            resources.getDimensionPixelSize(R.dimen.taskbar_nav_button_space_inbetween)
        val lastIndex = navButtonContainer.childCount - 1

        for (i in 0..lastIndex) {
            val navButton = navButtonContainer.getChildAt(i)
            val params = navButton.layoutParams as LinearLayout.LayoutParams
            params.weight = 0f

            when (i) {
                0 -> {
                    params.marginStart = 0
                    params.marginEnd = spaceInBetween
                }
                lastIndex -> {
                    params.marginStart = spaceInBetween
                    params.marginEnd = 0
                }
                else -> {
                    params.marginStart = spaceInBetween
                    params.marginEnd = spaceInBetween
                }
            }
        }
    }

    private fun setupStartContextualButtons(context: TaskbarActivityContext) {
        if (context.deviceProfile.deviceProperties.deviceConfiguration.isGestureMode) return

        repositionStartContainer()
        addImeSwitcherIfPresent()
    }

    private fun repositionStartContainer() {
        val buttonWidth = resources.getDimensionPixelSize(R.dimen.taskbar_contextual_button_width)
        val margin = resources.getDimensionPixelSize(R.dimen.taskbar_contextual_button_padding)

        repositionContextualContainer(
            startContextualContainer,
            buttonWidth,
            margin,
            margin,
            Gravity.START,
        )
    }

    private fun addImeSwitcherIfPresent() {
        if (imeSwitcher == null) return

        startContextualContainer.addView(imeSwitcher)

        val startMargin =
            resources.getDimensionPixelSize(R.dimen.taskbar_ime_switcher_button_margin_start)

        val imeParams =
            FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                .apply {
                    marginStart = startMargin
                    gravity = Gravity.CENTER_VERTICAL
                }

        imeSwitcher.layoutParams = imeParams
    }

    private fun setupEndContextualButtons(
        context: TaskbarActivityContext,
        isA11yVisible: Boolean,
        isMoreOptionsVisible: Boolean,
    ) {
        if (!context.deviceProfile.deviceProperties.deviceConfiguration.isGestureMode) {
            repositionContextualContainer(endContextualContainer, WRAP_CONTENT, 0, 0, Gravity.END)
        }

        detachFromParent(a11yButton)
        detachFromParent(moreOptionsButton)

        resetButtonFilters()

        when {
            isA11yVisible && isMoreOptionsVisible -> layoutContextualPill(context)
            isA11yVisible -> layoutSoloA11yButton()
            else -> endContextualContainer.visibility = View.GONE
        }
    }

    private fun detachFromParent(view: View?) {
        (view?.parent as? ViewGroup)?.removeView(view)
    }

    private fun resetButtonFilters() {
        a11yButton?.clearColorFilter()
        moreOptionsButton?.clearColorFilter()
    }

    private fun layoutContextualPill(context: TaskbarActivityContext) {
        // Safe assert: verified visibility before calling
        sanitizeButtonState(a11yButton!!)
        sanitizeButtonState(moreOptionsButton!!)

        addContextualGroup(context, endContextualContainer, a11yButton, moreOptionsButton)
        endContextualContainer.visibility = View.VISIBLE
    }

    private fun layoutSoloA11yButton() {
        sanitizeButtonState(a11yButton!!)
        endContextualContainer.addView(a11yButton)
        a11yButton.layoutParams = getParamsToCenterView()
        endContextualContainer.visibility = View.VISIBLE
    }

    private fun sanitizeButtonState(view: ImageView) {
        view.clearColorFilter()
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.alpha = 1f
    }

    private fun addContextualGroup(
        context: TaskbarActivityContext,
        parent: ViewGroup,
        a11yButton: ImageView,
        moreOptionsButton: ImageView,
    ) {
        val group = createContextualContainer(context, parent)
        val iconTint = Themes.getAttrColor(context, android.R.attr.textColorSecondary)
        val spacing = resources.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween_a11y)

        addIconToGroup(group, a11yButton, iconTint)
        addIconToGroup(group, moreOptionsButton, iconTint, marginStart = spacing)

        parent.addView(group, createGroupLayoutParams())
    }

    private fun createGroupLayoutParams(): FrameLayout.LayoutParams {
        val horizontalMargin =
            resources.getDimensionPixelSize(
                R.dimen.taskbar_a11y_more_options_pill_horizontal_margin
            )

        return getParamsToCenterView().apply {
            width = WRAP_CONTENT
            height = WRAP_CONTENT
            setMargins(horizontalMargin, 0, horizontalMargin, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun createContextualContainer(
        context: TaskbarActivityContext,
        parent: ViewGroup,
    ): LinearLayout {
        val inflater = LayoutInflater.from(parent.context)
        val group =
            inflater.inflate(R.layout.taskbar_contextual_button_group, parent, false)
                as LinearLayout

        return group.apply {
            setBackgroundResource(R.drawable.contextual_button_background)
            backgroundTintList =
                ColorStateList.valueOf(Themes.getAttrColor(context, R.attr.popupColorTertiary))
        }
    }

    private fun addIconToGroup(
        group: ViewGroup,
        view: ImageView,
        tintColor: Int,
        marginStart: Int = 0,
    ) {
        view.setColorFilter(tintColor)
        view.visibility = View.VISIBLE

        val params =
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                this.marginStart = marginStart
            }
        group.addView(view, params)
    }
}
