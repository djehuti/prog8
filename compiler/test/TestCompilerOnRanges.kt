package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.*
import prog8.ast.statements.ForLoop
import prog8.ast.statements.VarDecl
import prog8.compiler.target.C64Target
import prog8.compiler.target.Cx16Target
import prog8.compilerinterface.size
import prog8.compilerinterface.toConstantIntegerRange
import prog8tests.ast.helpers.cartesianProduct
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertFailure
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText


/**
 * ATTENTION: this is just kludge!
 * They are not really unit tests, but rather tests of the whole process,
 * from source file loading all the way through to running 64tass.
 */
class TestCompilerOnRanges: FunSpec({

    test("testUByteArrayInitializerWithRange_char_to_char") {
        val platform = Cx16Target
        val result = compileText(platform, true, """
            main {
                sub start() {
                    ubyte[] cs = @'a' to 'z' ; values are computed at compile time 
                    cs[0] = 23 ; keep optimizer from removing it
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val decl = startSub
            .statements.filterIsInstance<VarDecl>()[0]
        val rhsValues = (decl.value as ArrayLiteralValue)
            .value // Array<Expression>
            .map { (it as NumericLiteralValue).number.toInt() }
        val expectedStart = platform.encodeString("a", true)[0].toInt()
        val expectedEnd = platform.encodeString("z", false)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val actualStr = "${rhsValues.first()} .. ${rhsValues.last()}"
        withClue(".first .. .last") {
            actualStr shouldBe expectedStr
        }
        withClue("rangeExpr.size()") {
            (rhsValues.last() - rhsValues.first() + 1) shouldBe (expectedEnd - expectedStart + 1)
        }
    }

    test("testFloatArrayInitializerWithRange_char_to_char") {
        val platform = C64Target
        val result = compileText(platform, optimize = false, """
            %option enable_floats
            main {
                sub start() {
                    float[] cs = 'a' to 'z' ; values are computed at compile time 
                    cs[0] = 23 ; keep optimizer from removing it
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val decl = startSub
            .statements.filterIsInstance<VarDecl>()[0]
        val rhsValues = (decl.value as ArrayLiteralValue)
            .value // Array<Expression>
            .map { (it as NumericLiteralValue).number.toInt() }
        val expectedStart = platform.encodeString("a", false)[0].toInt()
        val expectedEnd = platform.encodeString("z", false)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val actualStr = "${rhsValues.first()} .. ${rhsValues.last()}"
        withClue(".first .. .last") {
            actualStr shouldBe expectedStr
        }
        withClue("rangeExpr.size()") {
            rhsValues.size shouldBe (expectedEnd - expectedStart + 1)
        }
    }

    context("floatArrayInitializerWithRange") {
        val combos = cartesianProduct(
            listOf("", "42", "41"),                 // sizeInDecl
            listOf("%option enable_floats", ""),    // optEnableFloats
            listOf(Cx16Target, C64Target),          // platform
            listOf(false, true)                    // optimize
        )

        combos.forEach {
            val (sizeInDecl, optEnableFloats, platform, optimize) = it
            val displayName =
                when (sizeInDecl) {
                    "" -> "no"
                    "42" -> "correct"
                    else -> "wrong"
                } + " array size given" +
                        ", " + (if (optEnableFloats == "") "without" else "with") + " %option enable_floats" +
                        ", ${platform.name}, optimize: $optimize"

            test(displayName) {
                val result = compileText(platform, optimize, """
                    $optEnableFloats
                    main {
                        sub start() {
                            float[$sizeInDecl] cs = 1 to 42 ; values are computed at compile time
                            cs[0] = 23 ; keep optimizer from removing it
                        }
                    }
                """)
                if (optEnableFloats != "" && (sizeInDecl=="" || sizeInDecl=="42"))
                    result.assertSuccess()
                else
                    result.assertFailure()

            }
        }
    }

    test("testForLoopWithRange_char_to_char") {
        val platform = Cx16Target
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in @'a' to 'f' {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val iterable = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }[0]
        val rangeExpr = iterable as RangeExpr

        val expectedStart = platform.encodeString("a", true)[0].toInt()
        val expectedEnd = platform.encodeString("f", false)[0].toInt()
        val expectedStr = "$expectedStart .. $expectedEnd"

        val intProgression = rangeExpr.toConstantIntegerRange()
        val actualStr = "${intProgression?.first} .. ${intProgression?.last}"
        withClue(".first .. .last") {
            actualStr shouldBe expectedStr
        }
        withClue("rangeExpr.size()") {
            rangeExpr.size() shouldBe (expectedEnd - expectedStart + 1)
        }
    }

    test("testForLoopWithRange_bool_to_bool") {
        val platform = Cx16Target
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in false to true {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val rangeExpr = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<RangeExpr>()[0]

        rangeExpr.size() shouldBe 2
        val intProgression = rangeExpr.toConstantIntegerRange()
        intProgression?.first shouldBe 0
        intProgression?.last shouldBe 1
    }

    test("testForLoopWithRange_ubyte_to_ubyte") {
        val platform = Cx16Target
        val result = compileText(platform, optimize = true, """
            main {
                sub start() {
                    ubyte i
                    for i in 1 to 9 {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val rangeExpr = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<RangeExpr>()[0]

        rangeExpr.size() shouldBe 9
        val intProgression = rangeExpr.toConstantIntegerRange()
        intProgression?.first shouldBe 1
        intProgression?.last shouldBe 9
    }

    test("testForLoopWithRange_str_downto_str") {
        val errors = ErrorReporterForTests()
        compileText(Cx16Target, true, """
            main {
                sub start() {
                    ubyte i
                    for i in "start" downto "end" {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """, errors, false).assertFailure()
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain ".p8:5:29: range expression from value must be integer"
        errors.errors[1] shouldContain ".p8:5:44: range expression to value must be integer"
    }

    test("testForLoopWithIterable_str") {
        val result = compileText(Cx16Target, false, """
            main {
                sub start() {
                    ubyte i
                    for i in "something" {
                        i += i ; keep optimizer from removing it
                    }
                }
            }
        """).assertSuccess()

        val program = result.program
        val startSub = program.entrypoint
        val iterable = startSub
            .statements.filterIsInstance<ForLoop>()
            .map { it.iterable }
            .filterIsInstance<IdentifierReference>()[0]

        iterable.inferType(program).getOr(DataType.UNDEFINED) shouldBe DataType.STR
    }

    test("testRangeExprNumericSize") {
        val expr = RangeExpr(
            NumericLiteralValue.optimalInteger(10, Position.DUMMY),
            NumericLiteralValue.optimalInteger(20, Position.DUMMY),
            NumericLiteralValue.optimalInteger(2, Position.DUMMY),
            Position.DUMMY)
        expr.size() shouldBe 6
        expr.toConstantIntegerRange()
    }
})