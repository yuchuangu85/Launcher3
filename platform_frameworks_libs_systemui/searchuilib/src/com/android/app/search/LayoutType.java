/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.app.search;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants to be used with {@link SearchTarget}.
 */
public class LayoutType {

    @StringDef(value = {
            ICON_SINGLE_VERTICAL_TEXT,
            ICON_HORIZONTAL_TEXT,
            HORIZONTAL_MEDIUM_TEXT,
            EXTRA_TALL_ICON_ROW,
            SMALL_ICON_HORIZONTAL_TEXT,
            SMALL_ICON_HORIZONTAL_TEXT_THUMBNAIL,
            ICON_CONTAINER,
            THUMBNAIL_CONTAINER,
            BIG_ICON_MEDIUM_HEIGHT_ROW,
            THUMBNAIL,
            ICON_SLICE,
            WIDGET_PREVIEW,
            WIDGET_LIVE,
            PEOPLE_TILE,
            TEXT_HEADER,
            DIVIDER,
            EMPTY_DIVIDER,
            CALCULATOR,
            SECTION_HEADER,
            TALL_CARD_WITH_IMAGE_NO_ICON,
            TEXT_HEADER_ROW,
            QS_TILE,
            QS_TILE_CONTAINER,
            PLACEHOLDER,
            RICHANSWER_PLACEHOLDER,
            PLAY_PLACEHOLDER,
            EDUCARD,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchLayoutType {}

    //     ------
    //    | icon |
    //     ------
    //      text
    public static final String ICON_SINGLE_VERTICAL_TEXT = "icon";

    // Below three layouts (to be deprecated) and two layouts render
    // {@link SearchTarget}s in following layout.
    //     ------                            ------   ------
    //    |      | title                    |(opt)|  |(opt)|
    //    | icon | subtitle (optional)      | icon|  | icon|
    //     ------                            ------  ------
    @Deprecated
    public static final String ICON_SINGLE_HORIZONTAL_TEXT = "icon_text_row";
    @Deprecated
    public static final String ICON_DOUBLE_HORIZONTAL_TEXT = "icon_texts_row";
    @Deprecated
    public static final String ICON_DOUBLE_HORIZONTAL_TEXT_BUTTON = "icon_texts_button";

    // will replace ICON_DOUBLE_* ICON_SINGLE_* layouts
    public static final String ICON_HORIZONTAL_TEXT = "icon_row";
    public static final String HORIZONTAL_MEDIUM_TEXT = "icon_row_medium";
    public static final String EXTRA_TALL_ICON_ROW = "extra_tall_icon_row";
    public static final String SMALL_ICON_HORIZONTAL_TEXT = "short_icon_row";
    public static final String SMALL_ICON_HORIZONTAL_TEXT_THUMBNAIL = "short_icon_row_thumbnail";

    // This layout contains a series of icon results (currently up to 4 per row).
    // The container does not support stretching for its children, and can only contain
    // {@link #ICON_SINGLE_VERTICAL_TEXT} layout types.
    public static final String ICON_CONTAINER = "icon_container";

    // This layout contains a series of thumbnails (currently up to 3 per row).
    // The container supports stretching for its children, and can only contain {@link #THUMBNAIL}
    // layout types.
    public static final String THUMBNAIL_CONTAINER = "thumbnail_container";

    // This layout creates a container for people grouping
    // Only available above version code 2
    public static final String BIG_ICON_MEDIUM_HEIGHT_ROW = "big_icon_medium_row";

    // This layout creates square thumbnail image (currently 3 column)
    public static final String THUMBNAIL = "thumbnail";

    // This layout contains an icon and slice
    public static final String ICON_SLICE = "slice";

    // Widget bitmap preview
    public static final String WIDGET_PREVIEW = "widget_preview";

    // Live widget search result
    public static final String WIDGET_LIVE = "widget_live";

    // Layout type used to display people tiles using shortcut info
    public static final String PEOPLE_TILE = "people_tile";

    // Deprecated
    // text based header to group various layouts in low confidence section of the results.
    public static final String TEXT_HEADER = "header";

    // horizontal bar to be inserted between fallback search results and low confidence section
    public static final String EMPTY_DIVIDER = "empty_divider";

    @Deprecated(since = "LC: Use EMPTY_DIVIDER instead")
    public static final String DIVIDER = EMPTY_DIVIDER;

    // layout representing quick calculations
    public static final String CALCULATOR = "calculator";

    // From version code 4, if TEXT_HEADER_ROW is used, no need to insert this on-device
    // section header.
    public static final String SECTION_HEADER = "section_header";

    // layout for a tall card with header and image, and no icon.
    public static final String TALL_CARD_WITH_IMAGE_NO_ICON = "tall_card_with_image_no_icon";

    // Layout for a text header
    // Available for SearchUiManager proxy service to use above version code 3
    public static final String TEXT_HEADER_ROW = "text_header_row";

    // Layout for a quick settings tile
    public static final String QS_TILE = "qs_tile";

    // Layout for a quick settings tile container
    public static final String QS_TILE_CONTAINER = "qs_tile_container";

    // Placeholder for web suggest.
    public static final String PLACEHOLDER = "placeholder";

    // Placeholder for rich answer cards.
    // Only available on or above version code 3.
    public static final String RICHANSWER_PLACEHOLDER = "richanswer_placeholder";

    // Play placeholder
    public static final String PLAY_PLACEHOLDER = "play_placeholder";

    // Only available on or above version code 8 (UP1A)
    // This layout is for educard.
    public static final String EDUCARD = "educard";

    // layout representing the empty, no query state
    public static final String EMPTY_STATE = "empty_state";

    // layout representing search settings
    public static final String SEARCH_SETTINGS = "launcher_settings";
}
