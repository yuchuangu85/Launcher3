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

package com.google.android.torus.settings.storage

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.util.Log
import androidx.annotation.GuardedBy
import org.json.JSONException
import org.json.JSONObject

/**
 * This class provide APIs to save and load a bundle of values that may map to
 * customization (inline control) settings. Each instance will have two set of values, one for
 * normal and one for preview mode.
 * Currently supported value types are boolean, integer, float and string.
 */
class CustomizedSharedPreferences(context: Context, name: String) {
    companion object {
        private const val TAG = "CustomizedSharedPref"
        private const val DEBUG = false
        private const val SUFFIX_PREVIEW = "_preview"
        const val PREF_FILENAME = "inline_control"

        /**
         * Convert a JSONObject to Bundle.
         *
         * @param json a JSONObject
         * @return a Bundle object
         */
        private fun jsonToBundle(json: JSONObject): Bundle {
            val bundle = Bundle()
            val it = json.keys()
            while (it.hasNext()) {
                val key = it.next()
                try {
                    when (val obj = json[key]) {
                        is Boolean -> {
                            bundle.putBoolean(key, obj)
                        }
                        is Int -> {
                            bundle.putInt(key, obj)
                        }
                        is Double -> {
                            // Only support Float, but JSONObject save Float as Double,
                            // So we convert Double to Float here.
                            bundle.putFloat(key, obj.toFloat())
                        }
                        is CharSequence -> {
                            bundle.putString(key, obj as String)
                        }
                    }
                } catch (e: JSONException) {
                    Log.w(TAG, "JSONObject get fail for $key")
                }
            }
            return bundle
        }

        /**
         * Convert a Bundle to JSONObject.
         *
         * @param bundle a Bundle to convert
         * @return a JsonObject object
         */
        private fun bundleToJson(bundle: Bundle): JSONObject {
            val jsonObj = JSONObject()
            for (key in bundle.keySet()) {
                try {
                    jsonObj.put(key, bundle[key])
                } catch (e: JSONException) {
                    if (DEBUG) {
                        e.printStackTrace()
                    }
                }
            }

            return jsonObj
        }
    }

    private val preferences: SharedPreferences
    private val wallpaperConfigName: String
    private val lock = Any()

    @GuardedBy("lock")
    private var bundle = Bundle()

    /**
     * Create an instance with a config name, the name should be unique among all wallpapers.
     */
    init {
        // Use createDeviceProtectedStorageContext to support direct boot.
        // The shared preference file will be saved under:
        // /data/user_de/0/com.google.pixel.livewallpapers/shared_prefs
        val secureContext = context.createDeviceProtectedStorageContext()
        preferences = secureContext.getSharedPreferences(PREF_FILENAME, Context.MODE_MULTI_PROCESS)
        wallpaperConfigName = name
    }

    /**
     * Save the bundle keys and values that have put in this object.
     *
     * @param isPreview Select save keys and values as normal set or preview set.
     */
    fun save(isPreview: Boolean) {
        lateinit var jsonObj: JSONObject
        synchronized(lock) { jsonObj = bundleToJson(bundle) }
        val editor = preferences.edit()
        val keySuffix = if (isPreview) SUFFIX_PREVIEW else ""
        editor.putString(wallpaperConfigName + keySuffix, jsonObj.toString())
        editor.apply()
    }

    /**
     * Load the bundle keys and values that stored in shared preferences file.
     *
     * @param isPreview Select load keys and values from normal set or preview set.
     */
    fun load(isPreview: Boolean) {
        val keySuffix = if (isPreview) SUFFIX_PREVIEW else ""
        val jsonStr = preferences.getString(wallpaperConfigName + keySuffix, "")
        try {
            val json = JSONObject(jsonStr)
            synchronized(lock) { bundle = jsonToBundle(json) }
        } catch (e: JSONException) {
            if (DEBUG) {
                Log.w(TAG, "JSONObject creation failed for \n$jsonStr")
                e.printStackTrace()
            }
        }
    }

    /**
     * Inserts a boolean value into the bundle in CustomizedSharedPreferences, replacing any
     * existing value for the given key.
     *
     * @param key   a String key
     * @param value a boolean
     */
    fun putBoolean(key: String, value: Boolean) {
        synchronized(lock) { bundle.putBoolean(key, value) }
    }

    /**
     * Inserts an int value into the bundle in CustomizedSharedPreferences, replacing any existing
     * value for the given key.
     *
     * @param key   a String key
     * @param value an int
     */
    fun putInt(key: String, value: Int) {
        synchronized(lock) { bundle.putInt(key, value) }
    }

    /**
     * Inserts a float value into the bundle in CustomizedSharedPreferences, replacing any existing
     * value for the given key.
     *
     * @param key   a String key
     * @param value a float
     */
    fun putFloat(key: String, value: Float) {
        synchronized(lock) { bundle.putFloat(key, value) }
    }

    /**
     * Inserts a String value into the bundle in CustomizedSharedPreferences, replacing any existing
     * value for the given key.
     *
     * @param key   a String key
     * @param value a String
     */
    fun putString(key: String, value: String) {
        synchronized(lock) { bundle.putString(key, value) }
    }

    /**
     * Return the value associated with the given key from currently loaded shared preference set,
     * or defaultValue if no mapping of the desired value of type Boolean exists for the given key.
     *
     * @param key          a String key
     * @param defaultValue Value to return if key does not exist
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        synchronized(lock) { return bundle.getBoolean(key, defaultValue) }
    }

    /**
     * Return the value associated with the given key from currently loaded shared preference set,
     * or defaultValue if no mapping of the desired value of type Int exists for the given key.
     *
     * @param key          a String key
     * @param defaultValue Value to return if key does not exist
     */
    fun getInt(key: String, defaultValue: Int): Int {
        synchronized(lock) { return bundle.getInt(key, defaultValue) }
    }

    /**
     * Return the value associated with the given key from currently loaded shared preference set,
     * or defaultValue if no mapping of the desired value of type Float exists for the given key.
     *
     * @param key          a String key
     * @param defaultValue Value to return if key does not exist
     */
    fun getFloat(key: String, defaultValue: Float): Float {
        synchronized(lock) { return bundle.getFloat(key, defaultValue) }
    }

    /**
     * Return the value associated with the given key from currently loaded shared preference set,
     * or defaultValue if no mapping of the desired value of type String exists for the given key.
     *
     * @param key          a String key
     * @param defaultValue Value to return if key does not exist
     */
    fun getString(key: String, defaultValue: String?): String {
        synchronized(lock) { return bundle.getString(key, defaultValue) }
    }

    /**
     * Remove any entry with the given key.
     *
     * @param key a String key
     */
    fun remove(key: String) {
        synchronized(lock) { bundle.remove(key) }
    }

    fun register(listener: OnSharedPreferenceChangeListener?) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }
}
