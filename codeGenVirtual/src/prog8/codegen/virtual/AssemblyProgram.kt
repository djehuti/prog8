package prog8.codegen.virtual

import prog8.code.core.CompilationOptions
import prog8.code.core.IAssemblyProgram
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div


internal class AssemblyProgram(override val name: String) : IAssemblyProgram
{
    override fun assemble(options: CompilationOptions): Boolean {
        val outfile = options.outputDir / ("$name.p8virt")
        println("write code to ${outfile}")
        outfile.bufferedWriter().use {
            it.write(memsrc)
            it.write("------PROGRAM------\n")
            it.write(src)
        }
        return true
    }

    val memsrc = """
$4000 strz "Hello from program! "derp" bye.\n"
$2000 ubyte 65,66,67,68,0
$2100 uword $1111,$2222,$3333,$4444
"""
    val src = """
; enable lores gfx screen
load r0, 0
syscall 8   
load.w r10, 320
load.w r11, 240
load.b r12, 0

_forever:
load.w r1, 0
_yloop:
load.w r0, 0
_xloop:
mul.b r2,r0,r1
add.b r2,r2,r12
syscall 10 
addi.w r0,r0,1
blt.w r0, r10, _xloop
addi.w r1,r1,1
blt.w r1, r11,_yloop
addi.b r12,r12,1
jump _forever

load.w r0, 2000
syscall 7
load.w r0,0
return"""

}