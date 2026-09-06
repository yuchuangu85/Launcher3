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

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference

object TypeUtils {

    fun KSTypeReference.fullDeclaration(): String = resolve().fullDeclaration()

    fun KSType.fullDeclaration(): String {
        val declaration = declaration
        val baseName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()

        // Get the type arguments (the <T, R> part)
        val args = arguments

        return if (args.isEmpty()) {
            baseName
        } else {
            val resolvedArgs =
                args.joinToString(", ") { arg ->
                    when (val argType = arg.type) {
                        null -> "*"
                        else -> argType.fullDeclaration()
                    }
                }
            "$baseName<$resolvedArgs>"
        }
    }

    fun KSAnnotation.className() = annotationType.resolve().fullDeclaration()

    /** Renders values inside an annotation */
    private fun renderValue(value: Any?): String {
        return when (value) {
            is String -> "\"$value\""
            is Boolean,
            is Int,
            is Long,
            is Float,
            is Double -> value.toString()
            is KSType -> "${value.fullDeclaration()}::class"

            // Enum
            is KSDeclaration -> value.qualifiedName?.asString() ?: value.simpleName.asString()

            // Handle Lists (Recursive call for each element)
            is List<*> ->
                value.joinToString(prefix = "[", postfix = "]") { item -> renderValue(item) }

            // Handle nested annotations (e.g., @Outer(inner = @Inner))
            is KSAnnotation -> value.fullDeclaration()

            else -> value.toString()
        }
    }

    /** Creates a full string declaration of the annotation */
    fun KSAnnotation.fullDeclaration(): String {
        val name = className()
        val args =
            (arguments - defaultArguments).joinToString(", ") { arg ->
                val argName = arg.name?.asString()
                val argVal = renderValue(arg.value)
                if (argName != null) "$argName = $argVal" else argVal
            }
        return if (args.isEmpty()) "@$name" else "@$name($args)"
    }
}
