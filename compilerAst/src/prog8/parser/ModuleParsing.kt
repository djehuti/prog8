package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.IStringEncoding
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.antlr.toAst
import prog8.ast.base.Position
import prog8.ast.base.SyntaxError
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths



fun moduleName(fileName: Path) = fileName.toString().substringBeforeLast('.')

internal fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


class ModuleImporter(private val program: Program,
                     private val encoder: IStringEncoding,
                     private val compilationTargetName: String,
                     private val libdirs: List<String>) {

    fun importModule(filePath: Path): Module {
        print("importing '${moduleName(filePath.fileName)}'")
        if(filePath.parent!=null) {
            var importloc = filePath.toString()
            val curdir = Paths.get("").toAbsolutePath().toString()
            if(importloc.startsWith(curdir))
                importloc = "." + importloc.substring(curdir.length)
            println(" (from '$importloc')")
        }
        else
            println("")
        if(!Files.isReadable(filePath))
            throw ParsingFailedError("No such file: $filePath")

        val content = filePath.toFile().readText()
        val cs = CharStreams.fromString(content)

        return importModule(cs, filePath)
    }

    fun importLibraryModule(name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg("", name, 42, position = Position("<<<implicit-import>>>", 0, 0, 0))
        ), Position("<<<implicit-import>>>", 0, 0, 0))
        return executeImportDirective(import, Paths.get(""))
    }

    private fun importModule(stream: CharStream, modulePath: Path): Module {
        val parser = Prog8Parser
        val sourceText = stream.toString()
        val moduleAst = parser.parseModule(sourceText)
        moduleAst.program = program
        moduleAst.linkParents(program.namespace)
        program.modules.add(moduleAst)

        // accept additional imports
        val lines = moduleAst.statements.toMutableList()
        lines.asSequence()
                .mapIndexed { i, it -> i to it }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(it.second as Directive, modulePath) }

        moduleAst.statements = lines
        return moduleAst
    }

    private fun executeImportDirective(import: Directive, source: Path): Module? {
        if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
            throw SyntaxError("invalid import directive", import.position)
        val moduleName = import.args[0].name!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name == moduleName }
        if(existing!=null)
            return null

        val rsc = tryGetModuleFromResource("$moduleName.p8", compilationTargetName)
        val importedModule =
                if(rsc!=null) {
                    // load the module from the embedded resource
                    val (resource, resourcePath) = rsc
                    resource.use {
                        println("importing '$moduleName' (library)")
                        val content = it.reader().readText().replace("\r\n", "\n")
                        importModule(CharStreams.fromString(content), Module.pathForResource(resourcePath))
                    }
                } else {
                    val modulePath = tryGetModuleFromFile(moduleName, source, import.position)
                    importModule(modulePath)
                }

        removeDirectivesFromImportedModule(importedModule)
        return importedModule
    }

    private fun removeDirectivesFromImportedModule(importedModule: Module) {
        // Most global directives don't apply for imported modules, so remove them
        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%target")
        var directives = importedModule.statements.filterIsInstance<Directive>()
        importedModule.statements.removeAll(directives)
        directives = directives.filter{ it.directive !in moduleLevelDirectives }
        importedModule.statements.addAll(0, directives)
    }

    private fun tryGetModuleFromResource(name: String, compilationTargetName: String): Pair<InputStream, String>? {
        val targetSpecificPath = "/prog8lib/$compilationTargetName/$name"
        val targetSpecificResource = object{}.javaClass.getResourceAsStream(targetSpecificPath)
        if(targetSpecificResource!=null)
            return Pair(targetSpecificResource, targetSpecificPath)

        val generalPath = "/prog8lib/$name"
        val generalResource = object{}.javaClass.getResourceAsStream(generalPath)
        if(generalResource!=null)
            return Pair(generalResource, generalPath)

        return null
    }

    private fun tryGetModuleFromFile(name: String, source: Path, position: Position?): Path {
        val fileName = "$name.p8"
        val libpaths = libdirs.map {Path.of(it)}
        val locations =
            (if(source.toString().isEmpty()) libpaths else libpaths.drop(1) + listOf(source.parent ?: Path.of("."))) +
                listOf(Paths.get(Paths.get("").toAbsolutePath().toString(), "prog8lib"))

        locations.forEach {
            val file = pathFrom(it.toString(), fileName)
            if (Files.isReadable(file)) return file
        }

        throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: embedded libs and $locations)")
    }
}
