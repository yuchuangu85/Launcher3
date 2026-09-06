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

package com.google.android.torus.settings.inlinecontrol

import android.Manifest
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import androidx.slice.SliceProvider
import androidx.slice.builders.ListBuilder
import com.google.android.torus.settings.storage.CustomizedSharedPreferences

/**
 * BaseSliceConfigProvider is the base class for configuration wallpaper Slices.
 * It can be extended and overridden the [uri], [onBindSlice], and [onConfigChange] for providing
 * different Slice UI.
 */
abstract class BaseSliceConfigProvider : SliceProvider(Manifest.permission.BIND_WALLPAPER) {
    companion object {
        const val EXTRA_WALLPAPER_URI = "extra_wallpaper_uri"
    }

    protected abstract val sharedPrefKey: String
    abstract val uriStringId: Int
    abstract fun onConfigChange()

    protected lateinit var preferences: CustomizedSharedPreferences
    protected lateinit var listBuilder: ListBuilder
    private lateinit var sharedPreferenceListener: OnSharedPreferenceChangeListener
    private lateinit var uri: Uri

    override fun onCreateSliceProvider(): Boolean {
        context?.let { context ->
            uri = Uri.parse(context.getString(uriStringId))
            preferences = CustomizedSharedPreferences(context, sharedPrefKey)
            sharedPreferenceListener = OnSharedPreferenceChangeListener { _, _ ->
                preferences.load(true)
                onConfigChange()
                updateSlice()
            }
            preferences.register(sharedPreferenceListener)
            preferences.load(true)
            onConfigChange()
            return true
        } ?: return false
    }

    private fun updateSlice() {
        context?.contentResolver?.notifyChange(uri, null)
    }
}
