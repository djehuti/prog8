package prog8tests.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.SymbolTable
import prog8.code.ast.PtProgram
import prog8.codegen.virtual.*
import prog8.vm.Opcode
import prog8.vm.VmDataType
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder

class TestVmPeepholeOpt: FunSpec({
    fun makeVmProgram(lines: List<VmCodeLine>): Pair<AssemblyProgram, VariableAllocator> {
        val st = SymbolTable()
        val program = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val allocations = VariableAllocator(st, program)
        val asm = AssemblyProgram("test", allocations)
        val block = VmCodeChunk()
        for(line in lines)
            block += line
        asm.addBlock(block)
        return Pair(asm, allocations)
    }

    fun AssemblyProgram.lines(): List<VmCodeLine> = this.getBlocks().flatMap { it.lines }

    test("remove nops") {
        val(asm, allocations) = makeVmProgram(listOf(
            VmCodeInstruction(Opcode.JUMP, labelSymbol = listOf("dummy")),
            VmCodeInstruction(Opcode.NOP),
            VmCodeInstruction(Opcode.NOP)
        ))
        asm.lines().size shouldBe 3
        val opt = VmPeepholeOptimizer(asm, allocations)
        opt.optimize()
        asm.lines().size shouldBe 1
    }

    test("remove jmp to label below") {
        val(asm, allocations) = makeVmProgram(listOf(
            VmCodeInstruction(Opcode.JUMP, labelSymbol = listOf("label")),  // removed
            VmCodeLabel(listOf("label")),
            VmCodeInstruction(Opcode.JUMP, labelSymbol = listOf("label2")), // removed
            VmCodeInstruction(Opcode.NOP),  // removed
            VmCodeLabel(listOf("label2")),
            VmCodeInstruction(Opcode.JUMP, labelSymbol = listOf("label3")),
            VmCodeInstruction(Opcode.INC, VmDataType.BYTE, reg1=1),
            VmCodeLabel(listOf("label3"))
        ))
        asm.lines().size shouldBe 8
        val opt = VmPeepholeOptimizer(asm, allocations)
        opt.optimize()
        val lines = asm.lines()
        lines.size shouldBe 5
        (lines[0] as VmCodeLabel).name shouldBe listOf("label")
        (lines[1] as VmCodeLabel).name shouldBe listOf("label2")
        (lines[2] as VmCodeInstruction).ins.opcode shouldBe Opcode.JUMP
        (lines[3] as VmCodeInstruction).ins.opcode shouldBe Opcode.INC
        (lines[4] as VmCodeLabel).name shouldBe listOf("label3")
    }

    test("remove double sec/clc") {
        val(asm, allocations) = makeVmProgram(listOf(
            VmCodeInstruction(Opcode.SEC),
            VmCodeInstruction(Opcode.SEC),
            VmCodeInstruction(Opcode.SEC),
            VmCodeInstruction(Opcode.CLC),
            VmCodeInstruction(Opcode.CLC),
            VmCodeInstruction(Opcode.CLC)
        ))
        asm.lines().size shouldBe 6
        val opt = VmPeepholeOptimizer(asm, allocations)
        opt.optimize()
        val lines = asm.lines()
        lines.size shouldBe 1
        (lines[0] as VmCodeInstruction).ins.opcode shouldBe Opcode.CLC
    }

    test("push followed by pop") {
        val(asm, allocations) = makeVmProgram(listOf(
            VmCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=42),
            VmCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=42),
            VmCodeInstruction(Opcode.PUSH, VmDataType.BYTE, reg1=99),
            VmCodeInstruction(Opcode.POP, VmDataType.BYTE, reg1=222)
        ))
        asm.lines().size shouldBe 4
        val opt = VmPeepholeOptimizer(asm, allocations)
        opt.optimize()
        val lines = asm.lines()
        lines.size shouldBe 1
        (lines[0] as VmCodeInstruction).ins.opcode shouldBe Opcode.LOADR
        (lines[0] as VmCodeInstruction).ins.reg1 shouldBe 222
        (lines[0] as VmCodeInstruction).ins.reg2 shouldBe 99
    }
})