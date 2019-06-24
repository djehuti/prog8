package prog8.compiler.target.c64

// note: to put stuff on the stack, we use Absolute,X  addressing mode which is 3 bytes / 4 cycles
// possible space optimization is to use zeropage (indirect),Y  which is 2 bytes, but 5 cycles

import prog8.ast.*
import prog8.compiler.RuntimeValue
import prog8.compiler.*
import prog8.compiler.intermediate.*
import prog8.stackvm.Syscall
import prog8.stackvm.syscallsForStackVm
import java.io.File
import java.util.*
import kotlin.math.abs


class AssemblyError(msg: String) : RuntimeException(msg)


class AsmGen(private val options: CompilationOptions, private val program: IntermediateProgram,
             private val heap: HeapValues, private val zeropage: Zeropage) {
    private val globalFloatConsts = mutableMapOf<Double, String>()
    private val assemblyLines = mutableListOf<String>()
    private lateinit var block: IntermediateProgram.ProgramBlock
    private var breakpointCounter = 0

    init {
        // Convert invalid label names (such as "<anon-1>") to something that's allowed.
        val newblocks = mutableListOf<IntermediateProgram.ProgramBlock>()
        for(block in program.blocks) {
            val newvars = block.variables.map { symname(it.key, block) to it.value }.toMap().toMutableMap()
            val newvarsZeropaged = block.variablesMarkedForZeropage.map{symname(it, block)}.toMutableSet()
            val newlabels = block.labels.map { symname(it.key, block) to it.value}.toMap().toMutableMap()
            val newinstructions = block.instructions.asSequence().map {
                when {
                    it is LabelInstr -> LabelInstr(symname(it.name, block), it.asmProc)
                    it.opcode == Opcode.INLINE_ASSEMBLY -> it
                    else ->
                        Instruction(it.opcode, it.arg, it.arg2,
                            callLabel = if (it.callLabel != null) symname(it.callLabel, block) else null,
                            callLabel2 = if (it.callLabel2 != null) symname(it.callLabel2, block) else null)
                }
            }.toMutableList()
            val newMempointers = block.memoryPointers.map { symname(it.key, block) to it.value }.toMap().toMutableMap()
            val newblock = IntermediateProgram.ProgramBlock(
                    block.name,
                    block.address,
                    newinstructions,
                    newvars,
                    newMempointers,
                    newlabels,
                    force_output = block.force_output)
            newblock.variablesMarkedForZeropage.clear()
            newblock.variablesMarkedForZeropage.addAll(newvarsZeropaged)
            newblocks.add(newblock)
        }
        program.blocks.clear()
        program.blocks.addAll(newblocks)

        val newAllocatedZp = program.allocatedZeropageVariables.map { symname(it.key, null) to it.value}
        program.allocatedZeropageVariables.clear()
        program.allocatedZeropageVariables.putAll(newAllocatedZp)

        // make a list of all const floats that are used
        for(block in program.blocks) {
            for(ins in block.instructions.filter{it.arg?.type==DataType.FLOAT}) {
                val float = ins.arg!!.numericValue().toDouble()
                if(float !in globalFloatConsts)
                    globalFloatConsts[float] = "prog8_const_float_${globalFloatConsts.size}"
            }
        }
    }

    fun compileToAssembly(optimize: Boolean): AssemblyProgram {
        println("Generating assembly code from intermediate code... ")

        assemblyLines.clear()
        header()
        for(b in program.blocks)
            block2asm(b)

        if(optimize) {
            var optimizationsDone = 1
            while (optimizationsDone > 0) {
                optimizationsDone = optimizeAssembly(assemblyLines)
            }
        }

        File("${program.name}.asm").printWriter().use {
            for (line in assemblyLines) { it.println(line) }
        }

        return AssemblyProgram(program.name)
    }

    private fun out(str: String, splitlines: Boolean=true) {
        if(splitlines) {
            for (line in str.split('\n')) {
                val trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line.trim()
                // trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                assemblyLines.add(trimmed)
            }
        } else assemblyLines.add(str)
    }


    // convert a fully scoped name (defined in the given block) to a valid assembly symbol name
    private fun symname(scoped: String, block: IntermediateProgram.ProgramBlock?): String {
        if(' ' in scoped)
            return scoped
        val blockLocal: Boolean
        var name = if (block!=null && scoped.startsWith("${block.name}.")) {
            blockLocal = true
            scoped.substring(block.name.length+1)
        }
        else {
            blockLocal = false
            scoped
        }
        name = name.replace("<", "prog8_").replace(">", "")     // take care of the autogenerated invalid (anon) label names
        if(name=="-")
            return "-"
        if(blockLocal)
            name = name.replace(".", "_")
        else {
            val parts = name.split(".", limit=2)
            if(parts.size>1)
                name = "${parts[0]}.${parts[1].replace(".", "_")}"
        }
        return name.replace("-", "")
    }

    private fun makeFloatFill(flt: Mflpt5): String {
        val b0 = "$"+flt.b0.toString(16).padStart(2, '0')
        val b1 = "$"+flt.b1.toString(16).padStart(2, '0')
        val b2 = "$"+flt.b2.toString(16).padStart(2, '0')
        val b3 = "$"+flt.b3.toString(16).padStart(2, '0')
        val b4 = "$"+flt.b4.toString(16).padStart(2, '0')
        return "$b0, $b1, $b2, $b3, $b4"
    }

    private fun header() {
        val ourName = this.javaClass.name
        out("; 6502 assembly code for '${program.name}'")
        out("; generated by $ourName on ${Date()}")
        out("; assembler syntax is for the 64tasm cross-assembler")
        out("; output options: output=${options.output} launcher=${options.launcher} zp=${options.zeropage}")
        out("\n.cpu  '6502'\n.enc  'none'\n")

        if(program.loadAddress==0)   // fix load address
            program.loadAddress = if(options.launcher==LauncherType.BASIC) BASIC_LOAD_ADDRESS else RAW_LOAD_ADDRESS

        when {
            options.launcher == LauncherType.BASIC -> {
                if (program.loadAddress != 0x0801)
                    throw AssemblyError("BASIC output must have load address $0801")
                out("; ---- basic program with sys call ----")
                out("* = ${program.loadAddress.toHex()}")
                val year = Calendar.getInstance().get(Calendar.YEAR)
                out("  .word  (+), $year")
                out("  .null  $9e, format(' %d ', _prog8_entrypoint), $3a, $8f, ' prog8 by idj'")
                out("+\t.word  0")
                out("_prog8_entrypoint\t; assembly code starts here\n")
                out("  jsr  prog8_lib.init_system")
            }
            options.output == OutputType.PRG -> {
                out("; ---- program without basic sys call ----")
                out("* = ${program.loadAddress.toHex()}\n")
                out("  jsr  prog8_lib.init_system")
            }
            options.output == OutputType.RAW -> {
                out("; ---- raw assembler program ----")
                out("* = ${program.loadAddress.toHex()}\n")
            }
        }

        if(zeropage.exitProgramStrategy!=Zeropage.ExitProgramStrategy.CLEAN_EXIT) {
            // disable shift-commodore charset switching and run/stop key
            out("  lda  #$80")
            out("  lda  #$80")
            out("  sta  657\t; disable charset switching")
            out("  lda  #239")
            out("  sta  808\t; disable run/stop key")
        }

        out("  ldx  #\$ff\t; init estack pointer")
        out("  ; initialize the variables in each block")
        for(block in program.blocks) {
            val initVarsLabel = block.instructions.firstOrNull { it is LabelInstr && it.name==initvarsSubName } as? LabelInstr
            if(initVarsLabel!=null)
                out("  jsr  ${block.name}.${initVarsLabel.name}")
        }
        out("  clc")
        when(zeropage.exitProgramStrategy) {
            Zeropage.ExitProgramStrategy.CLEAN_EXIT -> {
                out("  jmp  main.start\t; jump to program entrypoint")
            }
            Zeropage.ExitProgramStrategy.SYSTEM_RESET -> {
                out("  jsr  main.start\t; call program entrypoint")
                out("  jmp  (c64.RESET_VEC)\t; cold reset")
            }
        }
        out("")

        // the global list of all floating point constants for the whole program
        for(flt in globalFloatConsts) {
            val floatFill = makeFloatFill(Mflpt5.fromNumber(flt.key))
            out("${flt.value}\t.byte  $floatFill  ; float ${flt.key}")
        }
    }

    private fun block2asm(blk: IntermediateProgram.ProgramBlock) {
        block = blk
        out("\n; ---- block: '${block.name}' ----")
        if(!blk.force_output)
            out("${block.name}\t.proc\n")
        if(block.address!=null) {
            out(".cerror * > ${block.address?.toHex()}, 'block address overlaps by ', *-${block.address?.toHex()},' bytes'")
            out("* = ${block.address?.toHex()}")
        }

        // deal with zeropage variables
        for(variable in blk.variables) {
            val sym = symname(blk.name+"."+variable.key, null)
            val zpVar = program.allocatedZeropageVariables[sym]
            if(zpVar==null) {
                // This var is not on the ZP yet. Attempt to move it there (if it's not a float, those take up too much space)
                if(variable.value.type in zeropage.allowedDatatypes && variable.value.type != DataType.FLOAT) {
                    try {
                        val address = zeropage.allocate(sym, variable.value.type, null)
                        out("${variable.key} = $address\t; auto zp ${variable.value.type}")
                        // make sure we add the var to the set of zpvars for this block
                        blk.variablesMarkedForZeropage.add(variable.key)
                        program.allocatedZeropageVariables[sym] = Pair(address, variable.value.type)
                    } catch (x: ZeropageDepletedError) {
                        // leave it as it is.
                    }
                }
            }
            else {
                // it was already allocated on the zp
                out("${variable.key} = ${zpVar.first}\t; zp ${zpVar.second}")
            }
        }

        out("\n; memdefs and kernel subroutines")
        memdefs2asm(block)
        out("\n; non-zeropage variables")
        vardecls2asm(block)
        out("")

        val instructionPatternWindowSize = 8        // increase once patterns occur longer than this.
        var processed = 0

        for (ins in block.instructions.windowed(instructionPatternWindowSize, partialWindows = true)) {
            if (processed == 0) {
                processed = instr2asm(ins)
                if (processed == 0) {
                    // the instructions are not recognised yet and can't be translated into assembly
                    throw CompilerException("no asm translation found for instruction pattern: $ins")
                }
            }
            processed--
        }
        if(!blk.force_output)
            out("\n\t.pend\n")
    }

    private fun memdefs2asm(block: IntermediateProgram.ProgramBlock) {
        for(m in block.memoryPointers) {
            out("  ${m.key} = ${m.value.first.toHex()}")
        }
    }

    private fun vardecls2asm(block: IntermediateProgram.ProgramBlock) {
        // these are the non-zeropage variables
        val sortedVars = block.variables.filter{it.key !in block.variablesMarkedForZeropage}.toList().sortedBy { it.second.type }
        for (v in sortedVars) {
            when (v.second.type) {
                DataType.UBYTE -> out("${v.first}\t.byte  0")
                DataType.BYTE -> out("${v.first}\t.char  0")
                DataType.UWORD -> out("${v.first}\t.word  0")
                DataType.WORD -> out("${v.first}\t.sint  0")
                DataType.FLOAT -> out("${v.first}\t.byte  0,0,0,0,0  ; float")
                DataType.STR, DataType.STR_S -> {
                    val rawStr = heap.get(v.second.heapId!!).str!!
                    val bytes = encodeStr(rawStr, v.second.type).map { "$" + it.toString(16).padStart(2, '0') }
                    out("${v.first}\t; ${v.second.type} \"${escape(rawStr).replace("\u0000", "<NULL>")}\"")
                    for (chunk in bytes.chunked(16))
                        out("  .byte  " + chunk.joinToString())
                }
                DataType.ARRAY_UB -> {
                    // unsigned integer byte arraysize
                    val data = makeArrayFillDataUnsigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.byte  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("  .byte  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_B -> {
                    // signed integer byte arraysize
                    val data = makeArrayFillDataSigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.char  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("  .char  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_UW -> {
                    // unsigned word arraysize
                    val data = makeArrayFillDataUnsigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.word  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("  .word  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_W -> {
                    // signed word arraysize
                    val data = makeArrayFillDataSigned(v.second)
                    if (data.size <= 16)
                        out("${v.first}\t.sint  ${data.joinToString()}")
                    else {
                        out(v.first)
                        for (chunk in data.chunked(16))
                            out("  .sint  " + chunk.joinToString())
                    }
                }
                DataType.ARRAY_F -> {
                    // float arraysize
                    val array = heap.get(v.second.heapId!!).doubleArray!!
                    val floatFills = array.map { makeFloatFill(Mflpt5.fromNumber(it)) }
                    out(v.first)
                    for(f in array.zip(floatFills))
                        out("  .byte  ${f.second}  ; float ${f.first}")
                }
            }
        }
    }

    private fun encodeStr(str: String, dt: DataType): List<Short> {
        return when(dt) {
            DataType.STR -> {
                val bytes = Petscii.encodePetscii(str, true)
                bytes.plus(0)
            }
            DataType.STR_S -> {
                val bytes = Petscii.encodeScreencode(str, true)
                bytes.plus(0)
            }
            else -> throw AssemblyError("invalid str type")
        }
    }

    private fun makeArrayFillDataUnsigned(value: RuntimeValue): List<String> {
        val array = heap.get(value.heapId!!).array!!
        return when {
            value.type==DataType.ARRAY_UB ->
                // byte array can never contain pointer-to types, so treat values as all integers
                array.map { "$"+it.integer!!.toString(16).padStart(2, '0') }
            value.type==DataType.ARRAY_UW -> array.map {
                when {
                    it.integer!=null -> "$"+it.integer.toString(16).padStart(2, '0')
                    it.addressOf!=null -> symname(it.addressOf.scopedname!!, block)
                    else -> throw AssemblyError("weird type in array")
                }
            }
            else -> throw AssemblyError("invalid arraysize type")
        }
    }

    private fun makeArrayFillDataSigned(value: RuntimeValue): List<String> {
        val array = heap.get(value.heapId!!).array!!
        // note: array of signed value can never contain pointer-to type, so simply process values as being all integers
        return if (value.type == DataType.ARRAY_B || value.type == DataType.ARRAY_W) {
            array.map {
                if(it.integer!!>=0)
                    "$"+it.integer.toString(16).padStart(2, '0')
                else
                    "-$"+abs(it.integer).toString(16).padStart(2, '0')
            }
        }
        else throw AssemblyError("invalid arraysize type")
    }

    private fun instr2asm(ins: List<Instruction>): Int {
        // find best patterns (matching the most of the lines, then with the smallest weight)
        val fragments = findPatterns(ins).sortedByDescending { it.segmentSize }
        if(fragments.isEmpty()) {
            // we didn't find any matching patterns (complex multi-instruction fragments), try simple ones
            val firstIns = ins[0]
            val singleAsm = simpleInstr2Asm(firstIns)
            if(singleAsm != null) {
                outputAsmFragment(singleAsm)
                return 1
            }
            return 0
        }
        val best = fragments[0]
        outputAsmFragment(best.asm)
        return best.segmentSize
    }

    private fun outputAsmFragment(singleAsm: String) {
        if (singleAsm.isNotEmpty()) {
            if(singleAsm.startsWith("@inline@"))
                out(singleAsm.substring(8), false)
            else {
                val withNewlines = singleAsm.replace('|', '\n')
                out(withNewlines)
            }
        }
    }

    private fun getFloatConst(value: RuntimeValue): String =
            globalFloatConsts[value.numericValue().toDouble()]
                    ?: throw AssemblyError("should have a global float const for number $value")

    private fun simpleInstr2Asm(ins: Instruction): String? {
        // a label 'instruction' is simply translated into a asm label
        if(ins is LabelInstr) {
            val labelresult =
                    if(ins.name.startsWith("${block.name}."))
                        ins.name.substring(block.name.length+1)
                    else
                        ins.name
            return if(ins.asmProc) labelresult+"\t\t.proc" else labelresult
        }

        // simple opcodes that are translated directly into one or a few asm instructions
        return when(ins.opcode) {
            Opcode.LINE -> " ;\tsrc line: ${ins.callLabel}"
            Opcode.NOP -> " nop"      // shouldn't be present anymore though
            Opcode.START_PROCDEF -> ""  // is done as part of a label
            Opcode.END_PROCDEF -> " .pend"
            Opcode.TERMINATE -> " brk"
            Opcode.SEC -> " sec"
            Opcode.CLC -> " clc"
            Opcode.SEI -> " sei"
            Opcode.CLI -> " cli"
            Opcode.CARRY_TO_A -> " lda  #0 |  adc  #0"
            Opcode.JUMP -> {
                if(ins.callLabel!=null)
                    " jmp  ${ins.callLabel}"
                else
                    " jmp  ${hexVal(ins)}"
            }
            Opcode.CALL -> {
                if(ins.callLabel!=null)
                    " jsr  ${ins.callLabel}"
                else
                    " jsr  ${hexVal(ins)}"
            }
            Opcode.RETURN -> " rts"
            Opcode.RSAVE -> {
                // save cpu status flag and all registers A, X, Y.
                // see http://6502.org/tutorials/register_preservation.html
                " php |  sta  ${C64Zeropage.SCRATCH_REG} | pha  | txa  | pha  | tya  | pha  | lda  ${C64Zeropage.SCRATCH_REG}"
            }
            Opcode.RRESTORE -> {
                // restore all registers and cpu status flag
                " pla |  tay |  pla |  tax |  pla |  plp"
            }
            Opcode.RSAVEX -> " sta  ${C64Zeropage.SCRATCH_REG} |  txa |  pha |  lda  ${C64Zeropage.SCRATCH_REG}"
            Opcode.RRESTOREX -> " sta  ${C64Zeropage.SCRATCH_REG} |  pla |  tax |  lda  ${C64Zeropage.SCRATCH_REG}"
            Opcode.DISCARD_BYTE -> " inx"
            Opcode.DISCARD_WORD -> " inx"
            Opcode.DISCARD_FLOAT -> " inx |  inx |  inx"
            Opcode.INLINE_ASSEMBLY ->  "@inline@" + (ins.callLabel2 ?: "")      // All of the inline assembly is stored in the calllabel2 property. the '@inline@' is a special marker to process it.
            Opcode.INCLUDE_FILE -> {
                val offset = if(ins.arg==null) "" else ", ${ins.arg.integerValue()}"
                val length = if(ins.arg2==null) "" else ", ${ins.arg2.integerValue()}"
                " .binary  \"${ins.callLabel}\" $offset $length"
            }
            Opcode.SYSCALL -> {
                if (ins.arg!!.numericValue() in syscallsForStackVm.map { it.callNr })
                    throw CompilerException("cannot translate vm syscalls to real assembly calls - use *real* subroutine calls instead. Syscall ${ins.arg.numericValue()}")
                val call = Syscall.values().find { it.callNr==ins.arg.numericValue() }
                when(call) {
                    Syscall.FUNC_SIN,
                    Syscall.FUNC_COS,
                    Syscall.FUNC_ABS,
                    Syscall.FUNC_TAN,
                    Syscall.FUNC_ATAN,
                    Syscall.FUNC_LN,
                    Syscall.FUNC_LOG2,
                    Syscall.FUNC_SQRT,
                    Syscall.FUNC_RAD,
                    Syscall.FUNC_DEG,
                    Syscall.FUNC_ROUND,
                    Syscall.FUNC_FLOOR,
                    Syscall.FUNC_CEIL,
                    Syscall.FUNC_RNDF,
                    Syscall.FUNC_ANY_F,
                    Syscall.FUNC_ALL_F,
                    Syscall.FUNC_MAX_F,
                    Syscall.FUNC_MIN_F,
                    Syscall.FUNC_SUM_F -> " jsr  c64flt.${call.name.toLowerCase()}"
                    null -> ""
                    else -> " jsr  prog8_lib.${call.name.toLowerCase()}"
                }
            }
            Opcode.BREAKPOINT -> {
                breakpointCounter++
                "_prog8_breakpoint_$breakpointCounter\tnop"
            }

            Opcode.PUSH_BYTE -> {
                " lda  #${hexVal(ins)} |  sta  ${ESTACK_LO.toHex()},x |  dex"
            }
            Opcode.PUSH_WORD -> {
                val value = hexVal(ins)
                " lda  #<$value |  sta  ${ESTACK_LO.toHex()},x |  lda  #>$value |  sta  ${ESTACK_HI.toHex()},x |  dex"
            }
            Opcode.PUSH_FLOAT -> {
                val floatConst = getFloatConst(ins.arg!!)
                " lda  #<$floatConst |  ldy  #>$floatConst |  jsr  c64flt.push_float"
            }
            Opcode.PUSH_VAR_BYTE -> {
                when(ins.callLabel) {
                    "X" -> throw CompilerException("makes no sense to push X, it's used as a stack pointer itself. You should probably not use the X register (or only in trivial assignments)")
                    "A" -> " sta  ${ESTACK_LO.toHex()},x |  dex"
                    "Y" -> " tya |  sta  ${ESTACK_LO.toHex()},x |  dex"
                    else -> " lda  ${ins.callLabel} |  sta  ${ESTACK_LO.toHex()},x |  dex"
                }
            }
            Opcode.PUSH_VAR_WORD -> {
                " lda  ${ins.callLabel} |  sta  ${ESTACK_LO.toHex()},x |  lda  ${ins.callLabel}+1 |    sta  ${ESTACK_HI.toHex()},x |  dex"
            }
            Opcode.PUSH_VAR_FLOAT -> " lda  #<${ins.callLabel} |  ldy  #>${ins.callLabel}|  jsr  c64flt.push_float"
            Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB -> {
                """
                lda  ${hexVal(ins)}
                sta  ${ESTACK_LO.toHex()},x
                dex
                """
            }
            Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UW -> {
                """
                lda  ${hexVal(ins)}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${hexValPlusOne(ins)}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            }
            Opcode.PUSH_MEM_FLOAT -> {
                " lda  #<${hexVal(ins)} |  ldy  #>${hexVal(ins)}|  jsr  c64flt.push_float"
            }
            Opcode.PUSH_MEMREAD -> {
                """
                lda  ${(ESTACK_LO+1).toHex()},x
                sta  (+) +1
                lda  ${(ESTACK_HI+1).toHex()},x
                sta  (+) +2
+               lda  65535    ; modified
                sta  ${(ESTACK_LO+1).toHex()},x
                """
            }

            Opcode.PUSH_REGAY_WORD -> {
                " sta  ${ESTACK_LO.toHex()},x |  tya |  sta  ${ESTACK_HI.toHex()},x |  dex "
            }
            Opcode.PUSH_ADDR_HEAPVAR -> {
                " lda  #<${ins.callLabel} |  sta  ${ESTACK_LO.toHex()},x |  lda  #>${ins.callLabel}  |  sta  ${ESTACK_HI.toHex()},x |  dex"
            }
            Opcode.POP_REGAX_WORD -> throw AssemblyError("cannot load X register from stack because it's used as the stack pointer itself")
            Opcode.POP_REGXY_WORD -> throw AssemblyError("cannot load X register from stack because it's used as the stack pointer itself")
            Opcode.POP_REGAY_WORD -> {
                " inx |  lda  ${ESTACK_LO.toHex()},x |  ldy  ${ESTACK_HI.toHex()},x "
            }

            Opcode.READ_INDEXED_VAR_BYTE -> {
                """
                ldy  ${(ESTACK_LO+1).toHex()},x
                lda  ${ins.callLabel},y
                sta  ${(ESTACK_LO+1).toHex()},x
                """
            }
            Opcode.READ_INDEXED_VAR_WORD -> {
                """
                lda  ${(ESTACK_LO+1).toHex()},x
                asl  a
                tay
                lda  ${ins.callLabel},y
                sta  ${(ESTACK_LO+1).toHex()},x
                lda  ${ins.callLabel}+1,y
                sta  ${(ESTACK_HI+1).toHex()},x
                """
            }
            Opcode.READ_INDEXED_VAR_FLOAT -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.push_float_from_indexed_var
                """
            }
            Opcode.WRITE_INDEXED_VAR_BYTE -> {
                """
                inx
                ldy  ${ESTACK_LO.toHex()},x
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  ${ins.callLabel},y
                """
            }
            Opcode.WRITE_INDEXED_VAR_WORD -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                asl  a
                tay
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  ${ins.callLabel},y
                lda  ${ESTACK_HI.toHex()},x
                sta  ${ins.callLabel}+1,y
                """
            }
            Opcode.WRITE_INDEXED_VAR_FLOAT -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.pop_float_to_indexed_var
                """
            }
            Opcode.POP_MEM_BYTE -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  ${hexVal(ins)}
                """
            }
            Opcode.POP_MEM_WORD -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  ${hexVal(ins)}
                lda  ${ESTACK_HI.toHex()},x
                sta  ${hexValPlusOne(ins)}
                """
            }
            Opcode.POP_MEM_FLOAT -> {
                " lda  ${hexVal(ins)} |  ldy  ${hexValPlusOne(ins)} |  jsr  c64flt.pop_float"
            }
            Opcode.POP_MEMWRITE -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  (+) +1
                lda  ${ESTACK_HI.toHex()},x
                sta  (+) +2
                inx
                lda  ${ESTACK_LO.toHex()},x
+               sta  65535       ; modified
                """
            }

            Opcode.POP_VAR_BYTE -> {
                when (ins.callLabel) {
                    "X" -> throw CompilerException("makes no sense to pop X, it's used as a stack pointer itself")
                    "A" -> " inx |  lda  ${ESTACK_LO.toHex()},x"
                    "Y" -> " inx |  ldy  ${ESTACK_LO.toHex()},x"
                    else -> " inx |  lda  ${ESTACK_LO.toHex()},x |  sta  ${ins.callLabel}"
                }
            }
            Opcode.POP_VAR_WORD -> {
                " inx |  lda  ${ESTACK_LO.toHex()},x |  ldy  ${ESTACK_HI.toHex()},x |  sta  ${ins.callLabel} |  sty  ${ins.callLabel}+1"
            }
            Opcode.POP_VAR_FLOAT -> {
                " lda  #<${ins.callLabel} |  ldy  #>${ins.callLabel} |  jsr  c64flt.pop_float"
            }

            Opcode.INC_VAR_UB, Opcode.INC_VAR_B -> {
                when (ins.callLabel) {
                    "A" -> " clc |  adc  #1"
                    "X" -> " inx"
                    "Y" -> " iny"
                    else -> " inc  ${ins.callLabel}"
                }
            }
            Opcode.INC_VAR_UW, Opcode.INC_VAR_W -> {
                " inc  ${ins.callLabel} |  bne  + |  inc  ${ins.callLabel}+1 |+"
            }
            Opcode.INC_VAR_F -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.inc_var_f
                """
            }
            Opcode.POP_INC_MEMORY -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  (+) +1
                lda  ${ESTACK_HI.toHex()},x
                sta  (+) +2
+               inc  65535     ; modified
                """
            }
            Opcode.POP_DEC_MEMORY -> {
                """
                inx
                lda  ${ESTACK_LO.toHex()},x
                sta  (+) +1
                lda  ${ESTACK_HI.toHex()},x
                sta  (+) +2
+               dec  65535     ; modified
                """
            }
            Opcode.DEC_VAR_UB, Opcode.DEC_VAR_B -> {
                when (ins.callLabel) {
                    "A" -> " sec |  sbc  #1"
                    "X" -> " dex"
                    "Y" -> " dey"
                    else -> " dec  ${ins.callLabel}"
                }
            }
            Opcode.DEC_VAR_UW, Opcode.DEC_VAR_W -> {
                " lda  ${ins.callLabel} |  bne  + |  dec  ${ins.callLabel}+1 |+ |  dec  ${ins.callLabel}"
            }
            Opcode.DEC_VAR_F -> {
                """
                lda  #<${ins.callLabel}
                ldy  #>${ins.callLabel}
                jsr  c64flt.dec_var_f
                """
            }
            Opcode.INC_MEMORY -> " inc  ${hexVal(ins)}"
            Opcode.DEC_MEMORY -> " dec  ${hexVal(ins)}"
            Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB -> " inx |  txa |  pha |  lda  ${ESTACK_LO.toHex()},x |  tax |  inc  ${ins.callLabel},x |  pla |  tax"
            Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB -> " inx |  txa |  pha |  lda  ${ESTACK_LO.toHex()},x |  tax |  dec  ${ins.callLabel},x |  pla |  tax"

            Opcode.NEG_B -> " jsr  prog8_lib.neg_b"
            Opcode.NEG_W -> " jsr  prog8_lib.neg_w"
            Opcode.NEG_F -> " jsr  c64flt.neg_f"
            Opcode.ABS_B -> " jsr  prog8_lib.abs_b"
            Opcode.ABS_W -> " jsr  prog8_lib.abs_w"
            Opcode.ABS_F -> " jsr  c64flt.abs_f"
            Opcode.POW_F -> " jsr  c64flt.pow_f"
            Opcode.INV_BYTE -> {
                """
                lda  ${(ESTACK_LO + 1).toHex()},x
                eor  #255
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.INV_WORD -> " jsr  prog8_lib.inv_word"
            Opcode.NOT_BYTE -> " jsr  prog8_lib.not_byte"
            Opcode.NOT_WORD -> " jsr  prog8_lib.not_word"
            Opcode.BCS -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bcs  $label"
            }
            Opcode.BCC -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bcc  $label"
            }
            Opcode.BNEG -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bmi  $label"
            }
            Opcode.BPOS -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bpl  $label"
            }
            Opcode.BVC -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bvc  $label"
            }
            Opcode.BVS -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bvs  $label"
            }
            Opcode.BZ -> {
                val label = ins.callLabel ?: hexVal(ins)
                " beq  $label"
            }
            Opcode.BNZ -> {
                val label = ins.callLabel ?: hexVal(ins)
                " bne  $label"
            }
            Opcode.JZ -> {
                val label = ins.callLabel ?: hexVal(ins)
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                beq  $label
                """
            }
            Opcode.JZW -> {
                val label = ins.callLabel ?: hexVal(ins)
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                beq  $label
                lda  ${(ESTACK_HI).toHex()},x
                beq  $label
                """
            }
            Opcode.JNZ -> {
                val label = ins.callLabel ?: hexVal(ins)
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                bne  $label
                """
            }
            Opcode.JNZW -> {
                val label = ins.callLabel ?: hexVal(ins)
                """
                inx
                lda  ${(ESTACK_LO).toHex()},x
                bne  $label
                lda  ${(ESTACK_HI).toHex()},x
                bne  $label
                """
            }
            Opcode.CAST_B_TO_UB -> ""  // is a no-op, just carry on with the byte as-is
            Opcode.CAST_UB_TO_B -> ""  // is a no-op, just carry on with the byte as-is
            Opcode.CAST_W_TO_UW -> ""  // is a no-op, just carry on with the word as-is
            Opcode.CAST_UW_TO_W -> ""  // is a no-op, just carry on with the word as-is
            Opcode.CAST_W_TO_UB -> ""  // is a no-op, just carry on with the lsb of the word as-is
            Opcode.CAST_W_TO_B -> ""  // is a no-op, just carry on with the lsb of the word as-is
            Opcode.CAST_UW_TO_UB -> ""  // is a no-op, just carry on with the lsb of the uword as-is
            Opcode.CAST_UW_TO_B -> ""  // is a no-op, just carry on with the lsb of the uword as-is
            Opcode.CAST_UB_TO_F -> " jsr  c64flt.stack_ub2float"
            Opcode.CAST_B_TO_F -> " jsr  c64flt.stack_b2float"
            Opcode.CAST_UW_TO_F -> " jsr  c64flt.stack_uw2float"
            Opcode.CAST_W_TO_F -> " jsr  c64flt.stack_w2float"
            Opcode.CAST_F_TO_UB -> " jsr  c64flt.stack_float2uw"
            Opcode.CAST_F_TO_B -> " jsr  c64flt.stack_float2w"
            Opcode.CAST_F_TO_UW -> " jsr  c64flt.stack_float2uw"
            Opcode.CAST_F_TO_W -> " jsr  c64flt.stack_float2w"
            Opcode.CAST_UB_TO_UW, Opcode.CAST_UB_TO_W -> " lda  #0 |  sta  ${(ESTACK_HI+1).toHex()},x"     // clear the msb
            Opcode.CAST_B_TO_UW, Opcode.CAST_B_TO_W -> " lda  ${(ESTACK_LO+1).toHex()},x |  ${signExtendA("${(ESTACK_HI+1).toHex()},x")}"     // sign extend the lsb
            Opcode.MSB -> " lda  ${(ESTACK_HI+1).toHex()},x |  sta  ${(ESTACK_LO+1).toHex()},x"
            Opcode.MKWORD -> " inx |  lda  ${ESTACK_LO.toHex()},x |  sta  ${(ESTACK_HI+1).toHex()},x "

            Opcode.ADD_UB, Opcode.ADD_B -> {        // TODO inline better (pattern with more opcodes)
                """
                lda  ${(ESTACK_LO + 2).toHex()},x
                clc
                adc  ${(ESTACK_LO + 1).toHex()},x
                inx
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.SUB_UB, Opcode.SUB_B -> {        // TODO inline better (pattern with more opcodes)
                """
                lda  ${(ESTACK_LO + 2).toHex()},x
                sec
                sbc  ${(ESTACK_LO + 1).toHex()},x
                inx
                sta  ${(ESTACK_LO + 1).toHex()},x
                """
            }
            Opcode.ADD_W, Opcode.ADD_UW -> "  jsr  prog8_lib.add_w"
            Opcode.SUB_W, Opcode.SUB_UW -> "  jsr  prog8_lib.sub_w"
            Opcode.MUL_B, Opcode.MUL_UB -> "  jsr  prog8_lib.mul_byte"
            Opcode.MUL_W, Opcode.MUL_UW -> "  jsr  prog8_lib.mul_word"
            Opcode.MUL_F -> "  jsr  c64flt.mul_f"
            Opcode.ADD_F -> "  jsr  c64flt.add_f"
            Opcode.SUB_F -> "  jsr  c64flt.sub_f"
            Opcode.DIV_F -> "  jsr  c64flt.div_f"
            Opcode.IDIV_UB -> "  jsr  prog8_lib.idiv_ub"
            Opcode.IDIV_B -> "  jsr  prog8_lib.idiv_b"
            Opcode.IDIV_W -> "  jsr  prog8_lib.idiv_w"
            Opcode.IDIV_UW -> "  jsr  prog8_lib.idiv_uw"

            Opcode.AND_BYTE -> "  jsr  prog8_lib.and_b"
            Opcode.OR_BYTE -> "  jsr  prog8_lib.or_b"
            Opcode.XOR_BYTE -> "  jsr  prog8_lib.xor_b"
            Opcode.AND_WORD -> "  jsr  prog8_lib.and_w"
            Opcode.OR_WORD -> "  jsr  prog8_lib.or_w"
            Opcode.XOR_WORD -> "  jsr  prog8_lib.xor_w"

            Opcode.BITAND_BYTE -> "  jsr  prog8_lib.bitand_b"
            Opcode.BITOR_BYTE -> "  jsr  prog8_lib.bitor_b"
            Opcode.BITXOR_BYTE -> "  jsr  prog8_lib.bitxor_b"
            Opcode.BITAND_WORD -> "  jsr  prog8_lib.bitand_w"
            Opcode.BITOR_WORD -> "  jsr  prog8_lib.bitor_w"
            Opcode.BITXOR_WORD -> "  jsr  prog8_lib.bitxor_w"

            Opcode.REMAINDER_UB -> "  jsr prog8_lib.remainder_ub"
            Opcode.REMAINDER_UW -> "  jsr prog8_lib.remainder_uw"

            Opcode.GREATER_B -> "  jsr prog8_lib.greater_b"
            Opcode.GREATER_UB -> "  jsr prog8_lib.greater_ub"
            Opcode.GREATER_W -> "  jsr prog8_lib.greater_w"
            Opcode.GREATER_UW -> "  jsr prog8_lib.greater_uw"
            Opcode.GREATER_F -> "  jsr c64flt.greater_f"

            Opcode.GREATEREQ_B -> "  jsr prog8_lib.greatereq_b"
            Opcode.GREATEREQ_UB -> "  jsr prog8_lib.greatereq_ub"
            Opcode.GREATEREQ_W -> "  jsr prog8_lib.greatereq_w"
            Opcode.GREATEREQ_UW -> "  jsr prog8_lib.greatereq_uw"
            Opcode.GREATEREQ_F -> "  jsr c64flt.greatereq_f"

            Opcode.EQUAL_BYTE -> "  jsr prog8_lib.equal_b"
            Opcode.EQUAL_WORD -> "  jsr prog8_lib.equal_w"
            Opcode.EQUAL_F -> "  jsr c64flt.equal_f"
            Opcode.NOTEQUAL_BYTE -> "  jsr prog8_lib.notequal_b"
            Opcode.NOTEQUAL_WORD -> "  jsr prog8_lib.notequal_w"
            Opcode.NOTEQUAL_F -> "  jsr c64flt.notequal_f"

            Opcode.LESS_UB -> "  jsr  prog8_lib.less_ub"
            Opcode.LESS_B -> "  jsr  prog8_lib.less_b"
            Opcode.LESS_UW -> "  jsr  prog8_lib.less_uw"
            Opcode.LESS_W -> "  jsr  prog8_lib.less_w"
            Opcode.LESS_F -> "  jsr  c64flt.less_f"

            Opcode.LESSEQ_UB -> "  jsr  prog8_lib.lesseq_ub"
            Opcode.LESSEQ_B -> "  jsr  prog8_lib.lesseq_b"
            Opcode.LESSEQ_UW -> "  jsr  prog8_lib.lesseq_uw"
            Opcode.LESSEQ_W -> "  jsr  prog8_lib.lesseq_w"
            Opcode.LESSEQ_F -> "  jsr  c64flt.lesseq_f"

            Opcode.SHIFTEDL_BYTE -> "  asl  ${(ESTACK_LO+1).toHex()},x"
            Opcode.SHIFTEDL_WORD -> "  asl  ${(ESTACK_LO+1).toHex()},x |  rol  ${(ESTACK_HI+1).toHex()},x"
            Opcode.SHIFTEDR_SBYTE -> "  lda  ${(ESTACK_LO+1).toHex()},x |  asl  a |  ror  ${(ESTACK_LO+1).toHex()},x"
            Opcode.SHIFTEDR_UBYTE -> "  lsr  ${(ESTACK_LO+1).toHex()},x"
            Opcode.SHIFTEDR_SWORD -> "  lda  ${(ESTACK_HI+1).toHex()},x |  asl a  |  ror  ${(ESTACK_HI+1).toHex()},x |  ror  ${(ESTACK_LO+1).toHex()},x"
            Opcode.SHIFTEDR_UWORD -> "  lsr  ${(ESTACK_HI+1).toHex()},x |  ror  ${(ESTACK_LO+1).toHex()},x"

            else -> null
        }
    }

    private fun optimizedIntMultiplicationsOnStack(mulIns: Instruction, amount: Int): String? {

        if(mulIns.opcode == Opcode.MUL_B || mulIns.opcode==Opcode.MUL_UB) {
            if(amount in setOf(0,1,2,4,8,16,32,64,128,256))
                printWarning("multiplication by power of 2 should have been optimized into a left shift instruction: $mulIns $amount")
            if(amount in setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40))
                return " jsr  math.mul_byte_$amount"
            if(mulIns.opcode == Opcode.MUL_B && amount in setOf(-3,-5,-6,-7,-9,-10,-11,-12,-13,-14,-15,-20,-25,-40))
                return " jsr  prog8_lib.neg_b |  jsr  math.mul_byte_${-amount}"
        }
        else if(mulIns.opcode == Opcode.MUL_UW) {
            if(amount in setOf(0,1,2,4,8,16,32,64,128,256))
                printWarning("multiplication by power of 2 should have been optimized into a left shift instruction: $mulIns $amount")
            if(amount in setOf(3,5,6,7,9,10,12,15,20,25,40))
                return " jsr  math.mul_word_$amount"
        }
        else if(mulIns.opcode == Opcode.MUL_W) {
            if(amount in setOf(0,1,2,4,8,16,32,64,128,256))
                printWarning("multiplication by power of 2 should have been optimized into a left shift instruction: $mulIns $amount")
            if(amount in setOf(3,5,6,7,9,10,12,15,20,25,40))
                return " jsr  math.mul_word_$amount"
            if(amount in setOf(-3,-5,-6,-7,-9,-10,-12,-15,-20,-25,-40))
                return " jsr  prog8_lib.neg_w |  jsr  math.mul_word_${-amount}"
        }

        return null
    }

    private fun findPatterns(segment: List<Instruction>): List<AsmFragment> {
        val opcodes = segment.map { it.opcode }
        val result = mutableListOf<AsmFragment>()

        // check for operations that modify a single value, by putting it on the stack (and popping it afterwards)
        if((opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[2]==Opcode.POP_VAR_BYTE) ||
                (opcodes[0]==Opcode.PUSH_VAR_WORD && opcodes[2]==Opcode.POP_VAR_WORD) ||
                (opcodes[0]==Opcode.PUSH_VAR_FLOAT && opcodes[2]==Opcode.POP_VAR_FLOAT)) {
            if (segment[0].callLabel == segment[2].callLabel) {
                val fragment = sameVarOperation(segment[0].callLabel!!, segment[1])
                if (fragment != null) {
                    fragment.segmentSize = 3
                    result.add(fragment)
                }
            }
        }
        else if((opcodes[0]==Opcode.PUSH_BYTE && opcodes[1] in setOf(Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB,
                        Opcode.INC_INDEXED_VAR_UW, Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_FLOAT,
                        Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB, Opcode.DEC_INDEXED_VAR_W,
                        Opcode.DEC_INDEXED_VAR_UW, Opcode.DEC_INDEXED_VAR_FLOAT))) {
            val fragment = sameConstantIndexedVarOperation(segment[1].callLabel!!, segment[0].arg!!.integerValue(), segment[1])
            if(fragment!=null) {
                fragment.segmentSize=2
                result.add(fragment)
            }
        }
        else if((opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[1] in setOf(Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB,
                        Opcode.INC_INDEXED_VAR_UW, Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_FLOAT,
                        Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB, Opcode.DEC_INDEXED_VAR_W,
                        Opcode.DEC_INDEXED_VAR_UW, Opcode.DEC_INDEXED_VAR_FLOAT))) {
            val fragment = sameIndexedVarOperation(segment[1].callLabel!!, segment[0].callLabel!!, segment[1])
            if(fragment!=null) {
                fragment.segmentSize=2
                result.add(fragment)
            }
        }
        else if((opcodes[0]==Opcode.PUSH_MEM_UB && opcodes[2]==Opcode.POP_MEM_BYTE) ||
                (opcodes[0]==Opcode.PUSH_MEM_B && opcodes[2]==Opcode.POP_MEM_BYTE) ||
                (opcodes[0]==Opcode.PUSH_MEM_UW && opcodes[2]==Opcode.POP_MEM_WORD) ||
                (opcodes[0]==Opcode.PUSH_MEM_W && opcodes[2]==Opcode.POP_MEM_WORD) ||
                (opcodes[0]==Opcode.PUSH_MEM_FLOAT && opcodes[2]==Opcode.POP_MEM_FLOAT)) {
            if(segment[0].arg==segment[2].arg) {
                val fragment = sameMemOperation(segment[0].arg!!.integerValue(), segment[1])
                if(fragment!=null) {
                    fragment.segmentSize = 3
                    result.add(fragment)
                }
            }
        }
        else if((opcodes[0]==Opcode.PUSH_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_BYTE &&
                        opcodes[3]==Opcode.PUSH_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_BYTE) ||
                (opcodes[0]==Opcode.PUSH_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_WORD &&
                        opcodes[3]==Opcode.PUSH_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_WORD)) {
            if(segment[0].arg==segment[3].arg && segment[1].callLabel==segment[4].callLabel) {
                val fragment = sameConstantIndexedVarOperation(segment[1].callLabel!!, segment[0].arg!!.integerValue(), segment[2])
                if(fragment!=null){
                    fragment.segmentSize = 5
                    result.add(fragment)
                }
            }
        }
        else if((opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_BYTE &&
                        opcodes[3]==Opcode.PUSH_VAR_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_BYTE) ||
                (opcodes[0]==Opcode.PUSH_VAR_BYTE && opcodes[1]==Opcode.READ_INDEXED_VAR_WORD &&
                        opcodes[3]==Opcode.PUSH_VAR_BYTE && opcodes[4]==Opcode.WRITE_INDEXED_VAR_WORD)) {
            if(segment[0].callLabel==segment[3].callLabel && segment[1].callLabel==segment[4].callLabel) {
                val fragment = sameIndexedVarOperation(segment[1].callLabel!!, segment[0].callLabel!!, segment[2])
                if(fragment!=null){
                    fragment.segmentSize = 5
                    result.add(fragment)
                }
            }
        }

        // add any matching patterns from the big list
        for(pattern in patterns) {
            if(pattern.sequence.size > segment.size || (pattern.altSequence!=null && pattern.altSequence.size > segment.size))
                continue        //  don't process patterns that don't fit
            val opcodesList = opcodes.subList(0, pattern.sequence.size)
            if(pattern.sequence == opcodesList) {
                val asm = pattern.asm(segment)
                if(asm!=null)
                    result.add(AsmFragment(asm, pattern.sequence.size))
            } else if(pattern.altSequence!=null) {
                val opcodesListAlt = opcodes.subList(0, pattern.altSequence.size)
                if(pattern.altSequence == opcodesListAlt) {
                    val asm = pattern.asm(segment)
                    if (asm != null)
                        result.add(AsmFragment(asm, pattern.sequence.size))
                }
            }
        }

        return result
    }

    private fun sameConstantIndexedVarOperation(variable: String, index: Int, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-value / op / push-index-value / pop-into-indexed-var
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" asl  $variable+$index", 8)
            Opcode.SHR_UBYTE -> AsmFragment(" lsr  $variable+$index", 8)
            Opcode.SHR_SBYTE -> AsmFragment(" lda  $variable+$index |  asl  a |  ror  $variable+$index")
            Opcode.SHL_WORD -> AsmFragment(" asl  $variable+${index*2+1} |  rol  $variable+${index*2}", 8)
            Opcode.SHR_UWORD -> AsmFragment(" lsr  $variable+${index*2+1} |  ror  $variable+${index*2}", 8)
            Opcode.SHR_SWORD -> AsmFragment(" lda  $variable+${index*2+1} |  asl  a |  ror  $variable+${index*2+1} |  ror  $variable+${index*2}", 8)
            Opcode.ROL_BYTE -> AsmFragment(" rol  $variable+$index", 8)
            Opcode.ROR_BYTE -> AsmFragment(" ror  $variable+$index", 8)
            Opcode.ROL_WORD -> AsmFragment(" rol  $variable+${index*2+1} |  rol  $variable+${index*2}", 8)
            Opcode.ROR_WORD -> AsmFragment(" ror  $variable+${index*2+1} |  ror  $variable+${index*2}", 8)
            Opcode.ROL2_BYTE -> AsmFragment(" lda  $variable+$index |  cmp  #\$80 |  rol  $variable+$index", 8)
            Opcode.ROR2_BYTE -> AsmFragment(" lda  $variable+$index |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable+$index", 10)
            Opcode.ROL2_WORD -> AsmFragment(" asl  $variable+${index*2+1} |  rol  $variable+${index*2} |  bcc  + |  inc  $variable+${index*2+1} |+",20)
            Opcode.ROR2_WORD -> AsmFragment(" lsr  $variable+${index*2+1} |  ror  $variable+${index*2} |  bcc  + |  lda  $variable+${index*2+1} |  ora  #\$80 |  sta  $variable+${index*2+1} |+", 30)
            Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB -> AsmFragment(" inc  $variable+$index", 2)
            Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB -> AsmFragment(" dec  $variable+$index", 5)
            Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_UW -> AsmFragment(" inc  $variable+${index*2} |  bne  + |  inc  $variable+${index*2+1} |+")
            Opcode.DEC_INDEXED_VAR_W, Opcode.DEC_INDEXED_VAR_UW -> AsmFragment(" lda  $variable+${index*2} |  bne  + |  dec  $variable+${index*2+1} |+ |  dec  $variable+${index*2}")
            Opcode.INC_INDEXED_VAR_FLOAT -> AsmFragment(
                """
                lda  #<($variable+${index*Mflpt5.MemorySize})
                ldy  #>($variable+${index*Mflpt5.MemorySize})
                jsr  c64flt.inc_var_f
                """)
            Opcode.DEC_INDEXED_VAR_FLOAT -> AsmFragment(
                """
                lda  #<($variable+${index*Mflpt5.MemorySize})
                ldy  #>($variable+${index*Mflpt5.MemorySize})
                jsr  c64flt.dec_var_f
                """)

            else -> null
        }
    }

    private fun sameIndexedVarOperation(variable: String, indexVar: String, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-value / op / push-index-var / pop-into-indexed-var
        val saveX = " stx  ${C64Zeropage.SCRATCH_B1} |"
        val restoreX = " | ldx  ${C64Zeropage.SCRATCH_B1}"
        val loadXWord: String
        val loadX: String

        when(indexVar) {
            "X" -> {
                loadX = ""
                loadXWord = " txa |  asl  a |  tax |"
            }
            "Y" -> {
                loadX = " tya |  tax |"
                loadXWord = " tya |  asl  a |  tax |"
            }
            "A" -> {
                loadX = " tax |"
                loadXWord = " asl  a |  tax |"
            }
            else -> {
                // the indexvar is a real variable, not a register
                loadX = " ldx  $indexVar |"
                loadXWord = " lda  $indexVar |  asl  a |  tax |"
            }
        }

        return when (ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" txa |  $loadX  asl  $variable,x |  tax", 10)
            Opcode.SHR_UBYTE -> AsmFragment(" txa |  $loadX  lsr  $variable,x |  tax", 10)
            Opcode.SHR_SBYTE -> AsmFragment("$saveX  $loadX  lda  $variable,x |  asl a |  ror  $variable,x  $restoreX", 10)
            Opcode.SHL_WORD -> AsmFragment("$saveX $loadXWord  asl  $variable,x |  rol  $variable+1,x  $restoreX", 10)
            Opcode.SHR_UWORD -> AsmFragment("$saveX $loadXWord  lsr  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.SHR_SWORD -> AsmFragment("$saveX $loadXWord  lda  $variable+1,x |  asl a |  ror  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.ROL_BYTE -> AsmFragment(" txa |  $loadX  rol  $variable,x |  tax", 10)
            Opcode.ROR_BYTE -> AsmFragment(" txa |  $loadX  ror  $variable,x |  tax", 10)
            Opcode.ROL_WORD -> AsmFragment("$saveX $loadXWord  rol  $variable,x |  rol  $variable+1,x  $restoreX", 10)
            Opcode.ROR_WORD -> AsmFragment("$saveX $loadXWord  ror  $variable+1,x |  ror  $variable,x  $restoreX", 10)
            Opcode.ROL2_BYTE -> AsmFragment("$saveX $loadX  lda  $variable,x |  cmp  #\$80 |  rol  $variable,x  $restoreX", 10)
            Opcode.ROR2_BYTE -> AsmFragment("$saveX $loadX  lda  $variable,x |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable,x  $restoreX", 10)
            Opcode.ROL2_WORD -> AsmFragment(" txa |  $loadXWord  asl  $variable,x |  rol  $variable+1,x |  bcc  + |  inc  $variable,x  |+  |  tax", 30)
            Opcode.ROR2_WORD -> AsmFragment("$saveX $loadXWord  lsr  $variable+1,x |  ror  $variable,x |  bcc  + |  lda  $variable+1,x |  ora  #\$80 |  sta  $variable+1,x |+  $restoreX", 30)
            Opcode.INC_INDEXED_VAR_B, Opcode.INC_INDEXED_VAR_UB -> AsmFragment(" txa |  $loadX  inc  $variable,x |  tax", 10)
            Opcode.DEC_INDEXED_VAR_B, Opcode.DEC_INDEXED_VAR_UB -> AsmFragment(" txa |  $loadX  dec  $variable,x |  tax", 10)
            Opcode.INC_INDEXED_VAR_W, Opcode.INC_INDEXED_VAR_UW -> AsmFragment("$saveX $loadXWord  inc  $variable,x |  bne  + |  inc  $variable+1,x  |+  $restoreX", 10)
            Opcode.DEC_INDEXED_VAR_W, Opcode.DEC_INDEXED_VAR_UW -> AsmFragment("$saveX $loadXWord  lda  $variable,x |  bne  + |  dec  $variable+1,x |+ |  dec  $variable,x  $restoreX", 10)
            Opcode.INC_INDEXED_VAR_FLOAT -> AsmFragment(" lda  #<$variable |  ldy  #>$variable |  $saveX   $loadX   jsr  c64flt.inc_indexed_var_f  $restoreX")
            Opcode.DEC_INDEXED_VAR_FLOAT -> AsmFragment(" lda  #<$variable |  ldy  #>$variable |  $saveX   $loadX   jsr  c64flt.dec_indexed_var_f  $restoreX")

            else -> null
        }
    }

    private fun sameMemOperation(address: Int, ins: Instruction): AsmFragment? {
        // an in place operation that consists of  push-mem / op / pop-mem
        val addr = address.toHex()
        val addrHi = (address+1).toHex()
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> AsmFragment(" asl  $addr", 10)
            Opcode.SHR_UBYTE -> AsmFragment(" lsr  $addr", 10)
            Opcode.SHR_SBYTE -> AsmFragment(" lda  $addr |  asl  a |  ror  $addr", 10)
            Opcode.SHL_WORD -> AsmFragment(" asl  $addr |  rol  $addrHi", 10)
            Opcode.SHR_UWORD -> AsmFragment(" lsr  $addrHi |  ror  $addr", 10)
            Opcode.SHR_SWORD -> AsmFragment(" lda  $addrHi |  asl a |  ror  $addrHi |  ror  $addr", 10)
            Opcode.ROL_BYTE -> AsmFragment(" rol  $addr", 10)
            Opcode.ROR_BYTE -> AsmFragment(" ror  $addr", 10)
            Opcode.ROL_WORD -> AsmFragment(" rol  $addr |  rol  $addrHi", 10)
            Opcode.ROR_WORD -> AsmFragment(" ror  $addrHi |  ror  $addr", 10)
            Opcode.ROL2_BYTE -> AsmFragment(" lda  $addr |  cmp  #\$80 |  rol  $addr", 10)
            Opcode.ROR2_BYTE -> AsmFragment(" lda  $addr |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $addr", 10)
            Opcode.ROL2_WORD -> AsmFragment(" lda  $addr |  cmp #\$80 |  rol  $addr |  rol  $addrHi", 10)
            Opcode.ROR2_WORD -> AsmFragment(" lsr  $addrHi |  ror  $addr |  bcc  + |  lda  $addrHi |  ora  #$80 |  sta  $addrHi |+", 20)
            else -> null
        }
    }

    private fun sameVarOperation(variable: String, ins: Instruction): AsmFragment? {
        // an in place operation that consists of a push-var / op / pop-var
        return when(ins.opcode) {
            Opcode.SHL_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" asl  a", 10)
                    "X" -> AsmFragment(" txa |  asl  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  asl  a |  tay", 10)
                    else -> AsmFragment(" asl  $variable", 10)
                }
            }
            Opcode.SHR_UBYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" lsr  a", 10)
                    "X" -> AsmFragment(" txa |  lsr  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  lsr  a |  tay", 10)
                    else -> AsmFragment(" lsr  $variable", 10)
                }
            }
            Opcode.SHR_SBYTE -> {
                // arithmetic shift right (keep sign bit)
                when (variable) {
                    "A" -> AsmFragment(" cmp  #$80 |  ror  a", 10)
                    "X" -> AsmFragment(" txa |  cmp  #$80 |  ror  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  cmp  #$80 |  ror  a |  tay", 10)
                    else -> AsmFragment(" lda  $variable |  asl  a  | ror  $variable", 10)
                }
            }
            Opcode.SHL_WORD -> {
                AsmFragment(" asl  $variable |  rol  $variable+1", 10)
            }
            Opcode.SHR_UWORD -> {
                AsmFragment(" lsr  $variable+1 |  ror  $variable", 10)
            }
            Opcode.SHR_SWORD -> {
                // arithmetic shift right (keep sign bit)
                AsmFragment(" lda  $variable+1 |  asl  a |  ror  $variable+1 |  ror  $variable", 10)
            }
            Opcode.ROL_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" rol  a", 10)
                    "X" -> AsmFragment(" txa |  rol  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  rol  a |  tay", 10)
                    else -> AsmFragment(" rol  $variable", 10)
                }
            }
            Opcode.ROR_BYTE -> {
                when (variable) {
                    "A" -> AsmFragment(" ror  a", 10)
                    "X" -> AsmFragment(" txa |  ror  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  ror  a |  tay", 10)
                    else -> AsmFragment(" ror  $variable", 10)
                }
            }
            Opcode.ROL_WORD -> {
                AsmFragment(" rol  $variable |  rol  $variable+1", 10)
            }
            Opcode.ROR_WORD -> {
                AsmFragment(" ror  $variable+1 |  ror  $variable", 10)
            }
            Opcode.ROL2_BYTE -> {       // 8-bit rol
                when (variable) {
                    "A" -> AsmFragment(" cmp  #\$80 |  rol  a", 10)
                    "X" -> AsmFragment(" txa |  cmp  #\$80 |  rol  a |  tax", 10)
                    "Y" -> AsmFragment(" tya |  cmp  #\$80 |  rol  a |  tay", 10)
                    else -> AsmFragment(" lda  $variable |  cmp  #\$80  | rol  $variable", 10)
                }
            }
            Opcode.ROR2_BYTE -> {       // 8-bit ror
                when (variable) {
                    "A" -> AsmFragment(" lsr  a | bcc  + |  ora  #\$80  |+", 10)
                    "X" -> AsmFragment(" txa |  lsr  a |  bcc  + |  ora  #\$80  |+ |  tax", 10)
                    "Y" -> AsmFragment(" tya |  lsr  a |  bcc  + |  ora  #\$80  |+ |  tay", 10)
                    else -> AsmFragment(" lda  $variable |  lsr  a |  bcc  + |  ora  #\$80 |+ |  sta  $variable", 10)
                }
            }
            Opcode.ROL2_WORD -> {
                AsmFragment(" lda  $variable |  cmp #\$80 |  rol  $variable |  rol  $variable+1", 10)
            }
            Opcode.ROR2_WORD -> {
                AsmFragment(" lsr  $variable+1 |  ror  $variable |  bcc  + |  lda  $variable+1 |  ora  #\$80 |  sta  $variable+1 |+", 30)
            }
            else -> null
        }
    }

    private class AsmFragment(val asm: String, var segmentSize: Int=0)

    private class AsmPattern(val sequence: List<Opcode>, val altSequence: List<Opcode>?=null, val asm: (List<Instruction>)->String?)

    private fun loadAFromIndexedByVar(idxVarInstr: Instruction, readArrayInstr: Instruction): String {
        // A =  readArrayInstr [ idxVarInstr ]
        return when (idxVarInstr.callLabel) {
            "A" -> " tay |  lda  ${readArrayInstr.callLabel},y"
            "X" -> " txa |  tay |  lda  ${readArrayInstr.callLabel},y"
            "Y" -> " lda  ${readArrayInstr.callLabel},y"
            else -> " ldy  ${idxVarInstr.callLabel} |  lda  ${readArrayInstr.callLabel},y"
        }
    }

    private fun loadAYFromWordIndexedByVar(idxVarInstr: Instruction, readArrayInstr: Instruction): String {
        // AY = readWordArrayInstr [ idxVarInstr ]
        return when(idxVarInstr.callLabel) {
            "A" ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
            "X" ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                txa
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
            "Y" ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                tya
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
            else ->
                """
                stx  ${C64Zeropage.SCRATCH_B1}
                lda  ${idxVarInstr.callLabel}
                asl  a
                tax
                lda  ${readArrayInstr.callLabel},x
                ldy  ${readArrayInstr.callLabel}+1,x
                ldx  ${C64Zeropage.SCRATCH_B1}
                """
        }
    }

    private fun storeAToIndexedByVar(idxVarInstr: Instruction, writeArrayInstr: Instruction): String {
        // writeArrayInstr [ idxVarInstr ] =  A
        return when (idxVarInstr.callLabel) {
            "A" -> " tay |  sta  ${writeArrayInstr.callLabel},y"
            "X" -> " stx  ${C64Zeropage.SCRATCH_B1} |  ldy  ${C64Zeropage.SCRATCH_B1} |  sta  ${writeArrayInstr.callLabel},y"
            "Y" -> " sta  ${writeArrayInstr.callLabel},y"
            else -> " ldy  ${idxVarInstr.callLabel} |  sta  ${writeArrayInstr.callLabel},y"
        }
    }

    private fun intVal(valueInstr: Instruction) = valueInstr.arg!!.integerValue()
    private fun hexVal(valueInstr: Instruction) = valueInstr.arg!!.integerValue().toHex()
    private fun hexValPlusOne(valueInstr: Instruction) = (valueInstr.arg!!.integerValue()+1).toHex()
    private fun signExtendA(into: String) =
            """
            ora  #$7f
            bmi  +
            lda  #0
+           sta  $into
            """

    private val patterns = listOf(
            // ----------- assignment to BYTE VARIABLE ----------------
            // var = (u)bytevalue
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                when (segment[1].callLabel) {
                    "A", "X", "Y" ->
                        " ld${segment[1].callLabel!!.toLowerCase()}  #${hexVal(segment[0])}"
                    else ->
                        " lda  #${hexVal(segment[0])} |  sta  ${segment[1].callLabel}"
                }
            },
            // var = other var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[1].callLabel) {
                    "A" ->
                        when(segment[0].callLabel) {
                            "A" -> null
                            "X" -> "  txa"
                            "Y" -> "  tya"
                            else -> "  lda  ${segment[0].callLabel}"
                        }
                    "X" ->
                        when(segment[0].callLabel) {
                            "A" -> "  tax"
                            "X" -> null
                            "Y" -> "  tya |  tax"
                            else -> "  ldx  ${segment[0].callLabel}"
                        }
                    "Y" ->
                        when(segment[0].callLabel) {
                            "A" -> "  tay"
                            "X" -> "  txa |  tay"
                            "Y" -> null
                            else -> "  ldy  ${segment[0].callLabel}"
                        }
                    else ->
                        when(segment[0].callLabel) {
                            "A" -> "  sta  ${segment[1].callLabel}"
                            "X" -> "  stx  ${segment[1].callLabel}"
                            "Y" -> "  sty  ${segment[1].callLabel}"
                            else -> "  lda  ${segment[0].callLabel} |  sta  ${segment[1].callLabel}"
                        }
                }
            },
            // var = mem (u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.POP_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[1].callLabel) {
                    "A", "X", "Y" -> " ld${segment[1].callLabel!!.toLowerCase()}  ${hexVal(segment[0])}"
                    else -> " lda  ${hexVal(segment[0])} |  sta  ${segment[1].callLabel}"
                }
            },
            // var = (u)bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val index = intVal(segment[0])
                when (segment[2].callLabel) {
                    "A", "X", "Y" ->
                        " ld${segment[2].callLabel!!.toLowerCase()}  ${segment[1].callLabel}+$index"
                    else ->
                        " lda  ${segment[1].callLabel}+$index |  sta  ${segment[2].callLabel}"
                }
            },
            // var = (u)bytearray[indexvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                when(segment[2].callLabel) {
                    "A" -> " $loadByteA"
                    "X" -> " $loadByteA |  tax"
                    "Y" -> " $loadByteA |  tay"
                    else -> " $loadByteA |  sta  ${segment[2].callLabel}"
                }
            },
            // var = (u)bytearray[mem index var]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val loadByteA = " ldy  ${hexVal(segment[0])} |  lda  ${segment[1].callLabel},y"
                when(segment[2].callLabel) {
                    "A" -> " $loadByteA"
                    "X" -> " $loadByteA |  tax"
                    "Y" -> " $loadByteA |  tay"
                    else -> " $loadByteA |  sta  ${segment[2].callLabel}"
                }
            },


            // ----------- assignment to BYTE MEMORY ----------------
            // mem = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                " lda  #${hexVal(segment[0])} |  sta  ${hexVal(segment[1])}"
            },
            // mem = (u)bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                when(segment[0].callLabel) {
                    "A" -> " sta  ${hexVal(segment[1])}"
                    "X" -> " stx  ${hexVal(segment[1])}"
                    "Y" -> " sty  ${hexVal(segment[1])}"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${hexVal(segment[1])}"
                }
            },
            // mem = mem (u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.POP_MEM_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.POP_MEM_BYTE)) { segment ->
                " lda  ${hexVal(segment[0])} |  sta  ${hexVal(segment[1])}"
            },
            // mem = (u)bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                val address = hexVal(segment[2])
                val index = intVal(segment[0])
                " lda  ${segment[1].callLabel}+$index |  sta  $address"
            },
            // mem = (u)bytearray[indexvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                " $loadByteA |  sta  ${hexVal(segment[2])}"
            },
            // mem = (u)bytearray[mem index var]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                """
                ldy  ${hexVal(segment[0])}
                lda  ${segment[1].callLabel},y
                sta  ${hexVal(segment[2])}
                """
            },


            // ----------- assignment to BYTE ARRAY ----------------
            // bytearray[index] = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index = intVal(segment[1])
                val value = hexVal(segment[0])
                " lda  #$value |  sta  ${segment[2].callLabel}+$index"
            },
            // bytearray[index] = (u)bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index = intVal(segment[1])
                when(segment[0].callLabel) {
                    "A" -> " sta  ${segment[2].callLabel}+$index"
                    "X" -> " stx  ${segment[2].callLabel}+$index"
                    "Y" -> " sty  ${segment[2].callLabel}+$index"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index"
                }
            },
            // bytearray[index] = mem(u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index = intVal(segment[1])
                " lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel}+$index"
            },

            // bytearray[index var] = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[1], segment[2])
                " lda  #${hexVal(segment[0])} |  $storeA"
            },
            // (u)bytearray[index var] = (u)bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[1], segment[2])
                when(segment[0].callLabel) {
                    "A" -> " $storeA"
                    "X" -> " txa |  $storeA"
                    "Y" -> " tya |  $storeA"
                    else -> " lda  ${segment[0].callLabel} |  $storeA"
                }
            },
            // (u)bytearray[index var] = mem (u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[1], segment[2])
                " lda  ${hexVal(segment[0])} |  $storeA"
            },

            // bytearray[index mem] = (u)byte value
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                """
                lda  #${hexVal(segment[0])}
                ldy  ${hexVal(segment[1])}
                sta  ${segment[2].callLabel},y
                """
            },
            // bytearray[index mem] = (u)byte var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val loadY = " ldy  ${hexVal(segment[1])}"
                when(segment[0].callLabel) {
                    "A" -> " $loadY |  sta  ${segment[2].callLabel},y"
                    "X" -> " txa |  $loadY |  sta  ${segment[2].callLabel},y"
                    "Y" -> " tya |  $loadY |  sta  ${segment[2].callLabel},y"
                    else -> " lda  ${segment[0].callLabel} |  $loadY |  sta  ${segment[2].callLabel},y"
                }
            },
            // bytearray[index mem] = mem(u)byte
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                """
                ldy  ${hexVal(segment[1])}
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                """
            },

            // (u)bytearray2[index2] = (u)bytearray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment->
                val index1 = intVal(segment[0])
                val index2 = intVal(segment[2])
                " lda  ${segment[1].callLabel}+$index1 |  sta  ${segment[3].callLabel}+$index2"
            },
            // (u)bytearray2[index2] = (u)bytearray[indexvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                val index2 = intVal(segment[2])
                " $loadByteA |  sta  ${segment[3].callLabel}+$index2"
            },
            // (u)bytearray[index2] = (u)bytearray[mem ubyte]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index2 = intVal(segment[2])
                """
                ldy  ${hexVal(segment[0])}
                lda  ${segment[1].callLabel},y
                sta  ${segment[3].callLabel}+$index2
                """
            },

            // (u)bytearray2[idxvar2] = (u)bytearray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[2], segment[3])
                val index1 = intVal(segment[0])
                " lda  ${segment[1].callLabel}+$index1 |  $storeA"
            },
            // (u)bytearray2[idxvar2] = (u)bytearray1[idxvar]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                val storeA = storeAToIndexedByVar(segment[2], segment[3])
                " $loadA |  $storeA"
            },
            // (u)bytearray2[idxvar2] = (u)bytearray1[mem ubyte]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val storeA = storeAToIndexedByVar(segment[2], segment[3])
                " ldy  ${hexVal(segment[0])} |  lda  ${segment[1].callLabel},y |  $storeA"
            },

            // (u)bytearray2[index mem] = (u)bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val index1 = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index1
                ldy  ${hexVal(segment[2])}
                sta  ${segment[3].callLabel},y
                """
            },



            // ----------- assignment to WORD VARIABLE ----------------
            // var = wordvalue
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val number = hexVal(segment[0])
                """
                lda  #<$number
                ldy  #>$number
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" -> " sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                    "X" -> " stx  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                    "Y" -> " sty  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
                }
            },
            // var = other var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                ldy  ${segment[0].callLabel}+1
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var = address-of other var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
                " lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel} |  lda  #0 |  sta  ${segment[2].callLabel}+1"
            },
            // var = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[1].callLabel}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[1].callLabel}+1
                """
            },
            // var = ubytearray[index_byte]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
                val index = hexVal(segment[0])
                " lda  ${segment[1].callLabel}+$index |  sta  ${segment[3].callLabel} |  lda  #0 |  sta  ${segment[3].callLabel}+1"
            },
            // var = ubytearray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                " $loadA |  sta  ${segment[3].callLabel} |  lda  #0 |  sta  ${segment[3].callLabel}+1"
            },
            // var = ubytearray[index mem]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                lda  #0
                sta  ${segment[3].callLabel}+1
                """
            },
            // var = (u)wordarray[index_byte]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val index = intVal(segment[0])*2
                " lda  ${segment[1].callLabel}+$index |  sta  ${segment[2].callLabel} |  lda  ${segment[1].callLabel}+${index+1} |  sta  ${segment[2].callLabel}+1"
            },
            // var = (u)wordarray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val loadAY = loadAYFromWordIndexedByVar(segment[0], segment[1])
                """
                $loadAY
                sta  ${segment[2].callLabel}
                sty  ${segment[2].callLabel}+1
                """
            },
            // var = (u)wordarray[index mem]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                asl  a
                tay
                lda  ${segment[1].callLabel},y
                sta  ${segment[2].callLabel}
                lda  ${segment[1].callLabel}+1,y
                sta  ${segment[2].callLabel}+1
                """
            },
            // mem = (u)word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },
            // mem uword = ubyte var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" -> " sta  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                    "X" -> " stx  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                    "Y" -> " sty  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                    else -> " lda  ${segment[0].callLabel} ||  sta  ${hexVal(segment[2])} |  lda  #0 |  sta  ${hexValPlusOne(segment[2])}"
                }
            },
            // mem uword = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[2])}
                lda  #0
                sta  ${hexValPlusOne(segment[2])}
                """
            },
            // mem uword = uword var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                " lda  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1 |  sta  ${hexVal(segment[1])}  |  sty  ${hexValPlusOne(segment[1])}"
            },
            // mem uword = address-of var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_MEM_WORD)) { segment ->
                " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} |  sta  ${hexVal(segment[1])} |  sty  ${hexValPlusOne(segment[1])}"
            },
            // mem (u)word = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                ldy  ${hexValPlusOne(segment[0])}
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },
            // mem uword = ubytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                ldy  #0
                sta  ${hexVal(segment[3])}
                sty  ${hexValPlusOne(segment[3])}
                """
            },
            // mem uword = bytearray[index]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
            },
            // mem uword = bytearray[index var]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                """
                $loadA
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
            },
            // mem uword = bytearray[mem (u)byte]  (sign extended)
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[3])}
                ${signExtendA(hexValPlusOne(segment[3]))}
                """
            },
            // mem uword = ubytearray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
                val loadA = loadAFromIndexedByVar(segment[0], segment[1])
                " $loadA |  sta  ${hexVal(segment[3])} |  lda  #0 |  sta  ${hexValPlusOne(segment[3])}"
            },
            // mem uword = ubytearray[mem (u)bute]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[3])}
                lda  #0
                sta  ${hexValPlusOne(segment[3])}
                """
            },
            // mem uword = (u)wordarray[indexvalue]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                val index = intVal(segment[0])*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+1+$index
                sta  ${hexVal(segment[2])}
                sty  ${hexValPlusOne(segment[2])}
                """
            },
            // mem uword = (u)wordarray[index var]
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                val loadAY = loadAYFromWordIndexedByVar(segment[0], segment[1])
                """
                $loadAY
                sta  ${hexVal(segment[2])}
                sty  ${hexValPlusOne(segment[2])}
                """
            },
            // mem uword = (u)wordarray[mem index]
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                asl  a
                tay
                lda  ${segment[1].callLabel},y
                sta  ${hexVal(segment[2])}
                lda  ${segment[1].callLabel}+1,y
                sta  ${hexValPlusOne(segment[2])}
                """
            },
            // word var = bytevar (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${segment[2].callLabel}
                ${signExtendA(segment[2].callLabel + "+1")}
                """
            },
            // mem word = bytevar (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${hexVal(segment[2])}
                ${signExtendA(hexValPlusOne(segment[2]))}
                """
            },
            // mem word = mem byte (sign extended)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.POP_MEM_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${hexVal(segment[2])}
                ${signExtendA(hexValPlusOne(segment[2]))}
                """
            },
            // var = membyte (sign extended)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel}
                ${signExtendA(segment[2].callLabel + "+1")}
                """
            },
            // var = bytearray[index_byte]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
                val index = hexVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
            },
            // var = bytearray[index var]  (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
                val loadByteA = loadAFromIndexedByVar(segment[0], segment[1])
                """
                $loadByteA
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
            },
            // var = bytearray[mem (u)byte]  (sign extended)
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                ${signExtendA(segment[3].callLabel + "+1")}
                """
            },

            // ----------- assignment to UWORD ARRAY ----------------
            // uwordarray[index] = (u)word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[1])*2
                val value = hexVal(segment[0])
                """
                lda  #<$value
                ldy  #>$value
                sta  ${segment[2].callLabel}+$index
                sty  ${segment[2].callLabel}+${index+1}
                """
            },
            // uwordarray[index mem] = (u)word value
            AsmPattern(
                    listOf(Opcode.PUSH_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val value = hexVal(segment[0])
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  #<$value
                sta  ${segment[2].callLabel},y
                lda  #>$value
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index mem] = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index mem] = (u)word var
            AsmPattern(
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${segment[0].callLabel}
                sta  ${segment[2].callLabel},y
                lda  ${segment[0].callLabel}+1
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index mem] = address-of var
            AsmPattern(
                    listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  #<${segment[0].callLabel}
                sta  ${segment[2].callLabel},y
                lda  #>${segment[0].callLabel}
                sta  ${segment[2].callLabel}+1,y
                """
            },
            // uwordarray[index] = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[2])*2
                when(segment[0].callLabel) {
                    "A" -> " sta  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                    "X" -> " stx  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                    "Y" -> " sty  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[3].callLabel}+$index |  lda  #0 |  sta  ${segment[3].callLabel}+${index+1}"
                }
            },
            // wordarray[index] = bytevar  (extend sign)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[2])*2
                when(segment[0].callLabel) {
                    "A" ->
                        """
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                    "X" ->
                        """
                        txa
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                    "Y" ->
                        """
                        tya
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                    else ->
                        """
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel}+$index
                        ${signExtendA(segment[3].callLabel + "+${index+1}")}
                        """
                }
            },
            // wordarray[index mem] = bytevar  (extend sign)
            AsmPattern(
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" ->
                        """
                        pha
                        sty  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        pla
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                    "X" ->
                        """
                        stx  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${C64Zeropage.SCRATCH_B1}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                    "Y" ->
                        """
                        sty  ${C64Zeropage.SCRATCH_B1}
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${C64Zeropage.SCRATCH_B1}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                    else ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel},y
                        ${signExtendA(segment[3].callLabel + "+1,y")}
                        """
                }
            },
            // wordarray[memory (u)byte] = ubyte mem
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
            },
            // wordarray[index] = mem byte  (extend sign)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[2])*2
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}+$index
                ${signExtendA(segment[3].callLabel + "+${index+1}")}
                """
            },
            // wordarray2[index2] = bytearray1[index1] (extend sign)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                val index2 = segment[3].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel}+$index2
                ${signExtendA(segment[4].callLabel + "+${index2+1}")}
                """
            },
            // wordarray2[mem (u)byte] = bytearray1[index1] (extend sign)
            AsmPattern(
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                """
                lda  ${hexVal(segment[3])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel},y
                ${signExtendA(segment[4].callLabel + "+1,y")}
                """
            },

            // wordarray[indexvar]  = byte  var (sign extended)
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadValueOnStack = when(segment[0].callLabel) {
                    "A" -> " pha"
                    "X" -> " txa |  pha"
                    "Y" -> " tya |  pha"
                    else -> " lda  ${segment[0].callLabel} |  pha"
                }
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                """
                $loadValueOnStack
                $loadIndexY
                pla
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
            },
            // wordarray[indexvar]  = byte  mem (sign extended)
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                """
                $loadIndexY
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                ${signExtendA(segment[3].callLabel + "+1,y")}
                """
            },
            // wordarray[index] = mem word
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[1])*2
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel}+$index
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+$index+1
                """
            },
            // wordarray2[indexvar] = bytearay[index] (sign extended)
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index = intVal(segment[0])
                val loadIndex2Y = when(segment[3].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[3].callLabel} |  asl  a |  tay"
                }
                """
                $loadIndex2Y
                lda  ${segment[1].callLabel}+$index
                sta  ${segment[4].callLabel},y
                lda  ${segment[1].callLabel}+$index+1
                sta  ${segment[4].callLabel}+1,y
                """
            },
            // uwordarray[mem (u)byte] = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[1])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[2].callLabel},y
                lda  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+1,y
                """
            },

            // uwordarray[index mem] = ubytevar
            AsmPattern(
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                when(segment[0].callLabel) {
                    "A" ->
                        """
                        pha
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        pla
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                    "X" ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        txa
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                    "Y" ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        stx  ${C64Zeropage.SCRATCH_B1}
                        tax
                        tya
                        sta  ${segment[3].callLabel},x
                        lda  #0
                        sta  ${segment[3].callLabel}+1,x
                        ldx  ${C64Zeropage.SCRATCH_B1}
                        """
                    else ->
                        """
                        lda  ${hexVal(segment[2])}
                        asl  a
                        tay
                        lda  ${segment[0].callLabel}
                        sta  ${segment[3].callLabel},y
                        lda  #0
                        sta  ${segment[3].callLabel}+1,y
                        """
                }
            },
            // uwordarray[index mem] = ubyte mem
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel},y
                lda  #0
                sta  ${segment[3].callLabel}+1,y
                """
            },
            // uwordarray[index] = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[2])*2
                """
                lda  ${hexVal(segment[0])}
                sta  ${segment[3].callLabel}+$index
                lda  #0
                sta  ${segment[3].callLabel}+${index+1}
                """
            },
            // uwordarray[index] = (u)wordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[1])*2
                " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}+$index |  lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}+${index+1}"
            },
            // uwordarray[index] = address-of var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[1])*2
                " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel}  |  sta  ${segment[2].callLabel}+$index |  sty  ${segment[2].callLabel}+${index+1}"
            },
            // uwordarray[index] = mem uword
            AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val index = intVal(segment[1])*2
                """
                lda  ${hexVal(segment[0])}
                ldy  ${hexValPlusOne(segment[0])}
                sta  ${segment[2].callLabel}+$index
                sty  ${segment[2].callLabel}+${index+1}
                """
            },
            // uwordarray2[index2] = ubytearray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                val index2 = segment[3].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel}+$index2
                lda  #0
                sta  ${segment[4].callLabel}+${index2+1}
                """
            },
            // uwordarray2[index2] = (u)wordarray1[index1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                val index2 = intVal(segment[2])*2
                """
                lda  ${segment[1].callLabel}+$index1
                ldy  ${segment[1].callLabel}+${index1+1}
                sta  ${segment[3].callLabel}+$index2
                sta  ${segment[3].callLabel}+${index2+1}
                """
            },
            // uwordarray[indexvar] = (u)word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val value = hexVal(segment[0])
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  #<$value |  sta  ${segment[2].callLabel},y |  lda  #>$value |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = ubyte  var
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadValueOnStack = when(segment[0].callLabel) {
                    "A" -> " pha"
                    "X" -> " txa |  pha"
                    "Y" -> " tya |  pha"
                    else -> " lda  ${segment[0].callLabel} |  pha"
                }
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                " $loadValueOnStack | $loadIndexY |  pla |  sta  ${segment[3].callLabel},y |  lda  #0 |  sta  ${segment[3].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = uword  var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel},y |  lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = address-of var
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  #<${segment[0].callLabel} |  sta  ${segment[2].callLabel},y |  lda  #>${segment[0].callLabel} |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  ${hexVal(segment[0])} |  sta  ${segment[3].callLabel},y |  lda  #0 |  sta  ${segment[3].callLabel}+1,y"
            },
            // uwordarray[indexvar]  = mem (u)word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val loadIndexY = when(segment[1].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[1].callLabel} |  asl  a |  tay"
                }
                " $loadIndexY |  lda  ${hexVal(segment[0])} |  sta  ${segment[2].callLabel},y |  lda  ${hexValPlusOne(segment[0])} |  sta  ${segment[2].callLabel}+1,y"
            },
            // uwordarray2[indexvar] = ubytearay[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index = intVal(segment[0])
                val loadIndex2Y = when(segment[3].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[3].callLabel} |  asl  a |  tay"
                }
                " $loadIndex2Y |  lda  ${segment[1].callLabel}+$index |  sta  ${segment[4].callLabel},y |  lda  #0 |  sta  ${segment[4].callLabel}+1,y"
            },
            // uwordarray2[indexvar] = uwordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index = intVal(segment[0])*2
                val loadIndex2Y = when(segment[2].callLabel) {
                    "A" -> " asl  a |  tay"
                    "X" -> " txa |  asl  a | tay"
                    "Y" -> " tya |  asl  a | tay"
                    else -> " lda  ${segment[2].callLabel} |  asl  a |  tay"
                }
                " $loadIndex2Y |  lda  ${segment[1].callLabel}+$index |  sta  ${segment[3].callLabel},y |  lda  ${segment[1].callLabel}+${index+1} |  sta  ${segment[3].callLabel}+1,y"
            },
            // uwordarray2[index mem] = ubytearray1[index1]
            AsmPattern(
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_UW, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                """
                lda  ${hexVal(segment[3])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[4].callLabel},y
                lda  #0
                sta  ${segment[4].callLabel}+1,y
                """
            },
            // uwordarray2[index mem] = uwordarray1[index1]
            AsmPattern(
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_MEM_B, Opcode.WRITE_INDEXED_VAR_WORD),
                    listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_MEM_UB, Opcode.WRITE_INDEXED_VAR_WORD)) { segment->
                val index1 = intVal(segment[0])
                """
                lda  ${hexVal(segment[2])}
                asl  a
                tay
                lda  ${segment[1].callLabel}+$index1
                sta  ${segment[3].callLabel},y
                lda  ${segment[1].callLabel}+${index1+1}
                sta  ${segment[3].callLabel}+1,y
                """
            },


            // ----------- assignment to FLOAT VARIABLE ----------------
            // floatvar  = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.ub2float
                """
            },
            // floatvar = uwordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.uw2float
                """
            },
            // floatvar = bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.b2float
                """
            },
            // floatvar = wordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.w2float
                """
            },
            // floatvar = float value
            AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                val floatConst = getFloatConst(segment[0].arg!!)
                """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  c64flt.copy_float
                """
            },
            // floatvar = float var
            AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  c64flt.copy_float
                """
            },
            // floatvar = mem float
            AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[1].callLabel}
                ldy  #>${segment[1].callLabel}
                jsr  c64flt.copy_float
                """
            },
            // floatvar = mem byte
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.b2float
                """
            },
            // floatvar = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.ub2float
                """
            },
            // floatvar = mem word
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.CAST_W_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.w2float
                """
            },
            // floatvar = mem uword
            AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.CAST_UW_TO_F, Opcode.POP_VAR_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.uw2float
                """
            },

            // floatvar = bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.b2float
                """
            },
            // floatvar = ubytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.ub2float
                """
            },
            // floatvar = wordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                val index = intVal(segment[0])*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.w2float
                """
            },
            // floatvar = uwordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_VAR_FLOAT)) { segment->
                val index = intVal(segment[0])*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[3].callLabel}
                ldy  #>${segment[3].callLabel}
                jsr  c64flt.uw2float
                """
            },
            // floatvar = floatarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.POP_VAR_FLOAT)) { segment ->
                val index = intVal(segment[0]) * Mflpt5.MemorySize
                """
                lda  #<${segment[1].callLabel}+$index
                ldy  #>${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${segment[2].callLabel}
                ldy  #>${segment[2].callLabel}
                jsr  c64flt.copy_float
                """
            },

            // memfloat = float value
            AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                val floatConst = getFloatConst(segment[0].arg!!)
                """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  c64flt.copy_float
                """
            },
            // memfloat = float var
            AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  c64flt.copy_float
                """
            },
            // memfloat  = ubytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.ub2float
                """
            },
            // memfloat = uwordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.uw2float
                """
            },
            // memfloat = bytevar
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                val loadByteA = when(segment[0].callLabel) {
                    "A" -> ""
                    "X" -> "txa"
                    "Y" -> "tya"
                    else -> "lda  ${segment[0].callLabel}"
                }
                """
                $loadByteA
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.b2float
                """
            },
            // memfloat = wordvar
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${segment[0].callLabel}+1
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.w2float
                """
            },
            // memfloat = mem byte
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.CAST_B_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.b2float
                """
            },
            // memfloat = mem ubyte
            AsmPattern(listOf(Opcode.PUSH_MEM_UB, Opcode.CAST_UB_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.ub2float
                """
            },
            // memfloat = mem word
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.CAST_W_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.w2float
                """
            },
            // memfloat = mem uword
            AsmPattern(listOf(Opcode.PUSH_MEM_UW, Opcode.CAST_UW_TO_F, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                lda  ${hexValPlusOne(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.uw2float
                """
            },
            // memfloat = mem float
            AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[1])}
                ldy  #>${hexVal(segment[1])}
                jsr  c64flt.copy_float
                """
            },
            // memfloat = bytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_B_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.b2float
                """
            },
            // memfloat = ubytearray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.CAST_UB_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                val index = intVal(segment[0])
                """
                lda  ${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_B1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.ub2float
                """
            },
            // memfloat = wordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_W_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                val index = intVal(segment[0])*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.w2float
                """
            },
            // memfloat = uwordarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.CAST_UW_TO_F, Opcode.POP_MEM_FLOAT)) { segment->
                val index = intVal(segment[0])*2
                """
                lda  ${segment[1].callLabel}+$index
                ldy  ${segment[1].callLabel}+${index+1}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[3])}
                ldy  #>${hexVal(segment[3])}
                jsr  c64flt.uw2float
                """
            },
            // memfloat = floatarray[index]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.POP_MEM_FLOAT)) { segment ->
                val index = intVal(segment[0]) * Mflpt5.MemorySize
                """
                lda  #<${segment[1].callLabel}+$index
                ldy  #>${segment[1].callLabel}+$index
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<${hexVal(segment[2])}
                ldy  #>${hexVal(segment[2])}
                jsr  c64flt.copy_float
                """
            },
            // floatarray[idxbyte] = float
            AsmPattern(listOf(Opcode.PUSH_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val floatConst = getFloatConst(segment[0].arg!!)
                val index = intVal(segment[1]) * Mflpt5.MemorySize
                """
                lda  #<$floatConst
                ldy  #>$floatConst
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  c64flt.copy_float
                """
            },
            // floatarray[idxbyte] = floatvar
            AsmPattern(listOf(Opcode.PUSH_VAR_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val index = intVal(segment[1]) * Mflpt5.MemorySize
                """
                lda  #<${segment[0].callLabel}
                ldy  #>${segment[0].callLabel}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  c64flt.copy_float
                """
            },
            //  floatarray[idxbyte] = memfloat
            AsmPattern(listOf(Opcode.PUSH_MEM_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val index = intVal(segment[1]) * Mflpt5.MemorySize
                """
                lda  #<${hexVal(segment[0])}
                ldy  #>${hexVal(segment[0])}
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[2].callLabel}+$index)
                ldy  #>(${segment[2].callLabel}+$index)
                jsr  c64flt.copy_float
                """
            },
            //  floatarray[idx2] = floatarray[idx1]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_FLOAT, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_FLOAT)) { segment ->
                val index1 = intVal(segment[0]) * Mflpt5.MemorySize
                val index2 = intVal(segment[2]) * Mflpt5.MemorySize
                """
                lda  #<(${segment[1].callLabel}+$index1)
                ldy  #>(${segment[1].callLabel}+$index1)
                sta  ${C64Zeropage.SCRATCH_W1}
                sty  ${C64Zeropage.SCRATCH_W1+1}
                lda  #<(${segment[3].callLabel}+$index2)
                ldy  #>(${segment[3].callLabel}+$index2)
                jsr  c64flt.copy_float
                """
            },

            // ---------- some special operations ------------------
            // var word = AX register pair
            AsmPattern(listOf(Opcode.PUSH_REGAX_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                sta  ${segment[1].callLabel}
                stx  ${segment[1].callLabel}+1
                """
            },
            // var word = AY register pair
            AsmPattern(listOf(Opcode.PUSH_REGAY_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                sta  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // var word = XY register pair
            AsmPattern(listOf(Opcode.PUSH_REGXY_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                stx  ${segment[1].callLabel}
                sty  ${segment[1].callLabel}+1
                """
            },
            // mem word = AX register pair
            AsmPattern(listOf(Opcode.PUSH_REGAX_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                sta  ${hexVal(segment[1])}
                stx  ${hexValPlusOne(segment[1])}
                """
            },
            // mem word = AY register pair
            AsmPattern(listOf(Opcode.PUSH_REGAY_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                sta  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },
            // mem word = XY register pair
            AsmPattern(listOf(Opcode.PUSH_REGXY_WORD, Opcode.POP_MEM_WORD)) { segment ->
                """
                stx  ${hexVal(segment[1])}
                sty  ${hexValPlusOne(segment[1])}
                """
            },

            // AX register pair = word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGAX_WORD)) { segment ->
                val value = hexVal(segment[0])
                " lda  #<$value |  ldx  #>$value"
            },
            // AY register pair = word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGAY_WORD)) { segment ->
                val value = hexVal(segment[0])
                " lda  #<$value |  ldy  #>$value"
            },
            // XY register pair = word value
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.POP_REGXY_WORD)) { segment ->
                val value = hexVal(segment[0])
                " ldx  #<$value |  ldy  #>$value"
            },
            // AX register pair = word var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGAX_WORD)) { segment ->
                " lda  ${segment[0].callLabel} |  ldx  ${segment[0].callLabel}+1"
            },
            // AY register pair = word var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGAY_WORD)) { segment ->
                " lda  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1"
            },
            // XY register pair = word var
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.POP_REGXY_WORD)) { segment ->
                " ldx  ${segment[0].callLabel} |  ldy  ${segment[0].callLabel}+1"
            },
            // AX register pair = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGAX_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGAX_WORD)) { segment ->
                " lda  ${hexVal(segment[0])} |  ldx  ${hexValPlusOne(segment[0])}"
            },
            // AY register pair = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGAY_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGAY_WORD)) { segment ->
                " lda  ${hexVal(segment[0])} |  ldy  ${hexValPlusOne(segment[0])}"
            },
            // XY register pair = mem word
            AsmPattern(
                    listOf(Opcode.PUSH_MEM_UW, Opcode.POP_REGXY_WORD),
                    listOf(Opcode.PUSH_MEM_W, Opcode.POP_REGXY_WORD)) { segment ->
                " ldx  ${hexVal(segment[0])} |  ldy  ${hexValPlusOne(segment[0])}"
            },


            // byte var = wordvar as (u)byte
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_UB, Opcode.POP_VAR_BYTE),
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_B, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[2].callLabel) {
                    "A" -> " lda  ${segment[0].callLabel}"
                    "X" -> " ldx  ${segment[0].callLabel}"
                    "Y" -> " ldy  ${segment[0].callLabel}"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}"
                }
            },
            // byte var = uwordvar as (u)byte
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_UB, Opcode.POP_VAR_BYTE),
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_B, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[2].callLabel) {
                    "A" -> " lda  ${segment[0].callLabel}"
                    "X" -> " ldx  ${segment[0].callLabel}"
                    "Y" -> " ldy  ${segment[0].callLabel}"
                    else -> " lda  ${segment[0].callLabel} |  sta  ${segment[2].callLabel}"
                }
            },
            // byte var = msb(word var)
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.MSB, Opcode.POP_VAR_BYTE)) { segment ->
                when(segment[2].callLabel) {
                    "A" -> " lda  ${segment[0].callLabel}+1"
                    "X" -> " ldx  ${segment[0].callLabel}+1"
                    "Y" -> " ldy  ${segment[0].callLabel}+1"
                    else -> " lda  ${segment[0].callLabel}+1 |  sta  ${segment[2].callLabel}"
                }
            },
            // push word var as (u)byte
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_UB),
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_W_TO_B)) { segment ->
                " lda  ${segment[0].callLabel} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push uword var as (u)byte
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_UB),
                    listOf(Opcode.PUSH_VAR_WORD, Opcode.CAST_UW_TO_B)) { segment ->
                " lda  ${segment[0].callLabel} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push msb(word var)
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.MSB)) { segment ->
                " lda  ${segment[0].callLabel}+1 |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },

            // set a register pair to a certain memory address (of a variable)
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGAX_WORD)) { segment ->
                " lda  #<${segment[0].callLabel} |  ldx  #>${segment[0].callLabel} "
            },
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGAY_WORD)) { segment ->
                " lda  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} "
            },
            AsmPattern(listOf(Opcode.PUSH_ADDR_HEAPVAR, Opcode.POP_REGXY_WORD)) { segment ->
                " ldx  #<${segment[0].callLabel} |  ldy  #>${segment[0].callLabel} "
            },

            // push  memory byte | bytevalue
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.BITOR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.BITOR_BYTE)) { segment ->
                " lda  ${hexVal(segment[0])} |  ora  #${hexVal(segment[1])} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push  memory byte & bytevalue
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.BITAND_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.BITAND_BYTE)) { segment ->
                " lda  ${hexVal(segment[0])} |  and  #${hexVal(segment[1])} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push  memory byte ^ bytevalue
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_BYTE, Opcode.BITXOR_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_BYTE, Opcode.BITXOR_BYTE)) { segment ->
                " lda  ${hexVal(segment[0])} |  eor  #${hexVal(segment[1])} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push  var byte | bytevalue
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.BITOR_BYTE)) { segment ->
                " lda  ${segment[0].callLabel} |  ora  #${hexVal(segment[1])} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push  var byte & bytevalue
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.BITAND_BYTE)) { segment ->
                " lda  ${segment[0].callLabel} |  and  #${hexVal(segment[1])} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },
            // push  var byte ^ bytevalue
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.BITXOR_BYTE)) { segment ->
                " lda   ${segment[0].callLabel} |  eor  #${hexVal(segment[1])} |  sta  ${ESTACK_LO.toHex()},x |  dex "
            },

            // push  memory word | wordvalue
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_WORD, Opcode.BITOR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_WORD, Opcode.BITOR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                ora  #<${hexVal(segment[1])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${hexValPlusOne(segment[0])}
                ora  #>${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            // push  memory word & wordvalue
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_WORD, Opcode.BITAND_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_WORD, Opcode.BITAND_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                and  #<${hexVal(segment[1])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${hexValPlusOne(segment[0])}
                and  #>${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            // push  memory word ^ wordvalue
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_WORD, Opcode.BITXOR_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_WORD, Opcode.BITXOR_WORD)) { segment ->
                """
                lda  ${hexVal(segment[0])}
                eor  #<${hexVal(segment[1])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${hexValPlusOne(segment[0])}
                eor  #>${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            // push  var word | wordvalue
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_WORD, Opcode.BITOR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                ora  #<${hexVal(segment[1])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${segment[0].callLabel}+1
                ora  #>${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            // push  var word & wordvalue
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_WORD, Opcode.BITAND_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                and  #<${hexVal(segment[1])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${segment[0].callLabel}+1
                and  #>${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            // push  var word ^ wordvalue
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_WORD, Opcode.BITXOR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                eor  #<${hexVal(segment[1])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${segment[0].callLabel}+1
                eor  #>${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            // push  var byte & var byte
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.BITAND_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                """
                lda  ${segment[0].callLabel}
                and  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                """
            },
            // push  var byte | var byte
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.BITOR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                """
                lda  ${segment[0].callLabel}
                ora  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                """
            },
            // push  var byte ^ var byte
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.BITXOR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                """
                lda  ${segment[0].callLabel}
                eor  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                """
            },
            // push  var word & var word
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.BITAND_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                and  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[0].callLabel}+1
                and  ${segment[1].callLabel}+1
                sta  ${segment[3].callLabel}+1
                """
            },
            // push  var word | var word
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.BITOR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                ora  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[0].callLabel}+1
                ora  ${segment[1].callLabel}+1
                sta  ${segment[3].callLabel}+1
                """
            },
            // push  var word ^ var word
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.BITXOR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                eor  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[0].callLabel}+1
                eor  ${segment[1].callLabel}+1
                sta  ${segment[3].callLabel}+1
                """
            },

            // bytearray[consti3] = bytearray[consti1] ^ bytearray[consti2]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE,
                    Opcode.BITXOR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val i1 = segment[5].arg!!.integerValue()
                val i2 = segment[0].arg!!.integerValue()
                val i3 = segment[2].arg!!.integerValue()
                """
                lda  ${segment[1].callLabel}+$i2
                eor  ${segment[3].callLabel}+$i3
                sta  ${segment[6].callLabel}+$i1
                """
            },
            // warray[consti3] = warray[consti1] ^ warray[consti2]
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD,
                    Opcode.BITXOR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val i1 = segment[5].arg!!.integerValue()*2
                val i2 = segment[0].arg!!.integerValue()*2
                val i3 = segment[2].arg!!.integerValue()*2
                """
                lda  ${segment[1].callLabel}+$i2
                eor  ${segment[3].callLabel}+$i3
                sta  ${segment[6].callLabel}+$i1
                lda  ${segment[1].callLabel}+${i2+1}
                eor  ${segment[3].callLabel}+${i3+1}
                sta  ${segment[6].callLabel}+${i1+1}
                """
            },

            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${segment[1].callLabel}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD)) { segment ->
                """
                lda  #${hexVal(segment[0])}
                sta  ${ESTACK_LO.toHex()},x
                lda  ${segment[1].callLabel}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.MKWORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${ESTACK_LO.toHex()},x
                lda  #${hexVal(segment[1])}
                sta  ${ESTACK_HI.toHex()},x
                dex
                """
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${segment[3].callLabel}
                lda  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}+1
                """
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.MKWORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${segment[3].callLabel}
                lda  #${hexVal(segment[1])}
                sta  ${segment[3].callLabel}+1
                """
            },
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  #${hexVal(segment[0])}
                sta  ${segment[3].callLabel}
                lda  ${segment[1].callLabel}
                sta  ${segment[3].callLabel}+1
                """
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.MKWORD, Opcode.CAST_UW_TO_W, Opcode.POP_VAR_WORD)) { segment ->
                """
                lda  ${segment[0].callLabel}
                sta  ${segment[4].callLabel}
                lda  ${segment[1].callLabel}
                sta  ${segment[4].callLabel}+1
                """
            },

            // more efficient versions of x+1 and x-1 to avoid pushing the 1 on the stack
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.ADD_B), listOf(Opcode.PUSH_BYTE, Opcode.ADD_UB)) { segment ->
                val amount = segment[0].arg!!.integerValue()
                if(amount in 1..2) {
                    " inc  ${(ESTACK_LO + 1).toHex()},x | ".repeat(amount)
                }
                else
                    null
            },
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.ADD_UW), listOf(Opcode.PUSH_WORD, Opcode.ADD_W)) { segment ->
                val amount = segment[0].arg!!.integerValue()
                if(amount in 1..2) {
                    " inc  ${(ESTACK_LO + 1).toHex()},x |  bne  + |  inc  ${(ESTACK_HI + 1).toHex()},x |+ | ".repeat(amount)
                }
                else
                    null
            },
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.SUB_B), listOf(Opcode.PUSH_BYTE, Opcode.SUB_UB)) { segment ->
                val amount = segment[0].arg!!.integerValue()
                if(amount in 1..2) {
                    " dec  ${(ESTACK_LO + 1).toHex()},x | ".repeat(amount)
                }
                else
                    null
            },
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.SUB_UW), listOf(Opcode.PUSH_WORD, Opcode.SUB_W)) { segment ->
                val amount = segment[0].arg!!.integerValue()
                if(amount in 1..2) {
                    " lda  ${(ESTACK_LO + 1).toHex()},x |  bne  + |  dec  ${(ESTACK_HI + 1).toHex()},x |+ |  dec  ${(ESTACK_LO + 1).toHex()},x | ".repeat(amount)
                }
                else
                    null
            },

            // @todo optimize 8 and 16 bit adds and subs (avoid stack use altogether on most common operations)

            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.CMP_B), listOf(Opcode.PUSH_VAR_BYTE, Opcode.CMP_UB)) { segment ->
                // this pattern is encountered as part of the loop bound condition in for loops (var + cmp + jz/jnz)
                val cmpval = segment[1].arg!!.integerValue()
                " lda  ${segment[0].callLabel} |  cmp  #$cmpval "
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.CMP_W), listOf(Opcode.PUSH_VAR_WORD, Opcode.CMP_UW)) { segment ->
                // this pattern is encountered as part of the loop bound condition in for loops (var + cmp + jz/jnz)
                """
                lda  ${segment[0].callLabel}
                cmp  #<${hexVal(segment[1])}
                bne  +
                lda  ${segment[0].callLabel}+1
                cmp  #>${hexVal(segment[1])}
                bne  +
                lda  #0
+
                """
            },

            AsmPattern(listOf(Opcode.RRESTOREX, Opcode.LINE, Opcode.RSAVEX), listOf(Opcode.RRESTOREX, Opcode.RSAVEX)) { segment ->
                """
                sta  ${C64Zeropage.SCRATCH_REG}
                pla
                tax
                pha
                lda  ${C64Zeropage.SCRATCH_REG}
                """
            },

            // various optimizable integer multiplications
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.MUL_B), listOf(Opcode.PUSH_BYTE, Opcode.MUL_UB)) { segment ->
                val amount=segment[0].arg!!.integerValue()
                val result = optimizedIntMultiplicationsOnStack(segment[1], amount)
                result ?: " lda  #${hexVal(segment[0])} |  sta  ${ESTACK_LO.toHex()},x |  dex |  jsr  prog8_lib.mul_byte"
            },
            AsmPattern(listOf(Opcode.PUSH_WORD, Opcode.MUL_W), listOf(Opcode.PUSH_WORD, Opcode.MUL_UW)) { segment ->
                val amount=segment[0].arg!!.integerValue()
                val result = optimizedIntMultiplicationsOnStack(segment[1], amount)
                if (result != null) result else {
                    val value = hexVal(segment[0])
                    " lda  #<$value |  sta  ${ESTACK_LO.toHex()},x |  lda  #>$value |  sta  ${ESTACK_HI.toHex()},x |  dex |  jsr  prog8_lib.mul_word"
                }
            },

            // various variable or memory swaps
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.POP_VAR_BYTE, Opcode.POP_VAR_BYTE)) { segment ->
                val var1 = segment[0].callLabel
                val var2 = segment[1].callLabel
                val var3 = segment[2].callLabel
                val var4 = segment[3].callLabel
                if(var1==var3 && var2==var4) {
                    """
                    lda  $var1
                    tay
                    lda  $var2
                    sta  $var1
                    sty  $var2
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_WORD, Opcode.PUSH_VAR_WORD, Opcode.POP_VAR_WORD, Opcode.POP_VAR_WORD)) { segment ->
                val var1 = segment[0].callLabel
                val var2 = segment[1].callLabel
                val var3 = segment[2].callLabel
                val var4 = segment[3].callLabel
                if(var1==var3 && var2==var4) {
                    """
                    lda  $var1
                    tay
                    lda  $var2
                    sta  $var1
                    sty  $var2
                    lda  $var1+1
                    tay
                    lda  $var2+1
                    sta  $var1+1
                    sty  $var2+1
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_MEM_B, Opcode.PUSH_MEM_B, Opcode.POP_MEM_BYTE, Opcode.POP_MEM_BYTE),
                    listOf(Opcode.PUSH_MEM_UB, Opcode.PUSH_MEM_UB, Opcode.POP_MEM_BYTE, Opcode.POP_MEM_BYTE)) { segment ->
                val addr1 = segment[0].arg!!.integerValue()
                val addr2 = segment[1].arg!!.integerValue()
                val addr3 = segment[2].arg!!.integerValue()
                val addr4 = segment[3].arg!!.integerValue()
                if(addr1==addr3 && addr2==addr4) {
                    """
                    lda  ${addr1.toHex()}
                    tay
                    lda  ${addr2.toHex()}
                    sta  ${addr1.toHex()}
                    sty  ${addr2.toHex()}
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_MEM_W, Opcode.PUSH_MEM_W, Opcode.POP_MEM_WORD, Opcode.POP_MEM_WORD),
                    listOf(Opcode.PUSH_MEM_UW, Opcode.PUSH_MEM_UW, Opcode.POP_MEM_WORD, Opcode.POP_MEM_WORD)) { segment ->
                val addr1 = segment[0].arg!!.integerValue()
                val addr2 = segment[1].arg!!.integerValue()
                val addr3 = segment[2].arg!!.integerValue()
                val addr4 = segment[3].arg!!.integerValue()
                if(addr1==addr3 && addr2==addr4) {
                    """
                    lda  ${addr1.toHex()}
                    tay
                    lda  ${addr2.toHex()}
                    sta  ${addr1.toHex()}
                    sty  ${addr2.toHex()}
                    lda  ${(addr1+1).toHex()}
                    tay
                    lda  ${(addr2+1).toHex()}
                    sta  ${(addr1+1).toHex()}
                    sty  ${(addr2+1).toHex()}
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_BYTE,
                    Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val i1 = segment[0].arg!!.integerValue()
                val i2 = segment[2].arg!!.integerValue()
                val i3 = segment[4].arg!!.integerValue()
                val i4 = segment[6].arg!!.integerValue()
                val array1 = segment[1].callLabel
                val array2 = segment[3].callLabel
                val array3 = segment[5].callLabel
                val array4 = segment[7].callLabel
                if(i1==i3 && i2==i4 && array1==array3 && array2==array4) {
                    """
                    lda  $array1+$i1
                    tay
                    lda  $array2+$i2
                    sta  $array1+$i1
                    sty  $array2+$i2
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_BYTE,
                    Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_BYTE)) { segment ->
                val vi1 = segment[0].callLabel
                val vi2 = segment[2].callLabel
                val vi3 = segment[4].callLabel
                val vi4 = segment[6].callLabel
                val array1 = segment[1].callLabel
                val array2 = segment[3].callLabel
                val array3 = segment[5].callLabel
                val array4 = segment[7].callLabel
                if(vi1==vi3 && vi2==vi4 && array1==array3 && array2==array4) {
                    val load1 = loadAFromIndexedByVar(segment[0], segment[1])
                    val load2 = loadAFromIndexedByVar(segment[2], segment[3])
                    val storeIn1 = storeAToIndexedByVar(segment[0], segment[1])
                    val storeIn2 = storeAToIndexedByVar(segment[2], segment[3])
                    """
                    $load1
                    pha
                    $load2
                    $storeIn1
                    pla
                    $storeIn2
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.READ_INDEXED_VAR_WORD,
                    Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD, Opcode.PUSH_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val i1 = segment[0].arg!!.integerValue()*2
                val i2 = segment[2].arg!!.integerValue()*2
                val i3 = segment[4].arg!!.integerValue()*2
                val i4 = segment[6].arg!!.integerValue()*2
                val array1 = segment[1].callLabel
                val array2 = segment[3].callLabel
                val array3 = segment[5].callLabel
                val array4 = segment[7].callLabel
                if(i1==i3 && i2==i4 && array1==array3 && array2==array4) {
                    """
                    lda  $array1+$i1
                    tay
                    lda  $array2+$i2
                    sta  $array1+$i1
                    sty  $array2+$i2
                    lda  $array1+${i1+1}
                    tay
                    lda  $array2+${i2+1}
                    sta  $array1+${i1+1}
                    sty  $array2+${i2+1}
                    """
                } else null
            },
            AsmPattern(listOf(Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.READ_INDEXED_VAR_WORD,
                    Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD, Opcode.PUSH_VAR_BYTE, Opcode.WRITE_INDEXED_VAR_WORD)) { segment ->
                val vi1 = segment[0].callLabel
                val vi2 = segment[2].callLabel
                val vi3 = segment[4].callLabel
                val vi4 = segment[6].callLabel
                val array1 = segment[1].callLabel
                val array2 = segment[3].callLabel
                val array3 = segment[5].callLabel
                val array4 = segment[7].callLabel
                if(vi1==vi3 && vi2==vi4 && array1==array3 && array2==array4) {
                    //  SCRATCH_B1 = index1
                    //  SCRATCH_REG = index2
                    //  SCRATCH_W1 = temp storage of array[index2]
                    """
                    lda  ${segment[0].callLabel}
                    asl  a
                    sta  ${C64Zeropage.SCRATCH_B1}
                    lda  ${segment[2].callLabel}
                    asl  a
                    sta  ${C64Zeropage.SCRATCH_REG}
                    stx  ${C64Zeropage.SCRATCH_REG_X}
                    tax
                    lda  ${segment[3].callLabel},x
                    ldy  ${segment[3].callLabel}+1,x
                    sta  ${C64Zeropage.SCRATCH_W1}
                    sty  ${C64Zeropage.SCRATCH_W1}+1
                    ldx  ${C64Zeropage.SCRATCH_B1}
                    lda  ${segment[1].callLabel},x
                    ldy  ${segment[1].callLabel}+1,x
                    ldx  ${C64Zeropage.SCRATCH_REG}
                    sta  ${segment[3].callLabel},x
                    tya
                    sta  ${segment[3].callLabel}+1,x
                    ldx  ${C64Zeropage.SCRATCH_B1}
                    lda  ${C64Zeropage.SCRATCH_W1}
                    sta  ${segment[1].callLabel},x
                    lda  ${C64Zeropage.SCRATCH_W1}+1
                    sta  ${segment[1].callLabel}+1,x
                    ldx  ${C64Zeropage.SCRATCH_REG_X}
                    """
                } else null
            }


    )

}
