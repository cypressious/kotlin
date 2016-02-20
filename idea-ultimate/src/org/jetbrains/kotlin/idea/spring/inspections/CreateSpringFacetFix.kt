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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.facet.FacetManager
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.spring.SpringBundle
import com.intellij.spring.facet.SpringFacet
import com.intellij.ui.EditorNotifications
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager

// todo: Reuse com.intellij.spring.model.highlighting.config.CreateSpringFacetFix
class CreateSpringFacetFix(private val myModule: Module) : LocalQuickFix {
    override fun getName() = SpringBundle.message("spring.facet.inspection.create.facet")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        UsageTrigger.trigger("spring.SpringFacetInspection.AddFacetFix")
        DomElementAnnotationsManager.getInstance(project).dropAnnotationsCache()
        DaemonCodeAnalyzer.getInstance(project).restart()
        EditorNotifications.getInstance(project).updateAllNotifications()
        if (!ApplicationManager.getApplication().isUnitTestMode) {
            val facet = FacetManager.getInstance(this.myModule).addFacet(SpringFacet.getSpringFacetType(), "Spring", null)
            ModulesConfigurator.showFacetSettingsDialog(facet, null)
        }
    }
}