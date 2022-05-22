package prog8.optimizer

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.AstWalker
import prog8.ast.walk.IAstModification
import prog8.ast.walk.IAstVisitor
import prog8.code.core.InternalCompilerException


private  fun isEmptyReturn(stmt: Statement): Boolean = stmt is Return && stmt.value==null


class Inliner(val program: Program): AstWalker() {

    class DetermineInlineSubs(val program: Program): IAstVisitor {
        private val modifications = mutableListOf<IAstModification>()

        init {
            visit(program)
            modifications.forEach { it.perform() }
            modifications.clear()
        }

        override fun visit(subroutine: Subroutine) {
            if(!subroutine.isAsmSubroutine && !subroutine.inline && subroutine.parameters.isEmpty()) {
                val containsSubsOrVariables = subroutine.statements.any { it is VarDecl || it is Subroutine}
                if(!containsSubsOrVariables) {
                    if(subroutine.statements.size==1 || (subroutine.statements.size==2 && isEmptyReturn(subroutine.statements[1]))) {
                        // subroutine is possible candidate to be inlined
                        subroutine.inline =
                            when(val stmt=subroutine.statements[0]) {
                                is Return -> {
                                    if(stmt.value is NumericLiteral)
                                        true
                                    else if (stmt.value is IdentifierReference) {
                                        makeFullyScoped(stmt.value as IdentifierReference)
                                        true
                                    } else if(stmt.value!! is IFunctionCall && (stmt.value as IFunctionCall).args.size<=1 && (stmt.value as IFunctionCall).args.all { it is NumericLiteral || it is IdentifierReference }) {
                                        when (stmt.value) {
                                            is BuiltinFunctionCall -> {
                                                makeFullyScoped(stmt.value as BuiltinFunctionCall)
                                                true
                                            }
                                            is FunctionCallExpression -> {
                                                makeFullyScoped(stmt.value as FunctionCallExpression)
                                                true
                                            }
                                            else -> false
                                        }
                                    } else
                                        false
                                }
                                is Assignment -> {
                                    if(stmt.value.isSimple) {
                                        val targetInline =
                                            if(stmt.target.identifier!=null) {
                                                makeFullyScoped(stmt.target.identifier!!)
                                                true
                                            } else if(stmt.target.memoryAddress?.addressExpression is NumericLiteral || stmt.target.memoryAddress?.addressExpression is IdentifierReference) {
                                                if(stmt.target.memoryAddress?.addressExpression is IdentifierReference)
                                                    makeFullyScoped(stmt.target.memoryAddress?.addressExpression as IdentifierReference)
                                                true
                                            } else
                                                false
                                        val valueInline =
                                            if(stmt.value is IdentifierReference) {
                                                makeFullyScoped(stmt.value as IdentifierReference)
                                                true
                                            } else if((stmt.value as? DirectMemoryRead)?.addressExpression is NumericLiteral || (stmt.value as? DirectMemoryRead)?.addressExpression is IdentifierReference) {
                                                if((stmt.value as? DirectMemoryRead)?.addressExpression is IdentifierReference)
                                                    makeFullyScoped((stmt.value as? DirectMemoryRead)?.addressExpression as IdentifierReference)
                                                true
                                            } else
                                                false
                                        targetInline || valueInline
                                    } else
                                        false
                                }
                                is BuiltinFunctionCallStatement -> {
                                    val inline = stmt.args.size<=1 && stmt.args.all { it is NumericLiteral || it is IdentifierReference }
                                    if(inline)
                                        makeFullyScoped(stmt)
                                    inline
                                }
                                is FunctionCallStatement -> {
                                    val inline = stmt.args.size<=1 && stmt.args.all { it is NumericLiteral || it is IdentifierReference }
                                    if(inline)
                                        makeFullyScoped(stmt)
                                    inline
                                }
                                is PostIncrDecr -> {
                                    if(stmt.target.identifier!=null) {
                                        makeFullyScoped(stmt.target.identifier!!)
                                        true
                                    }
                                    else if(stmt.target.memoryAddress?.addressExpression is NumericLiteral || stmt.target.memoryAddress?.addressExpression is IdentifierReference) {
                                        if(stmt.target.memoryAddress?.addressExpression is IdentifierReference)
                                            makeFullyScoped(stmt.target.memoryAddress?.addressExpression as IdentifierReference)
                                        true
                                    } else
                                        false
                                }
                                is Jump, is GoSub -> true
                                else -> false
                            }
                    }
                }
            }
            super.visit(subroutine)
        }

        private fun makeFullyScoped(identifier: IdentifierReference) {
            val scoped = (identifier.targetStatement(program)!! as INamedStatement).scopedName
            val scopedIdent = IdentifierReference(scoped, identifier.position)
            modifications += IAstModification.ReplaceNode(identifier, scopedIdent, identifier.parent)
        }

        private fun makeFullyScoped(call: BuiltinFunctionCallStatement) {
            val scopedArgs = makeScopedArgs(call.args)
            val scopedCall = BuiltinFunctionCallStatement(call.target.copy(), scopedArgs.toMutableList(), call.position)
            modifications += IAstModification.ReplaceNode(call, scopedCall, call.parent)
        }

        private fun makeFullyScoped(call: FunctionCallStatement) {
            val sub = call.target.targetSubroutine(program)!!
            val scopedName = IdentifierReference(sub.scopedName, call.target.position)
            val scopedArgs = makeScopedArgs(call.args)
            val scopedCall = FunctionCallStatement(scopedName, scopedArgs.toMutableList(), call.void, call.position)
            modifications += IAstModification.ReplaceNode(call, scopedCall, call.parent)
        }

        private fun makeFullyScoped(call: BuiltinFunctionCall) {
            val sub = call.target.targetSubroutine(program)!!
            val scopedName = IdentifierReference(sub.scopedName, call.target.position)
            val scopedArgs = makeScopedArgs(call.args)
            val scopedCall = BuiltinFunctionCall(scopedName, scopedArgs.toMutableList(), call.position)
            modifications += IAstModification.ReplaceNode(call, scopedCall, call.parent)
        }

        private fun makeFullyScoped(call: FunctionCallExpression) {
            val scopedArgs = makeScopedArgs(call.args)
            val scopedCall = FunctionCallExpression(call.target.copy(), scopedArgs.toMutableList(), call.position)
            modifications += IAstModification.ReplaceNode(call, scopedCall, call.parent)
        }

        private fun makeScopedArgs(args: List<Expression>): List<Expression> {
            return args.map {
                when (it) {
                    is NumericLiteral -> it.copy()
                    is IdentifierReference -> {
                        val scoped = (it.targetStatement(program)!! as INamedStatement).scopedName
                        IdentifierReference(scoped, it.position)
                    }
                    else -> throw InternalCompilerException("expected only number or identifier arg, otherwise too complex")
                }
            }
        }
    }

    override fun before(program: Program): Iterable<IAstModification> {
        DetermineInlineSubs(program)
        return super.before(program)
    }

    override fun after(gosub: GoSub, parent: Node): Iterable<IAstModification> {
        val sub = gosub.identifier.targetStatement(program) as? Subroutine
        if(sub!=null && sub.inline && sub.parameters.isEmpty()) {
            require(sub.statements.size == 1 || (sub.statements.size == 2 && isEmptyReturn(sub.statements[1])))
            return if(sub.isAsmSubroutine) {
                // simply insert the asm for the argument-less routine
                listOf(IAstModification.ReplaceNode(gosub, sub.statements.single().copy(), parent))
            } else {
                // note that we don't have to process any args, because we online inline parameterless subroutines.
                when (val toInline = sub.statements.first()) {
                    is Return -> noModifications
                    else -> listOf(IAstModification.ReplaceNode(gosub, toInline.copy(), parent))
                }
            }

        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification>  {
        val sub = functionCallStatement.target.targetStatement(program) as? Subroutine
        if(sub!=null && sub.inline && sub.parameters.isEmpty()) {
            require(sub.statements.size==1 || (sub.statements.size==2 && isEmptyReturn(sub.statements[1])))
            return if(sub.isAsmSubroutine) {
                // simply insert the asm for the argument-less routine
                listOf(IAstModification.ReplaceNode(functionCallStatement, sub.statements.single().copy(), parent))
            } else {
                // note that we don't have to process any args, because we online inline parameterless subroutines.
                when (val toInline = sub.statements.first()) {
                    is Return -> noModifications
                    else -> listOf(IAstModification.ReplaceNode(functionCallStatement, toInline.copy(), parent))
                }
            }
        }
        return noModifications
    }

    // TODO also inline function call expressions, and remove it from the StatementOptimizer
}

