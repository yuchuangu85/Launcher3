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

package com.android.tools.dagger.mutation

import com.android.tools.dagger.mutation.TypeUtils.fullDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/** Class for writing the final kotlin code with some helper methods to write particular elements */
class CodeWriter(
    private val output: StringBuilder = StringBuilder(),
    private val usedMethodNames: MutableSet<String> = mutableSetOf(),
    private val indent: String = "",
) {

    /** Writes a single code line with current indentation */
    fun write(line: String) {
        output.append("${indent}$line\n")
    }

    /** Writes the [block] wrapped inside '{ }' block */
    fun writeBlock(header: String, block: CodeWriter.() -> Unit) {
        write("$header {\n")
        indent(block)
        write("}\n")
    }

    /** Writes the [annotation] assuming the [values] point to a list of class definitions */
    fun write(annotation: AnnotationListValueRef, values: List<String>) =
        write(
            annotation.run {
                if (values.isEmpty()) "@$className"
                else
                    values.joinToString(prefix = "@$className($param = [", postfix = "\n])") {
                        "\n$indent    $it::class"
                    }
            }
        )

    /**
     * Writes the [func] while preserving its annotations and any annotations for the parameters.
     *
     * It also picks a new name, if a function with same name is already written before. This is
     * because dagger doesn't support multiple methods with same name in Modules
     */
    fun write(func: KSFunctionDeclaration, code: (params: List<String>) -> String) {
        // Use a unique function name
        val funcNameBase = func.simpleName.asString()
        var funcName = funcNameBase
        var index = 0
        while (usedMethodNames.contains(funcName)) {
            index++
            funcName = funcNameBase + "_$index"
        }
        usedMethodNames.add(funcName)

        // Write annotations
        func.annotations.forEach { write(it.fullDeclaration()) }

        if (func.parameters.isEmpty()) {
            write("fun $funcName() ${code.invoke(emptyList())}")
        } else {
            val paramNames = mutableListOf<String>()
            index = 0
            write("fun $funcName(")
            output.append(
                func.parameters.joinToString(",\n") { param ->
                    val annos = param.annotations.joinToString("") { it.fullDeclaration() + " " }
                    val name = param.name?.asString() ?: "arg$index"
                    index++
                    paramNames.add(name)
                    val type = param.type.fullDeclaration()
                    "$indent    $annos$name: $type"
                }
            )
            output.append(") ${code.invoke(paramNames)}")
        }
    }

    /** Executes the [callback] with an indented [CodeWriter] */
    fun indent(callback: CodeWriter.() -> Unit) {
        CodeWriter(output, usedMethodNames, "    $indent").apply(callback)
    }

    /** The final code printed so far */
    override fun toString() = output.toString()
}
