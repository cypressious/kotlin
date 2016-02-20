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

package org.jetbrains.kotlin.idea.spring.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifier
import com.intellij.spring.SpringBundle
import com.intellij.spring.SpringManager
import com.intellij.spring.facet.SpringFacet
import com.intellij.spring.model.SpringModelSearchParameters
import com.intellij.spring.model.jam.stereotype.SpringConfiguration
import com.intellij.spring.model.utils.SpringCommonUtils
import com.intellij.spring.model.utils.SpringModelSearchers
import com.intellij.spring.model.utils.SpringModelUtils
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid

class KotlinSpringFacetCodeInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object: KtVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                val project = classOrObject.project

                if (!SpringCommonUtils.hasSpringLibrary(project)) return

                val lightClass = classOrObject.toLightClass() ?: return
                if (!SpringConfiguration.PSI_CLASS_PATTERN.accepts(lightClass)) return
                if (lightClass.hasModifierProperty(PsiModifier.STATIC)) return
                if (!SpringCommonUtils.isConfigurationOrMeta(lightClass)) return

                val configurationCheckResult = SpringModelUtils.getInstance().isUsedOrNotConfigurationFile(classOrObject.containingFile, false, false)
                if (!configurationCheckResult.checkResult) return

                val module = configurationCheckResult.myModule
                val virtualFile = configurationCheckResult.myVirtualFile
                val params = SpringModelSearchParameters.byClass(lightClass)
                val springModels = SpringManager.getInstance(project).getAllModels(module)
                if (springModels.any { SpringModelSearchers.doesBeanExist(it, params) }) return

                val fix = if (SpringFacet.getInstance(module) == null) CreateSpringFacetFix(module) else ConfigureFileSetFix(module, virtualFile)
                val descriptor = holder.manager.createProblemDescriptor(
                        classOrObject.nameIdentifier ?: classOrObject,
                        SpringBundle.message("spring.facet.inspection.context.not.configured.for.file"),
                        fix,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        isOnTheFly
                )
                holder.registerProblem(descriptor)
            }
        }
    }
}
