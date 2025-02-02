package org.utbot.sarif

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.utbot.framework.plugin.api.UtExecution
import org.utbot.framework.plugin.api.UtImplicitlyThrownException
import org.utbot.framework.plugin.api.UtMethod
import org.utbot.framework.plugin.api.UtPrimitiveModel
import org.utbot.framework.plugin.api.UtTestCase
import org.junit.Test
import org.mockito.Mockito

class SarifReportTest {

    @Test
    fun testNonEmptyReport() {
        val actualReport = SarifReport(
            testCases = listOf(),
            generatedTestsCode = "",
            sourceFindingEmpty
        ).createReport()

        assert(actualReport.isNotEmpty())
    }

    @Test
    fun testNoUncheckedExceptions() {
        val sarif = SarifReport(
            testCases = listOf(testCase),
            generatedTestsCode = "",
            sourceFindingEmpty
        ).createReport().toSarif()

        assert(sarif.runs.first().results.isEmpty())
    }

    @Test
    fun testDetectAllUncheckedExceptions() {
        mockUtMethodNames()

        val mockUtExecutionNPE = Mockito.mock(UtExecution::class.java, Mockito.RETURNS_DEEP_STUBS)
        Mockito.`when`(mockUtExecutionNPE.result).thenReturn(
            UtImplicitlyThrownException(NullPointerException(), false),
        )
        Mockito.`when`(mockUtExecutionNPE.stateBefore.parameters).thenReturn(listOf())

        val mockUtExecutionAIOBE = Mockito.mock(UtExecution::class.java, Mockito.RETURNS_DEEP_STUBS)
        Mockito.`when`(mockUtExecutionAIOBE.result).thenReturn(
            UtImplicitlyThrownException(ArrayIndexOutOfBoundsException(), false),
        )
        Mockito.`when`(mockUtExecutionAIOBE.stateBefore.parameters).thenReturn(listOf())

        val testCases = listOf(
            UtTestCase(mockUtMethod, listOf(mockUtExecutionNPE)),
            UtTestCase(mockUtMethod, listOf(mockUtExecutionAIOBE))
        )

        val report = SarifReport(
            testCases = testCases,
            generatedTestsCode = "",
            sourceFindingEmpty
        ).createReport().toSarif()

        assert(report.runs.first().results[0].message.text.contains("NullPointerException"))
        assert(report.runs.first().results[1].message.text.contains("ArrayIndexOutOfBoundsException"))
    }

    @Test
    fun testCorrectResultLocations() {
        mockUtMethodNames()

        Mockito.`when`(mockUtExecution.result).thenReturn(
            UtImplicitlyThrownException(NullPointerException(), false)
        )
        Mockito.`when`(mockUtExecution.stateBefore.parameters).thenReturn(listOf())
        Mockito.`when`(mockUtExecution.path.lastOrNull()?.stmt?.javaSourceStartLineNumber).thenReturn(1337)
        Mockito.`when`(mockUtExecution.testMethodName).thenReturn("testMain_ThrowArithmeticException")

        val report = sarifReportMain.createReport().toSarif()

        val result = report.runs.first().results.first()
        val location = result.locations.first().physicalLocation
        val relatedLocation = result.relatedLocations.first().physicalLocation

        assert(location.artifactLocation.uri.contains("Main.java"))
        assert(location.region.startLine == 1337)
        assert(relatedLocation.artifactLocation.uri.contains("MainTest.java"))
        assert(relatedLocation.region.startLine == 1)
    }

    @Test
    fun testCorrectMethodParameters() {
        mockUtMethodNames()

        Mockito.`when`(mockUtExecution.result).thenReturn(
            UtImplicitlyThrownException(NullPointerException(), false)
        )
        Mockito.`when`(mockUtExecution.stateBefore.parameters).thenReturn(
            listOf(
                UtPrimitiveModel(227),
                UtPrimitiveModel(3.14),
                UtPrimitiveModel(false)
            )
        )

        val report = sarifReportMain.createReport().toSarif()

        val result = report.runs.first().results.first()
        assert(result.message.text.contains("227"))
        assert(result.message.text.contains("3.14"))
        assert(result.message.text.contains("false"))
    }

    @Test
    fun testCorrectCodeFlows() {
        mockUtMethodNames()

        val uncheckedException = Mockito.mock(NullPointerException::class.java)
        val stackTraceElement = StackTraceElement("Main", "main", "Main.java", 17)
        Mockito.`when`(uncheckedException.stackTrace).thenReturn(
            Array(2) { stackTraceElement }
        )

        Mockito.`when`(mockUtExecution.result).thenReturn(
            UtImplicitlyThrownException(uncheckedException, false)
        )
        Mockito.`when`(mockUtExecution.stateBefore.parameters).thenReturn(listOf())

        val report = sarifReportMain.createReport().toSarif()

        val result = report.runs.first().results.first().codeFlows.first().threadFlows.first().locations.map {
            it.location.physicalLocation
        }
        for (index in 0..1) {
            assert(result[index].artifactLocation.uri.contains("Main.java"))
            assert(result[index].region.startLine == 17)
        }
    }

    @Test
    fun testCodeFlowsStartsWithMethodCall() {
        mockUtMethodNames()

        val uncheckedException = Mockito.mock(NullPointerException::class.java)
        val stackTraceElement = StackTraceElement("Main", "main", "Main.java", 3)
        Mockito.`when`(uncheckedException.stackTrace).thenReturn(arrayOf(stackTraceElement))

        Mockito.`when`(mockUtExecution.result).thenReturn(
            UtImplicitlyThrownException(uncheckedException, false)
        )
        Mockito.`when`(mockUtExecution.stateBefore.parameters).thenReturn(listOf())
        Mockito.`when`(mockUtExecution.testMethodName).thenReturn("testMain_ThrowArithmeticException")

        val report = sarifReportMain.createReport().toSarif()

        val codeFlowPhysicalLocations = report.runs[0].results[0].codeFlows[0].threadFlows[0].locations.map {
            it.location.physicalLocation
        }
        assert(codeFlowPhysicalLocations[0].artifactLocation.uri.contains("MainTest.java"))
        assert(codeFlowPhysicalLocations[0].region.startLine == 3)
    }

    @Test
    fun testCodeFlowsStartsWithPrivateMethodCall() {
        mockUtMethodNames()

        val uncheckedException = Mockito.mock(NullPointerException::class.java)
        val stackTraceElement = StackTraceElement("Main", "main", "Main.java", 3)
        Mockito.`when`(uncheckedException.stackTrace).thenReturn(arrayOf(stackTraceElement))

        Mockito.`when`(mockUtExecution.result).thenReturn(
            UtImplicitlyThrownException(uncheckedException, false)
        )
        Mockito.`when`(mockUtExecution.stateBefore.parameters).thenReturn(listOf())
        Mockito.`when`(mockUtExecution.testMethodName).thenReturn("testMain_ThrowArithmeticException")

        val report = sarifReportPrivateMain.createReport().toSarif()

        val codeFlowPhysicalLocations = report.runs[0].results[0].codeFlows[0].threadFlows[0].locations.map {
            it.location.physicalLocation
        }
        assert(codeFlowPhysicalLocations[0].artifactLocation.uri.contains("MainTest.java"))
        assert(codeFlowPhysicalLocations[0].region.startLine == 4)
    }

    // internal

    private val mockUtMethod = Mockito.mock(UtMethod::class.java, Mockito.RETURNS_DEEP_STUBS)

    private val mockUtExecution = Mockito.mock(UtExecution::class.java, Mockito.RETURNS_DEEP_STUBS)

    private val testCase = UtTestCase(mockUtMethod, listOf(mockUtExecution))

    private fun mockUtMethodNames() {
        Mockito.`when`(mockUtMethod.callable.name).thenReturn("main")
        Mockito.`when`(mockUtMethod.clazz.qualifiedName).thenReturn("Main")
    }

    private fun String.toSarif(): Sarif = jacksonObjectMapper().readValue(this)

    // constants

    private val sourceFindingEmpty = SourceFindingStrategyDefault(
        sourceClassFqn = "",
        sourceFilePath = "",
        testsFilePath = "",
        projectRootPath = ""
    )

    private val sourceFindingMain = SourceFindingStrategyDefault(
        sourceClassFqn = "Main",
        sourceFilePath = "src/Main.java",
        testsFilePath = "test/MainTest.java",
        projectRootPath = "."
    )

    private val generatedTestsCodeMain = """
        public void testMain_ThrowArithmeticException() {
            Main main = new Main();
            main.main(0);
        }
    """.trimIndent()

    private val generatedTestsCodePrivateMain = """
        public void testMain_ThrowArithmeticException() {
            Main main = new Main();
            // ...
            mainMethod.invoke(main, mainMethodArguments);
        }
    """.trimIndent()

    private val sarifReportMain =
        SarifReport(listOf(testCase), generatedTestsCodeMain, sourceFindingMain)

    private val sarifReportPrivateMain =
        SarifReport(listOf(testCase), generatedTestsCodePrivateMain, sourceFindingMain)
}