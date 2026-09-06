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
 * limitations under the License.
 */

package com.android.launcher3.tapl;

import androidx.test.uiautomator.UiObject2;

/**
 * Operations on qsb from either Home screen or AllApp screen.
 */
public abstract class Qsb implements SearchInputSource {

    protected final LauncherInstrumentation mLauncher;
    private final UiObject2 mContainer;
    private final String mQsbResName;

    protected Qsb(LauncherInstrumentation launcher, UiObject2 container, String qsbResName) {
        mLauncher = launcher;
        mContainer = container;
        mQsbResName = qsbResName;
        waitForQsbObject();
    }

    // Waits for the quick search box.
    private UiObject2 waitForQsbObject() {
        return mLauncher.waitForObjectInContainer(mContainer, mQsbResName);
    }

    /**
     * Show search result page from tapping qsb.
     */
    public SearchResultFromQsb showSearchResult() {
        try (LauncherInstrumentation.Closable c = mLauncher.addContextLayer(
                "want to open search result page");
             LauncherInstrumentation.Closable e = mLauncher.eventsCheck()) {
            clickQsb();
            try (LauncherInstrumentation.Closable c2 = mLauncher.addContextLayer(
                    "clicked qsb to open search result page")) {
                return createSearchResult();
            }
        }
    }

    protected void clickQsb() {
        waitForQsbObject().click();
    }

    @Override
    public LauncherInstrumentation getLauncher() {
        return mLauncher;
    }

    @Override
    public SearchResultFromQsb getSearchResultForInput() {
        return createSearchResult();
    }

    protected SearchResultFromQsb createSearchResult() {
        return new SearchResultFromQsb(mLauncher);
    }
}
