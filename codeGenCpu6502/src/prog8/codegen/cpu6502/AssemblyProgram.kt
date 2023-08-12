package prog8.codegen.cpu6502

import prog8.code.core.*
import prog8.code.target.C64Target
import java.nio.file.Path


internal class AssemblyProgram(
        override val name: String,
        outputDir: Path,
        private val compTarget: ICompilationTarget) : IAssemblyProgram {

    private val assemblyFile = outputDir.resolve("$name.asm")
    private val prgFile = outputDir.resolve("$name.prg")        // CBM prg executable program
    private val xexFile = outputDir.resolve("$name.xex")        // Atari xex executable program
    private val binFile = outputDir.resolve("$name.bin")
    private val viceMonListFile = outputDir.resolve(C64Target.viceMonListName(name))
    private val listFile = outputDir.resolve("$name.list")

    override fun assemble(options: CompilationOptions, errors: IErrorReporter): Boolean {

        val assemblerCommand: List<String>

        when (compTarget.name) {
            in setOf("c64", "c128", "cx16", "pet32") -> {
                // CBM machines .prg generation.

                // add "-Wlong-branch"  to see warnings about conversion of branch instructions to jumps (default = do this silently)
                val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-strict-bool", "-Wno-shadow", // "-Werror",
                    "--dump-labels", "--vice-labels", "--labels=$viceMonListFile", "--no-monitor"
                )

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                val outFile = when (options.output) {
                    OutputType.PRG -> {
                        command.add("--cbm-prg")
                        println("\nCreating prg for target ${compTarget.name}.")
                        prgFile
                    }
                    OutputType.RAW -> {
                        command.add("--nostart")
                        println("\nCreating raw binary for target ${compTarget.name}.")
                        binFile
                    }
                    else -> throw AssemblyError("invalid output type")
                }
                command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))
                assemblerCommand = command

            }
            "atari" -> {
                // Atari800XL .xex generation.

                // TODO are these options okay for atari?
                val command = mutableListOf("64tass", "--ascii", "--case-sensitive", "--long-branch",
                    "-Wall", "-Wno-strict-bool", "-Wno-shadow", // "-Werror",
                    "--no-monitor"
                )

                if(options.asmQuiet)
                    command.add("--quiet")

                if(options.asmListfile)
                    command.add("--list=$listFile")

                val outFile = when (options.output) {
                    OutputType.XEX -> {
                        command.add("--atari-xex")
                        println("\nCreating xex for target ${compTarget.name}.")
                        xexFile
                    }
                    OutputType.RAW -> {
                        command.add("--nostart")
                        println("\nCreating raw binary for target ${compTarget.name}.")
                        binFile
                    }
                    else -> throw AssemblyError("invalid output type")
                }
                command.addAll(listOf("--output", outFile.toString(), assemblyFile.toString()))
                assemblerCommand = command
            }
            else -> throw AssemblyError("invalid compilation target")
        }

        val proc = ProcessBuilder(assemblerCommand).inheritIO().start()
        val result = proc.waitFor()
        if (result == 0 && compTarget.name!="atari") {
            removeGeneratedLabelsFromMonlist()
            generateBreakpointList()
        }
        return result==0
    }

    private fun removeGeneratedLabelsFromMonlist() {
        val pattern = Regex("""al (\w+) \S+prog8_label_.+?""")
        val lines = viceMonListFile.toFile().readLines()
        viceMonListFile.toFile().outputStream().bufferedWriter().use {
            for (line in lines) {
                if(pattern.matchEntire(line)==null)
                    it.write(line+"\n")
            }
        }
    }

    private fun generateBreakpointList() {
        // builds list of breakpoints, appends to monitor list file
        val breakpoints = mutableListOf<String>()
        val pattern = Regex("""al (\w+) \S+_prog8_breakpoint_\d+.?""")      // gather breakpoints by the source label that's generated for them
        for (line in viceMonListFile.toFile().readLines()) {
            val match = pattern.matchEntire(line)
            if (match != null)
                breakpoints.add("break \$" + match.groupValues[1])
        }
        val num = breakpoints.size
        breakpoints.add(0, "; breakpoint list now follows")
        breakpoints.add(1, "; $num breakpoints have been defined")
        breakpoints.add(2, "del")
        viceMonListFile.toFile().appendText(breakpoints.joinToString("\n") + "\n")
    }
}
