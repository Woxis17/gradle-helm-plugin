package io.github.build.extensions.oss.gradle.plugins.helm.releases.tests.functional

import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.GradleRunnerProvider
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.io.TempDirDeletionStrategy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The test below checks we try to install helm chart according to release tags selected.
 *
 * The test doesn't call helm - instead it just checks what tasks will be evaluated.
 */
internal class FilterReleasesForTargetTest {
    private val sourceDirectory = File("./src/functionalTest/resources/test/filter-releases-for-target")

    @TempDir(deletionStrategy = TempDirDeletionStrategy.IgnoreFailures::class)
    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        sourceDirectory.copyRecursively(target = testProjectDir)
    }

    @ParameterizedTest
    @MethodSource("io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters#getDefaultParameterSetWithoutHelmVersion")
    fun shouldInstallOnlyReleasesSelectedByTarget(parameters: DefaultGradleRunnerParameters) {
        // Gradle resolves the complete task graph, but does not execute Helm or contact Kubernetes.
        val result = GradleRunnerProvider.createRunner(
            parameters = parameters,
            projectDir = testProjectDir,
            // IMPORTANT. The goal is to install to production
            arguments = listOf("helmInstallToProduction", "--dry-run", "--stacktrace"),
        ).build()

        // so, the production installation had been scheduled
        result.output shouldContain ":helmInstallApplicationToProduction SKIPPED"
        // database installation wasn't even considered
        result.output shouldNotContain ":helmInstallDatabaseToProduction"
        result.output shouldContain ":helmInstallToProduction SKIPPED"
    }
}
