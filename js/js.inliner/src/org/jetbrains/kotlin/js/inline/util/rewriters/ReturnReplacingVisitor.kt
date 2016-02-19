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

package org.jetbrains.kotlin.js.inline.util.rewriters

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.returnTarget
import org.jetbrains.kotlin.js.inline.context.InliningContext
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils

class ReturnReplacingVisitor(private val inliningContext: InliningContext) : JsVisitorWithContextImpl() {

    /**
     * Prevents replacing returns in object literal
     */
    override fun visit(x: JsObjectLiteral, ctx: JsContext<JsNode>): Boolean = false

    /**
     * Prevents replacing returns in inner function
     */
    override fun visit(x: JsFunction, ctx: JsContext<JsNode>): Boolean = false

    override fun endVisit(x: JsReturn, ctx: JsContext<JsNode>) {
        val blockInfo = inliningContext.blocks[x.returnTarget] ?: return

        ctx.removeMe()
        val returnReplacement = getReturnReplacement(blockInfo, x.expression)
        if (returnReplacement != null) {
            ctx.addNext(JsExpressionStatement(returnReplacement))
        }

        var labelName = blockInfo.label
        if (labelName == null) {
            labelName = blockInfo.namingContext.getFreshName(blockInfo.labelPrefix + "\$break")
            blockInfo.label = labelName
        }
        ctx.addNext(JsBreak(labelName.makeRef()))
    }

    private fun getReturnReplacement(blockInfo: InliningContext.BlockInfo, returnExpression: JsExpression?): JsExpression? {
        if (returnExpression == null) return returnExpression

        if (blockInfo.result == null) {
            val name = blockInfo.namingContext.getFreshName(blockInfo.labelPrefix)
            blockInfo.result = name.makeRef()
            blockInfo.namingContext.newVar(name)
        }
        blockInfo.returnCount++
        return JsAstUtils.assignment(blockInfo.result!!, returnExpression);
    }
}
