/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.util

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import android.util.Xml
import androidx.annotation.WorkerThread
import com.android.launcher3.util.XmlElement.Companion.getRootElement
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import org.xmlpull.v1.XmlSerializer

/** Utility class to store a list of items on disk */
class ListStore<T : Any>(private val fileName: String) {

    /** Writes the provided list of items on the disk */
    @WorkerThread
    fun write(context: Context, items: List<T>, writer: (XmlSerializer, T) -> Unit) {
        val file: AtomicFile = getFile(context)
        val fos: FileOutputStream
        try {
            fos = file.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to persist items in $fileName.xml", e)
            return
        }

        try {
            val out = Xml.newSerializer()
            out.setOutput(fos, StandardCharsets.UTF_8.name())
            out.startDocument(null, true)
            out.startTag(null, TAG_ROOT)
            for (item in items) {
                out.startTag(null, TAG_ENTRY)
                writer.invoke(out, item)
                out.endTag(null, TAG_ENTRY)
            }
            out.endTag(null, TAG_ROOT)
            out.endDocument()
        } catch (e: IOException) {
            file.failWrite(fos)
            Log.e(TAG, "Unable to persist items in $fileName.xml", e)
            return
        }

        file.finishWrite(fos)
    }

    /** Reads the items from the disk */
    @WorkerThread
    fun read(context: Context, factory: (XmlElement) -> T?): MutableList<T> =
        mutableListOf<T>().apply {
            try {
                getFile(context).openRead().use { fis ->
                    Xml.newPullParser()
                        .apply { setInput(InputStreamReader(fis, StandardCharsets.UTF_8)) }
                        .getRootElement(TAG_ROOT)
                        .children(TAG_ENTRY)
                        .forEach { element ->
                            runCatching { factory.invoke(element)?.let { add(it) } }
                                .onFailure { Log.e(TAG, "Skipped reading item", it) }
                        }
                }
            } catch (e: FileNotFoundException) {
                // Ignore
            } catch (e: Exception) {
                Log.e(TAG, "Unable to read items in $fileName.xml", e)
            }
        }

    /** Returns the underlying file used for persisting data */
    fun getFile(context: Context): AtomicFile {
        return AtomicFile(context.getFileStreamPath("$fileName.xml"))
    }

    companion object {
        private const val TAG = "ListStore"

        private const val TAG_ROOT = "items"
        private const val TAG_ENTRY = "entry"
    }
}
