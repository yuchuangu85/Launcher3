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

import com.android.tools.dagger.mutation.AnnotationRef.BINDS_INSTANCE
import com.android.tools.dagger.mutation.AnnotationRef.QUALIFIER
import com.android.tools.dagger.mutation.TypeUtils.fullDeclaration
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

/** Represents a single @BindView entry */
data class ValueBinding(val name: String, val type: KSType, val qualifier: String) {

    fun matches(type: KSType, annotations: KSAnnotated): Boolean {
        if (type != this.type) return false
        if (qualifier.isBlank()) return true
        return annotations.annotations.any { it.fullDeclaration() == qualifier }
    }

    override fun toString() =
        "$BINDS_INSTANCE fun bind_$name($qualifier impl: ${type.fullDeclaration()})"
}

/** Returns true if the annotation's definition has "@Qualifier" annotation */
private fun KSAnnotation.isQualifier(): Boolean {
    val declaration = annotationType.resolve().declaration as? KSClassDeclaration
    return declaration?.annotations?.any(QUALIFIER.matcher()) ?: false
}

fun KSPropertyDeclaration.asValueBinding() =
    ValueBinding(
        name = simpleName.asString(),
        type = type.resolve(),
        qualifier =
            annotations.firstOrNull { annotation -> annotation.isQualifier() }?.fullDeclaration()
                ?: "",
    )
