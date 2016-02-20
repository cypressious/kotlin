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
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.ui.LightFilePointer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.spring.SpringBundle
import com.intellij.spring.facet.SpringFacet
import com.intellij.spring.facet.SpringFileSet
import com.intellij.spring.facet.SpringFileSetImpl
import com.intellij.spring.facet.SpringFileSetService
import com.intellij.spring.facet.editor.FileSetEditor
import com.intellij.spring.facet.searchers.CodeConfigSearcher
import com.intellij.spring.facet.searchers.XmlConfigSearcher
import com.intellij.spring.statistics.SpringStatisticsConstants
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LayeredIcon
import com.intellij.util.Consumer
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import java.util.*
import javax.swing.Icon

// todo: Reuse com.intellij.spring.model.highlighting.config.ConfigureFileSetFix
internal class ConfigureFileSetFix(protected val myModule: Module,
                                   private val myVirtualFile: VirtualFile) : LocalQuickFix {

    override fun getName() = SpringBundle.message("spring.facet.inspection.configure.context.for.file")

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        doFix()

        DomElementAnnotationsManager.getInstance(project).dropAnnotationsCache()
        DaemonCodeAnalyzer.getInstance(project).restart()
        EditorNotifications.getInstance(project).updateAllNotifications()

        runWriteAction { ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true) }
    }

    private fun doFix() {
        UsageTrigger.trigger(SpringStatisticsConstants.USAGE_TRIGGER_PREFIX + "SpringFacetInspection.ConfigureFileSetFix")

        val fileSetsInMultipleModules = Ref.create(java.lang.Boolean.FALSE)
        val sets = LinkedHashSet<SpringFileSet>()
        ModuleUtilCore.visitMeAndDependentModules(myModule, object : ModuleUtilCore.ModuleVisitor {
            override fun visit(module: Module): Boolean {
                val facet = SpringFacet.getInstance(module)
                if (facet != null) {
                    for (set in SpringFileSetService.getInstance().getAllSets(facet)) {
                        if (!set.isAutodetected) {
                            sets.add(set)

                            if (myModule != module) {
                                fileSetsInMultipleModules.set(java.lang.Boolean.TRUE)
                            }
                        }
                    }
                }
                return true
            }
        })

        val list = ArrayList(sets)
        val facet = SpringFacet.getInstance(myModule)
        val fakeNewSet = if (facet != null) {
            object : SpringFileSetImpl(
                    SpringFileSetService.getInstance().getUniqueId(sets),
                    SpringBundle.message("spring.facet.inspection.context.create"),
                    facet
            ) {
                override fun isNew() = true
                override fun createVirtualFilePointer(url: String) = LightFilePointer(url)
            }.apply { list += this }
        }
        else null

        val step = object : BaseListPopupStep<SpringFileSet>(SpringBundle.message("spring.facet.inspection.context.choose"), list) {
            override fun onChosen(selectedValue: SpringFileSet?, finalChoice: Boolean): PopupStep<Any>? {
                return doFinalStep { this@ConfigureFileSetFix.onChosen(selectedValue, fakeNewSet, sets, facet) }
            }

            override fun getTextFor(fileSet: SpringFileSet): String {
                if (isFakeNewSet(fileSet) || !fileSetsInMultipleModules.get()) {
                    return fileSet.name
                }

                val filesetModule = fileSet.facet.module
                return fileSet.name + " [" + filesetModule.name + "]"
            }

            override fun getIconFor(fileSet: SpringFileSet): Icon? {
                if (isFakeNewSet(fileSet)) {
                    return LayeredIcon.create(fileSet.icon, AllIcons.Actions.New)
                }
                return fileSet.icon
            }

            override fun getSeparatorAbove(fileSet: SpringFileSet): ListSeparator? {
                if (isFakeNewSet(fileSet)) {
                    return ListSeparator()
                }
                return null
            }

            override fun isSpeedSearchEnabled(): Boolean {
                return true
            }

            private fun isFakeNewSet(fileSet: SpringFileSet): Boolean {
                return fileSet == fakeNewSet
            }
        }

        if (ApplicationManager.getApplication().isUnitTestMode) {
            assert(fakeNewSet != null)
            onChosen(list[0], fakeNewSet, sets, facet)
            return
        }

        val popup = JBPopupFactory.getInstance().createListPopup(step)
        DataManager.getInstance().dataContextFromFocus.doWhenDone(Consumer { popup.showInBestPositionFor(it) })
    }

    private fun onChosen(selectedValue: SpringFileSet?,
                         fakeNewSet: SpringFileSet?,
                         existingSets: Set<SpringFileSet>,
                         facet: SpringFacet?) {
        if (selectedValue == null) return

        if (selectedValue == fakeNewSet) {
            editNewSet(facet!!, existingSets, fakeNewSet)
            return
        }

        selectedValue.addFile(myVirtualFile)
    }

    private fun editNewSet(facet: SpringFacet,
                           sets: Set<SpringFileSet>,
                           fakeNewSet: SpringFileSet) {
        val name = SpringFileSetService.getInstance().getUniqueName(SpringBundle.message("facet.context.default.name"), sets)
        fakeNewSet.name = name
        fakeNewSet.addFile(myVirtualFile)

        if (DumbService.isDumb(myModule.project) || ApplicationManager.getApplication().isUnitTestMode) {
            addNewSet(facet, fakeNewSet)
            return
        }

        val editor = FileSetEditor(myModule,
                                   fakeNewSet,
                                   XmlConfigSearcher(this.myModule, false),
                                   CodeConfigSearcher(this.myModule, false))
        if (editor.showAndGet()) {
            addNewSet(facet, editor.editedFileSet)
        }
        else {
            Disposer.dispose(fakeNewSet)
        }
    }

    private fun addNewSet(facet: SpringFacet, fileSet: SpringFileSet) {
        facet.addFileSet(fileSet)
        facet.configuration.setModified()
    }
}