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

import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.COMPONENT_MODULES
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.INSTALL_MODULES
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.MODULE_INCLUDES
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.MODULE_SUBCOMPONENTS
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.TARGET_MODULE
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.UNBIND_VALUES
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.UNINSTALL_MODULES
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.find
import com.android.tools.dagger.mutation.AnnotationListValueRef.Companion.findClassList
import com.android.tools.dagger.mutation.AnnotationRef.BINDS
import com.android.tools.dagger.mutation.AnnotationRef.BINDS_OPTIONAL
import com.android.tools.dagger.mutation.AnnotationRef.BIND_VALUE
import com.android.tools.dagger.mutation.AnnotationRef.COMPONENT
import com.android.tools.dagger.mutation.AnnotationRef.COMPONENT_BUILDER
import com.android.tools.dagger.mutation.AnnotationRef.MULTI_BINDS
import com.android.tools.dagger.mutation.AnnotationRef.PROVIDES
import com.android.tools.dagger.mutation.TypeUtils.className
import com.android.tools.dagger.mutation.TypeUtils.fullDeclaration
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import java.lang.IllegalArgumentException

/**
 * Class responsible for the final code generation. The process for generation is described at
 * go/mutabledagger.
 *
 * This class generated all the three components: Unified module, Modified component and Helper
 * extension, and publishes them in a single kotlin file
 */
class MutatedComponentGenerator(private val targetClass: KSClassDeclaration) {

    private val packageName = targetClass.packageName.asString()
    private val unifiedModuleName = "${targetClass.simpleName.asString()}_UnifiedModule"
    private val modifiedComponentName = "${targetClass.simpleName.asString()}_ModifiedComponent"

    // Find the component target
    private val targetComponent =
        targetClass.find<KSType>(TARGET_MODULE)!!.declaration as KSClassDeclaration
    private val installModules = targetClass.findClassList(INSTALL_MODULES) ?: emptyList()
    private val uninstallModules = targetClass.findClassList(UNINSTALL_MODULES) ?: emptyList()

    private val bindValues =
        targetClass
            .getDeclaredProperties()
            .filter { it.annotations.any(BIND_VALUE.matcher()) }
            .map { it.asValueBinding() }

    private val unbindValues = targetClass.findClassList(UNBIND_VALUES) ?: emptyList()

    // Collect all modules defined in the [targetComponent] recursively, skipping any
    // [uninstallModules]
    private val allModulesToUnify = buildSet {
        fun read(declaration: KSClassDeclaration) {
            // Find the @Component or @Module annotation
            val moduleList: List<KSClassDeclaration> =
                declaration.findClassList(COMPONENT_MODULES)
                    ?: declaration.findClassList(MODULE_INCLUDES)
                    ?: return
            moduleList.forEach {
                if (!uninstallModules.contains(it) && add(it)) {
                    read(it)
                }
            }
        }

        read(targetComponent)
    }

    private fun KSFunctionDeclaration.shouldSkip(): Boolean {
        val returnType = returnType?.resolve() ?: return true
        if (unbindValues.contains(returnType.declaration)) return true
        return bindValues.any { it.matches(returnType, this) }
    }

    // Collect all binds and provides methods to be written in out unified module skipping any
    // function marked for unbindValues
    private val allModuleMethods =
        allModulesToUnify
            .flatMap { module ->
                // Get all functions and companion object functions
                module.getAllFunctions() +
                    module.declarations
                        .filterIsInstance<KSClassDeclaration>()
                        .filter { it.isCompanionObject }
                        .flatMap { it.getAllFunctions() }
            }
            .groupBy { func ->
                val annotationNames = func.annotations.map { it.className() }
                when {
                    // BindsOptionalOf are not skipped
                    annotationNames.contains(BINDS_OPTIONAL.className) -> BINDS_FUNCTIONS
                    annotationNames.contains(MULTI_BINDS.className) -> BINDS_FUNCTIONS
                    func.shouldSkip() -> SKIPPED_FUNCTIONS
                    annotationNames.contains(BINDS.className) -> BINDS_FUNCTIONS
                    annotationNames.contains(PROVIDES.className) -> PROVIDES_FUNCTIONS
                    else -> SKIPPED_FUNCTIONS
                }
            }

    private fun CodeWriter.writeUnifiedModule() {
        // Print module tag while preserving all subcomponents
        write(
            MODULE_SUBCOMPONENTS,
            allModulesToUnify
                .mapNotNull { it.find<List<KSType>>(MODULE_SUBCOMPONENTS) }
                .flatten()
                .map { it.fullDeclaration() },
        )
        writeBlock("interface $unifiedModuleName") {
            // 1. Render @Binds methods (Abstract)
            allModuleMethods[BINDS_FUNCTIONS]?.forEach { func ->
                val returnType = func.returnType?.resolve()?.fullDeclaration() ?: "Any"
                write(func) { ": $returnType\n" }
            }

            writeBlock("companion object") {
                allModuleMethods[PROVIDES_FUNCTIONS]?.forEach { func ->
                    write(func) { paramNames ->
                        // We need the parent module name to call it: ModuleName.providesMethod()
                        val parentModule =
                            (func.parentDeclaration as? KSClassDeclaration)
                                ?.qualifiedName
                                ?.asString()
                        val funcName = func.simpleName.asString()
                        "= $parentModule.$funcName(${paramNames.joinToString()})\n"
                    }
                }
            }
        }
    }

    private fun CodeWriter.writeUnifiedComponent() {
        // Write all annotations except the component declaration
        targetComponent.annotations.filterNot(COMPONENT.matcher()).forEach {
            write(it.fullDeclaration())
        }
        val addedModules =
            installModules.mapNotNull { it.qualifiedName?.asString() } + unifiedModuleName
        write(COMPONENT_MODULES, addedModules)
        writeBlock(
            "interface $modifiedComponentName : ${targetComponent.qualifiedName?.asString()}"
        ) {
            // Write the custom builder
            val builder =
                targetComponent.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.annotations.any(COMPONENT_BUILDER.matcher()) }
                    .firstOrNull()
            if (builder == null) {
                throw IllegalArgumentException(
                    "Target component ${targetComponent.qualifiedName?.asString()} must have a $COMPONENT_BUILDER defined"
                )
            }
            write("$COMPONENT_BUILDER")
            writeBlock("interface Builder : ${builder.qualifiedName?.asString()}") {
                // Write all bind value methods
                bindValues.forEach { write("$it: Builder\n") }
            }
        }
    }

    private fun CodeWriter.writeHelperExtension() {
        write("fun ${targetClass.simpleName.asString()}.mutatedComponentBuilder() =")
        indent {
            write("Dagger$modifiedComponentName.builder()")
            indent { bindValues.forEach { write(".bind_${it.name}(${it.name})") } }
        }
    }

    fun generate(codeGenerator: CodeGenerator) {
        // Create the complete code first in case there are any processing errors
        val writer =
            CodeWriter().apply {
                write("package $packageName\n")

                writeUnifiedModule()
                writeUnifiedComponent()
                writeHelperExtension()
            }
        codeGenerator
            .createNewFile(
                Dependencies(false, targetClass.containingFile!!),
                packageName,
                modifiedComponentName,
            )
            .use { it.write(writer.toString().toByteArray()) }
    }

    companion object {
        private const val SKIPPED_FUNCTIONS = "skipped-functions"
        private const val BINDS_FUNCTIONS = "binds-functions"
        private const val PROVIDES_FUNCTIONS = "provides-functions"
    }
}
