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

package com.android.launcher3.testutil.rule

import kotlin.reflect.KClassifier
import kotlin.reflect.KProperty
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * [TestRule] which evaluates the actual rules during execution to avoid reference being held by the
 * instrumentation runner.
 *
 * This is useful when we want to `spy` the test rule itself as the `spy` gets executed before any
 * rules have been evaluated, and outside the Mockito session.
 */
class LazyInitRule(private val providers: List<TypedProvider<*>>) : TestRule {

    private val valueMap = mutableMapOf<KClassifier, TestRule>()

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val valueList = mutableListOf<TestRule>()

                // Initialize the values in order
                for (i in providers.indices) {
                    val p = providers[i]
                    val rule = p.provider.invoke(this@LazyInitRule)
                    valueMap[p.type] = rule
                    valueList.add(i, rule)
                }

                // Now apply the results in reverse order
                var result = base
                valueList.reversed().forEach { result = it.apply(result, description) }
                result.evaluate()
            }
        }
    }

    fun <T : TestRule> get(clazz: Class<T>) = valueMap[clazz.kotlin] as T

    inline fun <reified T : TestRule> get(): T = get(T::class.java)

    inline operator fun <reified T : TestRule> getValue(thisRef: Any?, property: KProperty<*>): T =
        get()

    class TypedProvider<T : TestRule>(clazz: Class<T>, val provider: (LazyInitRule) -> T) {

        val type: KClassifier = clazz.kotlin
    }

    companion object {

        @JvmStatic
        inline fun <reified T : TestRule> lazyRule(noinline p: (LazyInitRule) -> T) =
            LazyInitRule(listOf(TypedProvider(T::class.java, p)))

        @JvmStatic
        inline fun <reified A : TestRule, reified B : TestRule> lazyRule(
            noinline p1: (LazyInitRule) -> A,
            noinline p2: (LazyInitRule) -> B,
        ) = LazyInitRule(listOf(TypedProvider(A::class.java, p1), TypedProvider(B::class.java, p2)))

        @JvmStatic fun lazyRule(vararg provider: TypedProvider<*>) = LazyInitRule(provider.asList())

        @JvmStatic
        fun <T : TestRule> lazyP(clazz: Class<T>, provider: (LazyInitRule) -> T) =
            TypedProvider(clazz, provider)
    }
}
