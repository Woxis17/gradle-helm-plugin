package io.github.build.extensions.oss.gradle.plugins.helm.releases.tests.functional

import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.GradleRunnerProvider
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.string.startWith
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
    // we run dry-run, therefore no need to permutate across helm versions
    @MethodSource("io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters#getDefaultParameterSetWithoutHelmVersion")
    fun shouldInstallOnlyReleasesSelectedByTarget(parameters: DefaultGradleRunnerParameters) {
        // Stage 1. We try installing application - and check that the database wasn't selected

        // Gradle resolves the complete task graph, but does not execute Helm or contact Kubernetes.
        val result1 = GradleRunnerProvider.createRunner(
            parameters = parameters,
            projectDir = testProjectDir,
            // IMPORTANT. The goal is to install to production
            arguments = listOf("helmInstallToProduction", "--dry-run", "--stacktrace"),
        ).build()

        // so, the production installation had been scheduled - no database tasks
        result1.output should startWith(
            """
                :helmAddRepositories SKIPPED
                :helmUpdateRepositories SKIPPED
                :helmInstallApplicationToProduction SKIPPED
                :helmInstallToProduction SKIPPED

                BUILD SUCCESSFUL
            """.trimIndent()
        )

        // Stage 2. We try installing database - and check that the application wasn't selected
        val result2 = GradleRunnerProvider.createRunner(
            parameters = parameters,
            projectDir = testProjectDir,
            arguments = listOf("helmInstallToDatabase", "--dry-run", "--stacktrace"),
        ).build()

        // only database tasks are here - no production
        result2.output should startWith(
            """
            :helmAddRepositories SKIPPED
            :helmUpdateRepositories SKIPPED
            :helmInstallDatabaseToDatabase SKIPPED
            :helmInstallToDatabase SKIPPED

            BUILD SUCCESSFUL
        """.trimIndent()
        )
    }
}
