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
import android.content.res.TypedArray
import android.util.Xml
import java.io.IOException
import kotlin.IntArray
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParserException

/**
 * Utility class for simplify xml parsing.
 *
 * TODO: Can be converted into inline value class once all the dependencies have migrated to kotlin
 */
class XmlElement(private val parser: XmlPullParser) {

    val name: String
        get() = parser.name

    /** Obtains styled attributes corresponding to [attrs] */
    fun obtainAttrs(context: Context, attrs: IntArray): TypedArray =
        context.obtainStyledAttributes(Xml.asAttributeSet(parser), attrs)

    /** Returns a sequence of all child elements */
    fun children(): Sequence<XmlElement> = children { true }

    /** Returns a sequence of all child elements matching [tagName] */
    fun children(tagName: String): Sequence<XmlElement> = children { tagName == it.name }

    private inline fun children(crossinline filter: (XmlPullParser) -> Boolean) = sequence {
        val depth = parser.depth
        var type: Int
        while (
            (parser.next().also { type = it } != END_TAG || parser.depth > depth) &&
                type != END_DOCUMENT
        ) {
            if (type == START_TAG && filter.invoke(parser)) yield(XmlElement(parser))
        }
    }

    /**
     * Return attribute value, attempting launcher-specific namespace first before falling back to
     * anonymous attribute.
     */
    operator fun get(attr: String): String? =
        parser.getAttributeValue(
            "http://schemas.android.com/apk/res-auto/com.android.launcher3",
            attr,
        ) ?: parser.getAttributeValue(null, attr)

    @Throws(XmlPullParserException::class)
    fun getAsInt(attr: String): Int =
        get(attr)?.toInt() ?: throw XmlPullParserException("Missing attribute $attr")

    /**
     * Return attribute resource value, attempting launcher-specific namespace first before falling
     * back to anonymous attribute.
     */
    fun getResource(attr: String, defaultValue: Int): Int {
        val attrs = Xml.asAttributeSet(parser)
        var value =
            attrs.getAttributeResourceValue(
                "http://schemas.android.com/apk/res-auto/com.android.launcher3",
                attr,
                defaultValue,
            )
        if (value == defaultValue) {
            value = attrs.getAttributeResourceValue(null, attr, defaultValue)
        }
        return value
    }

    @JvmOverloads
    fun childIterator(tagName: String? = null): Iterable<XmlElement> {
        return if (tagName == null) Iterable { children().iterator() }
        else Iterable { children(tagName).iterator() }
    }

    companion object {

        @JvmStatic
        @JvmOverloads
        @Throws(XmlPullParserException::class, IOException::class)
        fun XmlPullParser.getRootElement(firstElement: String? = null): XmlElement {
            var type: Int
            while (next().also { type = it } != START_TAG && type != END_DOCUMENT) {}

            if (type != START_TAG) {
                throw XmlPullParserException("No start tag found")
            }
            if (firstElement != null && name != firstElement) {
                throw XmlPullParserException(
                    "Unexpected start tag: found $name, expected $firstElement"
                )
            }
            return XmlElement(parser = this)
        }
    }
}
