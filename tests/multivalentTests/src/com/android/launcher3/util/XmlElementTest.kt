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

import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.XmlElement.Companion.getRootElement
import com.google.common.truth.Truth.assertThat
import java.io.StringReader
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParserException

@SmallTest
@RunWith(AndroidJUnit4::class)
class XmlElementTest {

    @Test(expected = XmlPullParserException::class)
    fun getRootElement_fails_for_wrong_tag() {
        """
            <hello>
              <one attr="cow" />
            </hello>
        """
            .toRootElement("bye")
    }

    @Test
    fun getRootElement_correct_tag() {
        """
            <hello>
              <one attr="cow" />
            </hello>
        """
            .toRootElement("hello")
    }

    @Test
    fun getRootElement_no_root_tag_specified() {
        """
            <hello>
              <one attr="cow" />
            </hello>
        """
            .toRootElement()
    }

    @Test
    fun getValue() {
        val value =
            """
                <hello key="sum"></hello>
            """
                .toRootElement()["key"]
        assertThat(value).isEqualTo("sum")
    }

    @Test
    fun getValue_as_int() {
        val value =
            """
                <hello key="sum" key2="5"></hello>
            """
                .toRootElement()
                .getAsInt("key2")
        assertThat(value).isEqualTo(5)
    }

    @Test
    fun children_iterates_all() {
        val value =
            """
            <hello>
              <one attr="1" />
              <two attr="2" />
              <one attr="3" />
              <two attr="4" />
              <one attr="5" />
            </hello>
        """
                .toRootElement()
                .children()
                .map { it.getAsInt("attr") }
                .toList()
        assertThat(value).containsExactly(1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun children_filters_tag() {
        val value =
            """
            <hello>
              <one attr="1" />
              <two attr="2" />
              <one attr="3" />
              <two attr="4" />
              <one attr="5" />
            </hello>
        """
                .toRootElement()
                .children("two")
                .map { it.getAsInt("attr") }
                .toList()
        assertThat(value).containsExactly(2, 4).inOrder()
    }

    @Test
    fun children_nested_iteration() {
        val value =
            """
            <hello>
              <one attr="1">
                <l1 attr="1.1" />
                <l1 attr="1.2" />
                <l1 attr="1.3" />
              </one>
              <two attr="2" >
                <l2 attr="2.1" />
                <l2 attr="2.2" />
              </two>
              <one attr="5" />
            </hello>
        """
                .toRootElement()
                .children()
                .flatMap { it.children().map { child -> child["attr"] } }
                .toList()
        assertThat(value).containsExactly("1.1", "1.2", "1.3", "2.1", "2.2").inOrder()
    }

    private fun String.toRootElement(firstElement: String? = null) =
        Xml.newPullParser().let {
            it.setInput(StringReader(this))
            it.getRootElement(firstElement)
        }
}
