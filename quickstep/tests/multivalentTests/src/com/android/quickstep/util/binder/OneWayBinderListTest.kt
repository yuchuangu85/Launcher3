/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.quickstep.util.binder

import android.os.RemoteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OneWayBinderListTest {

    @Test
    fun registerListenersCalled() {
        var registered = 0
        var unregistered = 0
        val target =
            OneWayBinderList(
                mapper = IBinderListTestListener.Stub::asInterface,
                onFirstRegister = { registered++ },
                onLastUnregister = { unregistered++ },
            )

        assertThat(registered).isEqualTo(0)
        assertThat(unregistered).isEqualTo(0)

        val c1 = target.register(SimpleListener())
        assertThat(registered).isEqualTo(1)

        val c2 = target.register(SimpleListener())
        assertThat(registered).isEqualTo(1)

        c1.close()
        assertThat(registered).isEqualTo(1)
        assertThat(unregistered).isEqualTo(0)

        c2.close()
        assertThat(registered).isEqualTo(1)
        assertThat(unregistered).isEqualTo(1)
    }

    @Test
    fun asyncMethodDispatchedToAll() {
        val target = OneWayBinderList(IBinderListTestListener.Stub::asInterface)

        val l1 = SimpleListener()
        val l2 = SimpleListener()

        val c1 = target.register(l1)
        val c2 = target.register(l2)

        target.iInterface.firstOneWayMethod(0, true)
        target.iInterface.firstOneWayMethod(1, false)

        assertThat(l1.firstMethodCalls).containsExactly(0 to true, 1 to false)
        assertThat(l2.firstMethodCalls).containsExactly(0 to true, 1 to false)

        target.iInterface.secondOneWayMethod()
        c2.close()
        target.iInterface.secondOneWayMethod()
        target.iInterface.secondOneWayMethod()
        c1.close()
        target.iInterface.secondOneWayMethod()

        assertThat(l1.secondMethodCallCount).isEqualTo(3)
        assertThat(l2.secondMethodCallCount).isEqualTo(1)
    }

    @Test(expected = RemoteException::class)
    fun syncMethodFails() {
        val target = OneWayBinderList(IBinderListTestListener.Stub::asInterface)
        target.register(SimpleListener())

        target.iInterface.syncMethod()
    }

    class SimpleListener : IBinderListTestListener.Stub() {

        val firstMethodCalls = mutableListOf<Pair<Int, Boolean>>()

        var secondMethodCallCount = 0

        override fun firstOneWayMethod(intParam: Int, boolParams: Boolean) {
            firstMethodCalls.add(intParam to boolParams)
        }

        override fun secondOneWayMethod() {
            secondMethodCallCount++
        }

        override fun syncMethod(): Boolean = false
    }
}
