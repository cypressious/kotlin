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

package org.jetbrains.kotlin.idea.spring.lineMarking

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.jam.JamService
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Pair
import com.intellij.psi.*
import com.intellij.psi.util.PropertyUtil
import com.intellij.spring.CommonSpringModel
import com.intellij.spring.SpringBundle
import com.intellij.spring.gutter.NavigationGutterIconBuilderUtil
import com.intellij.spring.gutter.SpringBeansPsiElementCellRenderer
import com.intellij.spring.gutter.SpringClassAnnotator
import com.intellij.spring.java.SpringJavaClassInfo
import com.intellij.spring.model.CommonSpringBean
import com.intellij.spring.model.SpringBeanPointer
import com.intellij.spring.model.highlighting.SpringAutowireUtil
import com.intellij.spring.model.highlighting.SpringJavaAutowiringInspection
import com.intellij.spring.model.jam.javaConfig.ContextJavaBean
import com.intellij.spring.model.jam.javaConfig.SpringOldJavaConfigurationUtil
import com.intellij.spring.model.utils.SpringCommonUtils
import com.intellij.util.xml.DomElement
import icons.SpringApiIcons
import org.jetbrains.kotlin.asJava.KtLightClassForExplicitDeclaration
import org.jetbrains.kotlin.asJava.KtLightElement
import org.jetbrains.kotlin.asJava.KtLightMethod
import org.jetbrains.kotlin.asJava.toLightElements
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.util.NotNullLazyValue
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.source.getPsi

class KotlinSpringClassAnnotator : SpringClassAnnotator() {
    private fun PsiAnnotation.findKtAnnotation(declaration: KtNamedDeclaration): KtAnnotationEntry? {
        val qualifiedName = qualifiedName ?: return null
        val annotationDescriptor = (declaration.resolveToDescriptor()).annotations.findAnnotation(FqName(qualifiedName))
                                   ?: return null
        return annotationDescriptor.source.getPsi() as? KtAnnotationEntry
    }

    private fun getStereotypeBean(method: PsiMethod) = JamService.getJamService(method.project).getJamElement(ContextJavaBean.BEAN_JAM_KEY, method)

    private fun addPropertiesGutterIcon(
            result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>,
            elementToAnnotate: PsiElement,
            computeTargets: () -> Collection<DomElement>
    ) {
        result += NavigationGutterIconBuilder
                .create(SpringApiIcons.SpringProperty,
                        NavigationGutterIconBuilder.DEFAULT_DOM_CONVERTOR,
                        NavigationGutterIconBuilder.DOM_GOTO_RELATED_ITEM_PROVIDER)
                .setTargets(NotNullLazyValue(computeTargets))
                .setCellRenderer(SpringBeansPsiElementCellRenderer.INSTANCE)
                .setPopupTitle(SpringBundle.message("spring.bean.property.navigate.choose.class.title"))
                .setTooltipText(SpringBundle.message("spring.bean.property.tooltip.navigate.declaration"))
                .createLineMarkerInfo(elementToAnnotate)
    }

    private fun addStereotypeBeanAutowiredCandidatesBeanGutterIcon(
            result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>,
            method: PsiMethod,
            annotation: KtAnnotationEntry
    ) {
        result += NavigationGutterIconBuilder
                .create(SpringApiIcons.ShowAutowiredDependencies)
                .setPopupTitle(SpringBundle.message("gutter.choose.autowired.candidates.title"))
                .setEmptyPopupText(SpringBundle.message("gutter.navigate.no.matching.autowired.candidates"))
                .setTooltipText(SpringBundle.message("gutter.navigate.to.autowired.candidates.title"))
                .setTargets(
                        NotNullLazyValue<Collection<PsiElement>> {
                            val type = method.returnType ?: return@NotNullLazyValue emptySet()
                            val module = ModuleUtilCore.findModuleForPsiElement(method) ?: return@NotNullLazyValue emptySet()
                            SpringAutowireUtil.getAutowiredMembers(type, module)
                        }
                )
                .createLineMarkerInfo(annotation)
    }

    private fun addMethodTypesGutterIcon(
            result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>,
            elementToAnnotate: PsiElement,
            targets: Collection<Pair<DomElement, SpringJavaClassInfo.SpringMethodType>>
    ) {
        var tooltipText = SpringBundle.message("spring.bean.methods.tooltip.navigate.declaration", *arrayOfNulls<Any>(0))
        var icon = SpringApiIcons.SpringBeanMethod
        targets.singleOrNull()?.let {
            val methodType = it.second
            tooltipText = SpringBundle.message("spring.bean.method.tooltip.navigate.declaration", methodType.getName())
            if (methodType == SpringJavaClassInfo.SpringMethodType.FACTORY) {
                icon = SpringApiIcons.FactoryMethodBean
            }
        }

        result += NavigationGutterIconBuilder
                .create(icon, NavigationGutterIconBuilder.DEFAULT_DOM_CONVERTOR, NavigationGutterIconBuilder.DOM_GOTO_RELATED_ITEM_PROVIDER)
                .setTargets(targets.map { it.first })
                .setCellRenderer(SpringBeansPsiElementCellRenderer.INSTANCE)
                .setPopupTitle(SpringBundle.message("spring.bean.class.navigate.choose.class.title"))
                .setTooltipText(tooltipText)
                .createLineMarkerInfo(elementToAnnotate)
    }

    private fun addSpringBeanGutterIcon(
            result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>,
            elementToAnnotate: PsiElement,
            targets: () -> Collection<SpringBeanPointer<CommonSpringBean>>
    ) {
        result += NavigationGutterIconBuilder
                .create(SpringApiIcons.SpringBean,
                        NavigationGutterIconBuilderUtil.BEAN_POINTER_CONVERTOR,
                        NavigationGutterIconBuilderUtil.BEAN_POINTER_GOTO_PROVIDER)
                .setTargets(NotNullLazyValue(targets))
                .setEmptyPopupText(SpringBundle.message("gutter.navigate.no.matching.beans"))
                .setPopupTitle(SpringBundle.message("spring.bean.class.navigate.choose.class.title"))
                .setCellRenderer(SpringBeansPsiElementCellRenderer.INSTANCE)
                .setTooltipText(SpringBundle.message("spring.bean.class.tooltip.navigate.declaration"))
                .createLineMarkerInfo(elementToAnnotate)
    }

    private fun processAnnotatedMethod(method: PsiMethod, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
        if (!SpringAutowireUtil.isInjectionPoint(method)) return
        val model = SpringAutowireUtil.getProcessingSpringModel(method.containingClass) ?: return

        if (SpringAutowireUtil.getResourceAnnotation(method) != null && PropertyUtil.isSimplePropertySetter(method)) {
            val parameter = method.parameterList.parameters.first()
            processVariable(method, result, model, parameter.type)
        }
        else {
            method.parameterList.parameters.forEach { processVariable(it, result, model, it.type) }
        }

    }

    private fun processVariable(
            variable: PsiModifierListOwner,
            result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>?,
            model: CommonSpringModel,
            type: PsiType
    ) {
        val elementToAnnotate = (variable as? KtLightElement<*, *>)?.getOrigin() as? KtNamedDeclaration ?: return

        val list = SpringJavaAutowiringInspection.checkAutowiredPsiMember(variable, type, null, model, false)
        if (list == null || list.isEmpty()) return

        if (result != null) {
            NavigationGutterIconBuilderUtil.addAutowiredBeansGutterIcon(list, result, elementToAnnotate)
        }
    }

    private fun annotateKotlinLightMethod(
            lightMethod: KtLightMethod,
            result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>
    ) {
        val lightClass = lightMethod.containingClass ?: return
        val declaration = lightMethod.getOrigin() as? KtNamedDeclaration ?: return
        val nameIdentifier = declaration.nameIdentifier ?: return
        if (SpringCommonUtils.isSpringBeanCandidateClassInSpringProject(lightClass)) {
            val springClassInfo = SpringJavaClassInfo.getSpringJavaClassInfo(lightClass)
            var autowired = false
            if (PropertyUtil.isSimplePropertySetter(lightMethod)) {
                if (springClassInfo.isMappedProperty(lightMethod)) {
                    addPropertiesGutterIcon(result, nameIdentifier) {
                        val propertyName = PropertyUtil.getPropertyNameBySetter(lightMethod)
                        SpringJavaClassInfo.getSpringJavaClassInfo(lightClass).getMappedProperties(propertyName)
                    }
                }
                else {
                    autowired = springClassInfo.isAutowired
                    if (autowired) {
                        checkAutowiredMethod(lightMethod, result, springClassInfo)
                    }
                }
            }
            else {
                getStereotypeBean(lightMethod)?.psiAnnotation?.findKtAnnotation(declaration)?.let {
                    addStereotypeBeanAutowiredCandidatesBeanGutterIcon(result, lightMethod, it)
                }

                val externalBeans = SpringOldJavaConfigurationUtil.findExternalBeans(lightMethod)
                if (!externalBeans.isEmpty()) {
                    addSpringBeanGutterIcon(result, nameIdentifier) {
                        SpringOldJavaConfigurationUtil.findExternalBeans(lightMethod)
                                .apply { sortWith(SpringBeanPointer.DISPLAY_COMPARATOR) }
                    }
                }
            }

            if (!autowired) {
                processAnnotatedMethod(lightMethod, result)
            }

            val methodTypes1 = springClassInfo.getMethodTypes(lightMethod)
            if (!methodTypes1.isEmpty()) {
                addMethodTypesGutterIcon(result, lightMethod, methodTypes1)
            }
        }

    }

    override fun getElementToProcess(psiElement: PsiElement): PsiElement? {
        psiElement.getParentOfTypeAndBranch<KtAnnotationEntry> { typeReference }?.let { return it }
        psiElement.getParentOfTypeAndBranch<KtNamedDeclaration> { nameIdentifier }?.let { return it.toLightElements().firstOrNull() }
        return null
    }

    override fun collectNavigationMarkers(psiElement: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
        val element = this.getElementToProcess(psiElement)
        when (element) {
            is KtLightClassForExplicitDeclaration -> super.collectNavigationMarkers(psiElement, result)
            is KtLightMethod -> annotateKotlinLightMethod(element, result)
        }
    }
}