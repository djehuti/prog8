package prog8.compiler

import prog8.ast.Program
import prog8.ast.base.AstException
import prog8.ast.base.FatalAstException
import prog8.ast.base.SyntaxError
import prog8.ast.expressions.*
import prog8.ast.statements.VarDecl
import prog8.code.core.*
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt


private typealias ConstExpressionCaller = (args: List<Expression>, position: Position, program: Program) -> NumericLiteral

internal val constEvaluatorsForBuiltinFuncs: Map<String, ConstExpressionCaller> = mapOf(
    "abs" to ::builtinAbs,
    "len" to ::builtinLen,
    "sizeof" to ::builtinSizeof,
    "sgn" to ::builtinSgn,
    "sqrt16" to { a, p, prg -> oneIntArgOutputInt(a, p, prg) { sqrt(it.toDouble()) } },
    "any" to { a, p, prg -> collectionArg(a, p, prg, ::builtinAny) },
    "all" to { a, p, prg -> collectionArg(a, p, prg, ::builtinAll) },
    "lsb" to { a, p, prg -> oneIntArgOutputInt(a, p, prg) { x: Int -> (x and 255).toDouble() } },
    "msb" to { a, p, prg -> oneIntArgOutputInt(a, p, prg) { x: Int -> (x ushr 8 and 255).toDouble()} },
    "mkword" to ::builtinMkword
)

private fun builtinAny(array: List<Double>): Double = if(array.any { it!=0.0 }) 1.0 else 0.0

private fun builtinAll(array: List<Double>): Double = if(array.all { it!=0.0 }) 1.0 else 0.0

internal fun builtinFunctionReturnType(function: String): InferredTypes.InferredType {
    if(function in arrayOf("set_carry", "set_irqd", "clear_carry", "clear_irqd"))
        return InferredTypes.InferredType.void()

    val func = BuiltinFunctions.getValue(function)
    val returnType = func.returnType
    return if(returnType==null)
        InferredTypes.InferredType.void()
    else
        InferredTypes.knownFor(returnType)
}


internal class NotConstArgumentException: AstException("not a const argument to a built-in function")
internal class CannotEvaluateException(func:String, msg: String): FatalAstException("cannot evaluate built-in function $func: $msg")


private fun oneIntArgOutputInt(args: List<Expression>, position: Position, program: Program, function: (arg: Int)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("built-in function requires one integer argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    if(constval.type != DataType.UBYTE && constval.type!= DataType.UWORD)
        throw SyntaxError("built-in function requires one integer argument", position)

    val integer = constval.number.toInt()
    return NumericLiteral.optimalInteger(function(integer).toInt(), args[0].position)
}

private fun collectionArg(args: List<Expression>, position: Position, program: Program, function: (arg: List<Double>)->Double): NumericLiteral {
    if(args.size!=1)
        throw SyntaxError("builtin function requires one non-scalar argument", position)

    val array= args[0] as? ArrayLiteral ?: throw NotConstArgumentException()
    val constElements = array.value.map{it.constValue(program)?.number}
    if(constElements.contains(null))
        throw NotConstArgumentException()

    return NumericLiteral.optimalNumeric(function(constElements.mapNotNull { it }), args[0].position)
}

private fun builtinAbs(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = int, result type= uword
    if(args.size!=1)
        throw SyntaxError("abs requires one integer argument", position)

    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return when (constval.type) {
        in IntegerDatatypesNoBool -> NumericLiteral.optimalInteger(abs(constval.number.toInt()), args[0].position)
        else -> throw SyntaxError("abs requires one integer argument", position)
    }
}

private fun builtinSizeof(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // 1 arg, type = anything, result type = ubyte
    if(args.size!=1)
        throw SyntaxError("sizeof requires one argument", position)
    if(args[0] !is IdentifierReference)
        throw SyntaxError("sizeof argument should be an identifier", position)

    val dt = args[0].inferType(program)
    if(dt.isKnown) {
        val target = (args[0] as IdentifierReference).targetStatement(program)
            ?: throw CannotEvaluateException("sizeof", "no target")

        return when {
            dt.isArray -> {
                val length = (target as VarDecl).arraysize!!.constIndex() ?: throw CannotEvaluateException("sizeof", "unknown array size")
                val elementDt = ArrayToElementTypes.getValue(dt.getOr(DataType.UNDEFINED))
                NumericLiteral.optimalInteger(program.memsizer.memorySize(elementDt) * length, position)
            }
            dt istype DataType.STR -> throw SyntaxError("sizeof str is undefined, did you mean len?", position)
            else -> NumericLiteral(DataType.UBYTE, program.memsizer.memorySize(dt.getOr(DataType.UNDEFINED)).toDouble(), position)
        }
    } else {
        throw SyntaxError("sizeof invalid argument type", position)
    }
}

private fun builtinLen(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    // note: in some cases the length is > 255, and then we have to return a UWORD type instead of a UBYTE.
    if(args.size!=1)
        throw SyntaxError("len requires one argument", position)

    val directMemVar = ((args[0] as? DirectMemoryRead)?.addressExpression as? IdentifierReference)?.targetVarDecl(program)
    var arraySize = directMemVar?.arraysize?.constIndex()
    if(arraySize != null)
        return NumericLiteral.optimalInteger(arraySize, position)
    if(args[0] is ArrayLiteral)
        return NumericLiteral.optimalInteger((args[0] as ArrayLiteral).value.size, position)
    if(args[0] !is IdentifierReference)
        throw SyntaxError("len argument should be an identifier", position)
    val target = (args[0] as IdentifierReference).targetVarDecl(program)
        ?: throw CannotEvaluateException("len", "no target vardecl")

    return when(target.datatype) {
        in ArrayDatatypes -> {
            arraySize = target.arraysize?.constIndex()
            if(arraySize==null)
                throw CannotEvaluateException("len", "arraysize unknown")
            NumericLiteral.optimalInteger(arraySize, args[0].position)
        }
        DataType.STR -> {
            val refLv = target.value as? StringLiteral ?: throw CannotEvaluateException("len", "stringsize unknown")
            NumericLiteral.optimalInteger(refLv.value.length, args[0].position)
        }
        in NumericDatatypes -> throw SyntaxError("cannot use len on numeric value, did you mean sizeof?", args[0].position)
        else -> throw InternalCompilerException("weird datatype")
    }
}

private fun builtinMkword(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 2)
        throw SyntaxError("mkword requires msb and lsb arguments", position)
    val constMsb = args[0].constValue(program) ?: throw NotConstArgumentException()
    val constLsb = args[1].constValue(program) ?: throw NotConstArgumentException()
    val result = (constMsb.number.toInt() shl 8) or constLsb.number.toInt()
    return NumericLiteral(DataType.UWORD, result.toDouble(), position)
}

private fun builtinSgn(args: List<Expression>, position: Position, program: Program): NumericLiteral {
    if (args.size != 1)
        throw SyntaxError("sgn requires one argument", position)
    val constval = args[0].constValue(program) ?: throw NotConstArgumentException()
    return NumericLiteral(DataType.BYTE, constval.number.sign, position)
}