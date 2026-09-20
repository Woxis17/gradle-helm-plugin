package io.github.build.extensions.oss.gradle.plugins.helm.releases.tests.functional

import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.GradleRunnerProvider
import io.kotest.matchers.should
import io.kotest.matchers.string.startWith
import java.io.File
import java.util.stream.Stream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.io.TempDirDeletionStrategy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal data class FilterReleasesForTargetTestParameters(
    val gradleParameters: DefaultGradleRunnerParameters,
    val productionOnlyCommandLineArgs: List<String>,
    val databaseOnlyCommandLineArgs: List<String>,
) {
    // custom toString to avoid issued with project unpack
    override fun toString(): String {
        return "$gradleParameters-$productionOnlyCommandLineArgs-$databaseOnlyCommandLineArgs"
    }
}

/**
 * The test below checks we try to install helm chart according to release tags selected.
 *
 * The test doesn't call helm - instead it just checks what tasks will be evaluated.
 */
internal class FilterReleasesForTargetTest {
    companion object {
        @JvmStatic
        fun parameters(): Stream<FilterReleasesForTargetTestParameters> =
            DefaultGradleRunnerParameters.allWithoutHelmVersion
                .flatMap { gradleParameters ->
                    listOf(
                        FilterReleasesForTargetTestParameters(
                            gradleParameters = gradleParameters,
                            productionOnlyCommandLineArgs = listOf("helmInstallToProduction"),
                            databaseOnlyCommandLineArgs = listOf("helmInstallToDatabase"),
                        ),
                        FilterReleasesForTargetTestParameters(
                            gradleParameters = gradleParameters,
                            productionOnlyCommandLineArgs =
                                listOf("helmInstall", "-Phelm.release.target=production"),
                            databaseOnlyCommandLineArgs =
                                listOf("helmInstall", "-Phelm.release.target=database"),
                        ),
                    )
                }
                .stream()
    }

    private val sourceDirectory = File("./src/functionalTest/resources/test/filter-releases-for-target")

    @TempDir(deletionStrategy = TempDirDeletionStrategy.IgnoreFailures::class)
    private lateinit var testProjectDir: File

    @BeforeEach
    fun setup() {
        sourceDirectory.copyRecursively(target = testProjectDir)
    }

    @ParameterizedTest
    // we run dry-run, therefore no need to permutate across helm versions
    @MethodSource("parameters")
    fun shouldInstallOnlyReleasesSelectedByTarget(parameters: FilterReleasesForTargetTestParameters) {
        // Stage 1. We try installing application - and check that the database wasn't selected

        // Gradle resolves the complete task graph, but does not execute Helm or contact Kubernetes.
        val result1 = GradleRunnerProvider.createRunner(
            parameters = parameters.gradleParameters,
            projectDir = testProjectDir,
            // IMPORTANT. The goal is to install to production
            arguments = parameters.productionOnlyCommandLineArgs + listOf("--dry-run", "--stacktrace"),
        ).build()

        // so, the production installation had been scheduled - no database tasks
        result1.output.normaliseLineEndings() should startWith(
            expectedDryRunOutput(
                releaseTask = "helmInstallApplicationToProduction",
                targetTask = "helmInstallToProduction",
                commandLineArgs = parameters.productionOnlyCommandLineArgs,
            )
        )

        // Stage 2. We try installing database - and check that the application wasn't selected
        val result2 = GradleRunnerProvider.createRunner(
            parameters = parameters.gradleParameters,
            projectDir = testProjectDir,
            arguments = parameters.databaseOnlyCommandLineArgs + listOf("--dry-run", "--stacktrace"),
        ).build()

        // only database tasks are here - no production
        result2.output.normaliseLineEndings() should startWith(
            expectedDryRunOutput(
                releaseTask = "helmInstallDatabaseToDatabase",
                targetTask = "helmInstallToDatabase",
                commandLineArgs = parameters.databaseOnlyCommandLineArgs,
            )
        )
    }

    /**
     * Please note - we generate the expected output first, which will be checked with `startsWith`
     * We finish the expectations with `BUILD SUCCESSFUL`, therefore we check all tasks planned by Gradle.
     */
    private fun expectedDryRunOutput(
        releaseTask: String,
        targetTask: String,
        commandLineArgs: List<String>,
    ): String = buildString {
        appendLine(":helmAddRepositories SKIPPED")
        appendLine(":helmUpdateRepositories SKIPPED")
        appendLine(":$releaseTask SKIPPED")
        appendLine(":$targetTask SKIPPED")
        if (commandLineArgs.first() == "helmInstall") {
            appendLine(":helmInstall SKIPPED")
        }
        appendLine()
        append("BUILD SUCCESSFUL")
    }.normaliseLineEndings()

    // I don't understand what line endings are used by Gradle, so let's just use Unix ones always
    private fun String.normaliseLineEndings(): String =
        replace("\r\n", "\n")
}
