/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.NOTHING_TO_OVERRIDE
import org.jetbrains.kotlin.idea.refactoring.pullUp.KotlinPullUpHandler
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner

class PullUpFix(element: KtDeclaration) : KotlinQuickFixAction<KtDeclaration>(element) {
    override fun getFamilyName() = "Pull up"
    override fun getText() = "Pull up"

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        KotlinPullUpHandler().invoke(project, arrayOf(element), DataContext.EMPTY_CONTEXT)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtModifierListOwner>? {
            val casted = NOTHING_TO_OVERRIDE.cast(diagnostic)
            val declaration = casted.psiElement as? KtDeclaration ?: return null
            return PullUpFix(declaration)
        }
    }
}