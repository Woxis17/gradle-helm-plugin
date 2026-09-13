package io.github.build.extensions.oss.gradle.plugins.helm.tests.functional

import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.GradleRunnerProvider
import io.kotest.matchers.file.containFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class HelmLibraryRenderTest {

    private val sourceDirectory = File("./src/functionalTest/resources/test/library-simple")

    @TempDir
    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        sourceDirectory.copyRecursively(target = testProjectDir)
    }

    /**
     * Helm library charts can't be rendered by design. Additionally the code has if...else conditions for them (see RenderTaskRule)
     */
    @ParameterizedTest
    @MethodSource("io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters#getDefaultParameterSet")
    fun helmRenderShouldSkipLibraryChart(parameters: DefaultGradleRunnerParameters) {
        // given
        val gradleRunner = GradleRunnerProvider.createRunner(
            parameters = parameters,
            projectDir = testProjectDir,
            arguments = listOf("helmRender", "--stacktrace"),
        )

        // when
        val result = gradleRunner.build()

        // then
        // check that we can build everything
        result.output shouldContain "BUILD SUCCESSFUL"
        result.task(":helmLintMainChart")?.outcome shouldBe TaskOutcome.SUCCESS
        // check the chart can be packed
        result.task(":helmPackageMainChart")?.outcome shouldBe TaskOutcome.SUCCESS
        // RenderTaskRule skips this chart
        result.task(":helmRenderMainChartDefaultRendering")?.outcome shouldBe TaskOutcome.SKIPPED

        val folderWithChart = testProjectDir.resolve("build/helm/charts/helmRenderProjectName")
        folderWithChart should exist()
        folderWithChart should containFile("Chart.yaml")
        folderWithChart.resolve("Chart.yaml").readText() shouldContain "type: library"

        testProjectDir.resolve("build/helm/charts") should containFile("helmRenderProjectName-1.2.3.tgz")

        testProjectDir.resolve("build/helm/render") shouldNot exist()
    }
}
