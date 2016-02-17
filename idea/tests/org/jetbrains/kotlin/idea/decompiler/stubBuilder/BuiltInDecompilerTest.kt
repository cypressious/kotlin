/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.decompiler.stubBuilder

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.builtins.BuiltInSerializerProtocol
import org.jetbrains.kotlin.idea.decompiler.builtIns.KotlinBuiltInStubBuilder
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.stubs.elements.KtFileStubBuilder
import org.junit.Assert

class BuiltInDecompilerTest : LightCodeInsightFixtureTestCase() {
    fun testBuiltInStubTreeEqualToStubTreeFromDecompiledText() {
        doTest("kotlin")
        doTest("kotlin.collections")
    }

    private fun doTest(packageFqName: String) {
        val dirInRuntime = findDir(packageFqName)
        val kotlinBuiltInsVirtualFile = dirInRuntime.children.single { it.extension == BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION }
        val stubTreeFromDecompiler = KotlinBuiltInStubBuilder().buildFileStub(FileContentImpl.createByFile(kotlinBuiltInsVirtualFile))!!
        myFixture.configureFromExistingVirtualFile(kotlinBuiltInsVirtualFile)

        val stubTreeFromDecompiledText = KtFileStubBuilder().buildStubTree(myFixture.file)
        val expectedText = stubTreeFromDecompiledText.serializeToString()
        Assert.assertEquals("Stub mismatch for package $packageFqName", expectedText, stubTreeFromDecompiler.serializeToString())
    }

    private fun findDir(packageFqName: String): VirtualFile {
        val classNameIndex = KotlinFullClassNameIndex.getInstance()
        val randomClassInPackage = classNameIndex.getAllKeys(project).first {
            it.startsWith(packageFqName + ".") && "." !in it.substringAfter(packageFqName + ".")
        }
        val classes = classNameIndex.get(randomClassInPackage, project, GlobalSearchScope.allScope(project))
        return classes.first().containingFile.virtualFile.parent
    }

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}
