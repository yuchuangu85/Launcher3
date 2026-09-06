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

import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.TARGET_MODULE
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

class MyProcessor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) :
    SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> =
        resolver
            .getSymbolsWithAnnotation(TARGET_MODULE.className)
            .filterIsInstance<KSClassDeclaration>()
            .filter {
                try {
                    MutatedComponentGenerator(it).generate(codeGenerator)
                    logger.info("Generation successful: $it")
                    // Code successfully generated, skip
                    false
                } catch (e: Throwable) {
                    logger.warn("Generation skipped $it, will try again: " + e.message)
                    // Generation failed, try again
                    true
                }
            }
            .toList()
}
