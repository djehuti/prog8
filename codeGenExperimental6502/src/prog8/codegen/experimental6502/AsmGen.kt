package prog8.codegen.experimental6502

import prog8.ast.Program
import prog8.ast.base.FatalAstException
import prog8.compilerinterface.*

class AsmGen(internal val program: Program,
             internal val errors: IErrorReporter,
             internal val symbolTable: SymbolTable,
             internal val options: CompilationOptions): IAssemblyGenerator {

    override fun compileToAssembly(): IAssemblyProgram? {

        println("\n** experimental 65(c)02 code generator **\n")

        symbolTable.print()

        // TODO temporary location to do this:
        val intermediateAst = IntermediateAstMaker.transform(program)
        intermediateAst.print()
        val entry = intermediateAst.entrypoint() ?: throw FatalAstException("no main.start() found")
        println(entry)

        println("..todo: create assembly code into ${options.outputDir.toAbsolutePath()}..")
        return AssemblyProgram("dummy")
    }
}