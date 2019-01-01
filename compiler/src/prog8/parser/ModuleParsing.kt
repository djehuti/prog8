package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


class ParsingFailedError(override var message: String) : Exception(message)


private val importedModules : HashMap<String, Module> = hashMapOf()


private class LexerErrorListener: BaseErrorListener() {
    var  numberOfErrors: Int = 0
    override fun syntaxError(p0: Recognizer<*, *>?, p1: Any?, p2: Int, p3: Int, p4: String?, p5: RecognitionException?) {
        numberOfErrors++
    }
}

fun importModule(filePath: Path) : Module {
    print("importing '${filePath.fileName}'")
    if(filePath.parent!=null)
        println(" (from '${filePath.parent}')")
    else
        println("")
    if(!Files.isReadable(filePath))
        throw ParsingFailedError("No such file: $filePath")

    val moduleName = filePath.fileName.toString().substringBeforeLast('.')
    val input = CharStreams.fromPath(filePath)
    val lexer = prog8Lexer(input)
    val lexerErrors = LexerErrorListener()
    lexer.addErrorListener(lexerErrors)
    val tokens = CommentHandlingTokenStream(lexer)
    val parser = prog8Parser(tokens)
    val parseTree = parser.module()
    val numberOfErrors = parser.numberOfSyntaxErrors + lexerErrors.numberOfErrors
    if(numberOfErrors > 0)
        throw ParsingFailedError("There are $numberOfErrors errors in '${filePath.fileName}'.")

    // You can do something with the parsed comments:
    // tokens.commentTokens().forEach { println(it) }

    // convert to Ast
    val moduleAst = parseTree.toAst(moduleName)
    importedModules[moduleAst.name] = moduleAst

    // process imports
    val lines = moduleAst.statements.toMutableList()
    // always import the prog8 compiler library
    if(!moduleAst.position.file.startsWith("prog8lib."))
        lines.add(0, Directive("%import", listOf(DirectiveArg(null, "prog8lib", null, moduleAst.position)), moduleAst.position))
    val imports = lines
            .asSequence()
            .mapIndexed { i, it -> Pair(i, it) }
            .filter { (it.second as? Directive)?.directive == "%import" }
            .map { Pair(it.first, executeImportDirective(it.second as Directive, filePath)) }
            .toList()

    imports.reversed().forEach {
        if(it.second==null) {
            // this import was already satisfied. just remove this line.
            lines.removeAt(it.first)
        } else {
            // merge imported lines at this spot
            lines.addAll(it.first, it.second!!.statements)
        }
    }

    moduleAst.statements = lines
    return moduleAst
}


fun discoverImportedModule(name: String, importedFrom: Path, position: Position?): Path {
    val fileName = "$name.p8"
    val locations = mutableListOf(Paths.get(importedFrom.parent.toString()))

    val propPath = System.getProperty("prog8.libdir")
    if(propPath!=null)
        locations.add(Paths.get(propPath))
    val envPath = System.getenv("PROG8_LIBDIR")
    if(envPath!=null)
        locations.add(Paths.get(envPath))
    locations.add(Paths.get(Paths.get("").toAbsolutePath().toString(), "prog8lib"))

    locations.forEach {
        val file = Paths.get(it.toString(), fileName)
        if (Files.isReadable(file)) return file
    }

    throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: $locations)")
}


fun executeImportDirective(import: Directive, importedFrom: Path): Module? {
    if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
        throw SyntaxError("invalid import directive", import.position)
    val moduleName = import.args[0].name!!
    if("$moduleName.p8" == import.position.file)
        throw SyntaxError("cannot import self", import.position)
    if(importedModules.containsKey(moduleName))
        return null

    val modulePath = discoverImportedModule(moduleName, importedFrom, import.position)
    val importedModule = importModule(modulePath)
    importedModule.checkImportedValid()

    return importedModule
}
