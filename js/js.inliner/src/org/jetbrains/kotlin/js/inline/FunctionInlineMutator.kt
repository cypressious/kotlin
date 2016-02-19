/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.jetbrains.kotlin.js.inline

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.declarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.js.inline.clean.removeDefaultInitializers
import org.jetbrains.kotlin.js.inline.context.InliningContext
import org.jetbrains.kotlin.js.inline.context.NamingContext
import org.jetbrains.kotlin.js.inline.util.*
import org.jetbrains.kotlin.js.inline.util.rewriters.ReturnReplacingVisitor

class FunctionInlineMutator
private constructor(
        private val call: JsInvocation,
        private val inliningContext: InliningContext
) {
    private val invokedFunction: JsFunction
    private val isResultNeeded: Boolean
    private val namingContext: NamingContext
    private val body: JsBlock
    private var resultExpr: JsExpression? = null
    private val currentStatement = inliningContext.statementContext.currentNode
    private val declaration: DeclarationDescriptor?

    init {

        val functionContext = inliningContext.functionContext
        invokedFunction = functionContext.getFunctionDefinition(call)
        body = invokedFunction.body.deepCopy()
        isResultNeeded = isResultNeeded(call)
        namingContext = inliningContext.newNamingContext()
        declaration = invokedFunction.body.declarationDescriptor
    }

    private fun process() {
        val arguments = getArguments()
        val parameters = getParameters()

        if (declaration != null) {
            inliningContext.blocks[declaration] = InliningContext.BlockInfo(getLabelPrefix(), namingContext)
        }

        replaceThis()
        removeDefaultInitializers(arguments, parameters, body)
        aliasArgumentsIfNeeded(namingContext, arguments, parameters)
        renameLocalNames(namingContext, invokedFunction)
        removeStatementsAfterTopReturn()
        processReturns()

        namingContext.applyRenameTo(body)
        resultExpr = resultExpr?.let {
            namingContext.applyRenameTo(it) as JsExpression
        }
    }

    private fun replaceThis() {
        if (!hasThisReference(body)) return

        var thisReplacement = getThisReplacement(call)
        if (thisReplacement == null || thisReplacement is JsLiteral.JsThisRef) return

        if (thisReplacement.needToAlias()) {
            val thisName = namingContext.getFreshName(getThisAlias())
            namingContext.newVar(thisName, thisReplacement)
            thisReplacement = thisName.makeRef()
        }

        replaceThisReference(body, thisReplacement)
    }

    private fun removeStatementsAfterTopReturn() {
        val statements = body.statements

        val statementsSize = statements.size
        for (i in 0..statementsSize - 1) {
            val statement = statements[i]

            if (statement is JsReturn) {
                statements.subList(i + 1, statementsSize).clear()
                break
            }
        }
    }

    private fun processReturns() {
        doReplaceReturns()
    }

    private fun doReplaceReturns() {
        val visitor = ReturnReplacingVisitor(inliningContext)
        visitor.accept(body)
    }

    private fun getArguments(): List<JsExpression> {
        val arguments = call.arguments
        if (isCallInvocation(call)) {
            return arguments.subList(1, arguments.size)
        }

        return arguments
    }

    private fun isResultNeeded(call: JsInvocation): Boolean {
        return currentStatement !is JsExpressionStatement || call != currentStatement.expression
    }

    private fun getParameters(): List<JsParameter> {
        return invokedFunction.parameters
    }

    private fun getThisAlias(): String {
        return "\$this"
    }

    fun getLabelPrefix(): String {
        val ident = getSimpleIdent(call)
        val labelPrefix = ident ?: "inline$"

        if (labelPrefix.endsWith("$")) {
            return labelPrefix
        }

        return labelPrefix + "$"
    }

    companion object {

        @JvmStatic fun getInlineableCallReplacement(call: JsInvocation, inliningContext: InliningContext): InlineableResult {
            val mutator = FunctionInlineMutator(call, inliningContext)
            mutator.process()

            var inlineableBody: JsStatement = mutator.body
            val block = inliningContext.blocks[mutator.declaration]!!
            if (block.label != null) {
                val breakLabel = JsLabel(block.label)
                breakLabel.statement = inlineableBody
                inlineableBody = breakLabel
            }

            return InlineableResult(inlineableBody, block.result)
        }

        @JvmStatic
        private fun getThisReplacement(call: JsInvocation): JsExpression? {
            if (isCallInvocation(call)) {
                return call.arguments[0]
            }

            if (hasCallerQualifier(call)) {
                return getCallerQualifier(call)
            }

            return null
        }

        private fun hasThisReference(body: JsBlock): Boolean {
            val thisRefs = collectInstances(JsLiteral.JsThisRef::class.java, body)
            return !thisRefs.isEmpty()
        }

        @JvmStatic fun canBeExpression(function: JsFunction): Boolean {
            return canBeExpression(function.body)
        }

        private fun canBeExpression(body: JsBlock): Boolean {
            val statements = body.statements
            return statements.size == 1 && statements[0] is JsReturn
        }
    }
}
