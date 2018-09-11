package il65.compiler

import il65.ast.*
import kotlin.experimental.and
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.system.exitProcess


class CompilerException(message: String?) : Exception(message)

// 5-byte cbm MFLPT format limitations:
const val FLOAT_MAX_POSITIVE = 1.7014118345e+38
const val FLOAT_MAX_NEGATIVE = -1.7014118345e+38


fun Number.toHex(): String {
    //  0..15 -> "0".."15"
    //  16..255 -> "$10".."$ff"
    //  256..65536 -> "$0100".."$ffff"
    val integer = this.toInt()
    return when (integer) {
        in 0 until 16 -> integer.toString()
        in 0 until 0x100 -> "$"+integer.toString(16).padStart(2,'0')
        in 0 until 0x10000 -> "$"+integer.toString(16).padStart(4,'0')
        else -> throw CompilerException("number too large for 16 bits $this")
    }
}


data class Mflpt5(val b0: Short, val b1: Short, val b2: Short, val b3: Short, val b4: Short) {

    companion object {
        val zero = Mflpt5(0, 0,0,0,0)
        fun fromNumber(num: Number): Mflpt5 {
            // see https://en.wikipedia.org/wiki/Microsoft_Binary_Format
            // and https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
            // and https://en.wikipedia.org/wiki/IEEE_754-1985

            val flt = num.toDouble()
            if(flt < FLOAT_MAX_NEGATIVE || flt > FLOAT_MAX_POSITIVE)
                throw CompilerException("floating point number out of 5-byte mflpt range: $this")
            if(flt==0.0)
                return zero

            val sign = if(flt<0.0) 0x80L else 0x00L
            var exponent = 128 + 32	// 128 is cbm's bias, 32 is this algo's bias
            var mantissa = flt.absoluteValue

            // if mantissa is too large, shift right and adjust exponent
            while(mantissa >= 0x100000000) {
                mantissa /= 2.0
                exponent ++
            }
            // if mantissa is too small, shift left and adjust exponent
            while(mantissa < 0x80000000) {
                mantissa *= 2.0
                exponent --
            }

            return when {
                exponent<0 -> zero  // underflow, use zero instead
                exponent>255 -> throw CompilerException("floating point overflow: $this")
                exponent==0 -> zero
                else -> {
                    val mantLong = mantissa.toLong()
                    Mflpt5(
                            exponent.toShort(),
                            (mantLong.and(0x7f000000L) ushr 24).or(sign).toShort(),
                            (mantLong.and(0x00ff0000L) ushr 16).toShort(),
                            (mantLong.and(0x0000ff00L) ushr 8).toShort(),
                            (mantLong.and(0x000000ffL)).toShort())
                }
            }
        }
    }

    fun toDouble(): Double {
        if(this == zero) return 0.0
        val exp = b0 - 128
        val sign = (b1.and(0x80)) > 0
        val number = 0x80000000L.or(b1.toLong() shl 24).or(b2.toLong() shl 16).or(b3.toLong() shl 8).or(b4.toLong())
        val result = number.toDouble() * (2.0).pow(exp) / 0x100000000
        return if(sign) -result else result
    }
}


class Compiler(private val options: CompilationOptions) {
    fun compile(module: Module) : StackVmProgram {
        println("\nCompiling parsed source code to stackvmProg code...")

        val namespace = module.definingScope()

        val intermediate = StackVmProgram(module.name)
        namespace.debugPrint()

        // create the pool of all variables used in all blocks and scopes
        val varGather = VarGatherer(intermediate)
        varGather.process(module)
        println("Number of allocated variables and constants: ${intermediate.variables.size} (${intermediate.variablesMemSize} bytes)")

        val translator = StatementTranslator(intermediate, namespace)
        translator.process(module)
        println("Number of source statements: ${translator.stmtUniqueSequenceNr}")
        println("Number of vm instructions: ${intermediate.instructions.size}")

        return intermediate
    }


    class VarGatherer(val stackvmProg: StackVmProgram): IAstProcessor {
        // collect all the VarDecls to make them into one global list
        override fun process(decl: VarDecl): IStatement {
            assert(decl.type==VarDeclType.VAR) {"only VAR decls should remain: CONST and MEMORY should have been processed away"}
            stackvmProg.blockvar(decl.scopedname, decl)
            return super.process(decl)
        }
    }

    class StatementTranslator(val stackvmProg: StackVmProgram, val namespace: INameScope): IAstProcessor {
        var stmtUniqueSequenceNr = 0
            private set

        override fun process(subroutine: Subroutine): IStatement {
            translate(subroutine.statements)
            return super.process(subroutine)
        }

        override fun process(block: Block): IStatement {
            translate(block.statements)
            return super.process(block)
        }

        private fun translate(statements: List<IStatement>) {
            for (stmt: IStatement in statements) {
                stmtUniqueSequenceNr++
                when (stmt) {
                    is AnonymousStatementList -> translate(stmt.statements)
                    is BuiltinFunctionStatementPlaceholder -> translate(stmt)
                    is Label -> translate(stmt)
                    is Return -> translate(stmt)
                    is Assignment -> translate(stmt)
                    is PostIncrDecr -> translate(stmt)
                    is Jump -> translate(stmt)
                    is FunctionCallStatement -> translate(stmt)
                    is InlineAssembly -> translate(stmt)
                    is IfStatement -> translate(stmt)
                    is BranchStatement -> translate(stmt)
                    is Directive, is VarDecl, is Subroutine -> {}   // skip this, already processed these.
                    else -> TODO("translate statement $stmt")
                }
            }
        }

        private fun translate(branch: BranchStatement) {
            /*
             * A branch: IF_CC { stuff } else { other_stuff }
             * Which is desugared into:
             *      BCS _stmt_999_else
             *      stuff
             *      JUMP _stmt_999_continue
             * _stmt_999_else:
             *      other_stuff     ;; optional
             * _stmt_999_continue:
             *      ...
             */
            val labelElse = makeLabel("else")
            val labelContinue = makeLabel("continue")
            val opcode = when(branch.condition) {
                BranchCondition.CS -> "bcc"
                BranchCondition.CC -> "bcs"
                BranchCondition.EQ -> "bne"
                BranchCondition.NE -> "beq"
                BranchCondition.VS -> "bvc"
                BranchCondition.VC -> "bvs"
                BranchCondition.MI -> "bpl"
                BranchCondition.PL -> "bmi"
            }
            if(branch.elsepart.isEmpty()) {
                stackvmProg.instruction("$opcode $labelContinue")
                translate(branch.statements)
                stackvmProg.label(labelContinue)
            } else {
                stackvmProg.instruction("$opcode $labelElse")
                translate(branch.statements)
                stackvmProg.instruction("jump $labelContinue")
                stackvmProg.label(labelElse)
                translate(branch.elsepart)
                stackvmProg.label(labelContinue)
            }
        }

        private fun makeLabel(postfix: String): String = "_il65stmt_${stmtUniqueSequenceNr}_$postfix"

        private fun translate(stmt: IfStatement) {
            println("@todo translate: #$stmtUniqueSequenceNr :  $stmt")
            stackvmProg.instruction("nop") // todo translate if statement
        }

        private fun translate(stmt: InlineAssembly) {
            println("@todo translate: #$stmtUniqueSequenceNr :  $stmt")
            TODO("inline assembly not supported yet by stackvm")
        }

        private fun translate(stmt: FunctionCallStatement) {
            val targetStmt = stmt.target.targetStatement(namespace)!!
            if(targetStmt is BuiltinFunctionStatementPlaceholder) {
                // call to a builtin function
                TODO("BUILTIN_${stmt.target.nameInSource[0]}")      // TODO
                return
            }

            val targetname = when(targetStmt) {
                is Label -> targetStmt.scopedname
                is Subroutine -> targetStmt.scopedname
                else -> throw AstException("invalid call target node type: ${targetStmt::class}")
            }

            for (arg in stmt.arglist) {
                val lv = arg.constValue(namespace)
                if(lv==null) TODO("argument must be constant for now")      // TODO non-const args
                stackvmProg.instruction("push ${makeValue(lv)}")
            }
            stackvmProg.instruction("call $targetname")
        }

        private fun makeValue(value: LiteralValue): String {
            return when {
                value.isString -> "\"${value.strvalue}\""
                value.isNumeric -> value.asNumericValue.toString()
                else -> TODO("stackvm value for $value")
            }
        }

        private fun translate(stmt: Jump) {
            val instr =
                    if(stmt.address!=null) {
                        "jump \$${stmt.address.toString(16)}"
                    } else {
                        val target = stmt.identifier!!.targetStatement(namespace)!!
                        when(target) {
                            is Label -> "jump ${target.scopedname}"
                            is Subroutine -> "jump ${target.scopedname}"
                            else -> throw CompilerException("invalid jump target type ${target::class}")
                        }
                    }
            stackvmProg.instruction(instr)
        }

        private fun translate(stmt: PostIncrDecr) {
            if(stmt.target.register!=null) {
                TODO("register++/-- not yet implemented")
            } else {
                val targetStatement = stmt.target.identifier!!.targetStatement(namespace) as VarDecl
                when(stmt.operator) {
                    "++" -> stackvmProg.instruction("inc_var ${targetStatement.scopedname}")
                    "--" -> stackvmProg.instruction("decr_var ${targetStatement.scopedname}")
                }
            }
        }

        private fun translate(stmt: Assignment) {
            println("@todo translate: #$stmtUniqueSequenceNr :  $stmt")
            stackvmProg.instruction("nop") // todo translate assignment
        }

        private fun translate(stmt: Return) {
            if(stmt.values.isNotEmpty()) {
                TODO("return with value(s) not yet supported: $stmt")
            }
            stackvmProg.instruction("return")
        }

        private fun translate(stmt: Label) {
            stackvmProg.label(stmt.scopedname)
        }

        private fun translate(stmt: BuiltinFunctionStatementPlaceholder) {
            println("@todo translate: #$stmtUniqueSequenceNr :  $stmt")
            stackvmProg.instruction("nop") // todo translate builtinfunction placeholder
        }
    }
}



class StackVmProgram(val name: String) {

    val variables = mutableMapOf<String, VarDecl>()
    val instructions = mutableListOf<String>()
    val variablesMemSize: Int
        get() {
            return variables.values.fold(0) { acc, vardecl -> acc+vardecl.memorySize}
        }

    fun optimize() {
        println("\nOptimizing stackvmProg code...")
        // todo optimize stackvm code
    }

    fun compileToAssembly(): AssemblyResult {
        println("\nGenerating assembly code from stackvmProg code... ")
        // todo generate 6502 assembly
        return AssemblyResult(name)
    }

    fun blockvar(scopedname: String, decl: VarDecl) {
        variables[scopedname] = decl
    }

    fun toTextLines() : List<String> {
        val result = mutableListOf("; stackvm program code for: $name")
        result.add("%memory")
        // todo memory lines for initial memory initialization
        result.add("%end_memory")
        result.add("%variables")
        for(v in variables) {
            assert(v.value.type==VarDeclType.VAR || v.value.type==VarDeclType.CONST)
            val litval = v.value.value as LiteralValue
            val litvalStr = when {
                litval.isNumeric -> litval.asNumericValue.toString()
                litval.isString -> "\"${litval.strvalue}\""
                else -> TODO()
            }
            val line = "${v.key} ${v.value.datatype.toString().toLowerCase()} $litvalStr"
            result.add(line)
        }
        result.add("%end_variables")
        result.add("%instructions")
        result.addAll(instructions)
        result.add("%end_instructions")
        return result
    }

    fun instruction(s: String) {
        instructions.add("    $s")
    }

    fun label(name: String) {
        instructions.add("$name:")
    }
}

enum class OutputType {
    RAW,
    PRG
}

enum class LauncherType {
    BASIC,
    NONE
}

enum class ZeropageType {
    BASICSAFE,
    KERNALSAFE,
    FULL
}


data class CompilationOptions(val output: OutputType,
                              val launcher: LauncherType,
                              val zeropage: ZeropageType,
                              val floats: Boolean)


class AssemblyResult(val name: String) {
    fun assemble(options: CompilationOptions, inputfilename: String, outputfilename: String) {
        println("\nGenerating machine code program...")

        val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "-Wall", "-Wno-strict-bool",
            "--dump-labels", "--vice-labels", "-l", "$outputfilename.vice-mon-list",
            "--no-monitor", "--output", outputfilename, inputfilename)

        when(options.output) {
            OutputType.PRG -> {
                command.add("--cbm-prg")
                println("\nCreating C-64 prg.")
            }
            OutputType.RAW -> {
                command.add("--nostart")
                println("\nCreating raw binary.")
            }
        }

        val proc = ProcessBuilder(command).inheritIO().start()
        val result = proc.waitFor()
        if(result!=0) {
            System.err.println("assembler failed with returncode $result")
            exitProcess(result)
        }
    }

    fun generateBreakpointList(): String {
        // todo build breakpoint list!
/*
    def generate_breakpoint_list(self, program_filename: str) -> str:
        breakpoints = []
        vice_mon_file = program_filename + ".vice-mon-list"
        with open(vice_mon_file, "rU") as f:
            for line in f:
                match = re.fullmatch(r"al (?P<address>\w+) \S+_il65_breakpoint_\d+.?", line, re.DOTALL)
                if match:
                    breakpoints.append("$" + match.group("address"))
        with open(vice_mon_file, "at") as f:
            print("; vice monitor breakpoint list now follows", file=f)
            print("; {:d} breakpoints have been defined here".format(len(breakpoints)), file=f)
            print("del", file=f)
            for b in breakpoints:
                print("break", b, file=f)
        return vice_mon_file
 */
        return "monitorfile.txt"
    }
}