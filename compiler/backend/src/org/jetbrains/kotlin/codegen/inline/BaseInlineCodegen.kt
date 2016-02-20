/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.inline

import org.jetbrains.kotlin.codegen.CallGenerator
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.OwnerKind
import org.jetbrains.kotlin.codegen.context.CodegenContext
import org.jetbrains.kotlin.codegen.context.PackageContext
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.inline.InlineUtil

abstract class BaseInlineCodegen(
        function: FunctionDescriptor,
        @JvmField protected val codegen: ExpressionCodegen,
        @JvmField val state: GenerationState
) : CallGenerator() {

    @JvmField
    val functionDescriptor =
            if (InlineUtil.isArrayConstructorWithLambda(function))
                FictitiousArrayConstructor.create(function as ConstructorDescriptor)
            else
                function.original

    @JvmField
    val typeMapper = state.typeMapper

    @JvmField
    val context = getContext(
            functionDescriptor, state,
            DescriptorToSourceUtils.descriptorToDeclaration(functionDescriptor)?.containingFile as? KtFile
    )

    @JvmField
    val jvmSignature = typeMapper.mapSignature(functionDescriptor, context.contextKind)

    init {
        assert(InlineUtil.isInline(function) || InlineUtil.isArrayConstructorWithLambda(function)) {
            "InlineCodegen can inline only inline functions and array constructors: " + function
        }

        if (functionDescriptor !is FictitiousArrayConstructor) {
            reportIncrementalInfo(functionDescriptor, codegen.context.functionDescriptor.original)
        }
    }


    private fun reportIncrementalInfo(
            sourceDescriptor: FunctionDescriptor,
            targetDescriptor: FunctionDescriptor) {
        val incrementalCompilationComponents = state.incrementalCompilationComponents
        val targetId = state.targetId

        if (incrementalCompilationComponents == null || targetId == null) return

        val incrementalCache = incrementalCompilationComponents.getIncrementalCache(targetId)
        val classFilePath = sourceDescriptor.getClassFilePath(typeMapper, incrementalCache)
        val sourceFilePath = targetDescriptor.sourceFilePath
        incrementalCache.registerInline(classFilePath, jvmSignature.toString(), sourceFilePath)
    }

    companion object {
        @JvmStatic
        protected fun getContext(descriptor: DeclarationDescriptor, state: GenerationState, sourceFile: KtFile?): CodegenContext<*> {
            if (descriptor is PackageFragmentDescriptor) {
                return PackageContext(descriptor, state.rootContext, null, sourceFile)
            }

            val parent = getContext(descriptor.containingDeclaration!!, state, sourceFile)

            if (descriptor is ScriptDescriptor) {
                val earlierScripts = state.replSpecific.earlierScriptsForReplInterpreter
                return parent.intoScript(descriptor,
                                         earlierScripts ?: emptyList<ScriptDescriptor>(),
                                         descriptor as ClassDescriptor, state.typeMapper)
            }
            else if (descriptor is ClassDescriptor) {
                val kind = if (DescriptorUtils.isInterface(descriptor)) OwnerKind.DEFAULT_IMPLS else OwnerKind.IMPLEMENTATION
                return parent.intoClass(descriptor, kind, state)
            }
            else if (descriptor is FunctionDescriptor) {
                return parent.intoFunction(descriptor)
            }

            throw IllegalStateException("Couldn't build context for " + descriptorName(descriptor))
        }

        @JvmStatic
        protected fun descriptorName(descriptor: DeclarationDescriptor): String {
            return DescriptorRenderer.SHORT_NAMES_IN_TYPES.render(descriptor)
        }
    }

}
