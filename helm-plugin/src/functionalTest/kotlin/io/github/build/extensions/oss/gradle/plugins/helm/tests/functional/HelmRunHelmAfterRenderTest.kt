package io.github.build.extensions.oss.gradle.plugins.helm.tests.functional

import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.GradleRunnerProvider
import io.kotest.matchers.file.containFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.io.TempDirDeletionStrategy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Checks direct access to the Helm extension from a user's doLast action after normal rendering.
 */
internal class HelmRunHelmAfterRenderTest {

    private val sourceDirectory = File("./src/functionalTest/resources/test/custom-call-after-render")

    @TempDir(deletionStrategy = TempDirDeletionStrategy.IgnoreFailures::class)
    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        sourceDirectory.copyRecursively(target = testProjectDir)
    }

    @ParameterizedTest
    @MethodSource("io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters#getDefaultParameterSet")
    fun helmRenderShouldExecuteHelmHelpThroughTheExtensionInDoLast(parameters: DefaultGradleRunnerParameters) {
        val gradleRunner = GradleRunnerProvider.createRunner(
            parameters = parameters,
            projectDir = testProjectDir,
            arguments = listOf("helmRender", "--stacktrace", "--info"),
        )

        val result = gradleRunner.build()

        result.task(":helmLintMainChart")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":helmRender")?.outcome shouldBe TaskOutcome.SUCCESS
        // check that helm help command had been executed - this is the custom one was run after helm render
        result.output shouldContain Regex("Executing: \\[[^\\r\\n]*, help\\]")

        val folderWithChart = testProjectDir.resolve("build/helm/charts/helmRenderProjectName")
        folderWithChart should containFile("Chart.yaml")

        val renderedServiceYaml = testProjectDir.resolve(
            "build/helm/render/main/default/helmRenderProjectName/templates/service.yaml",
        )
        renderedServiceYaml should exist()
        renderedServiceYaml.readText() shouldContain "name: xxxxxhelmRenderProjectName"
    }
}
