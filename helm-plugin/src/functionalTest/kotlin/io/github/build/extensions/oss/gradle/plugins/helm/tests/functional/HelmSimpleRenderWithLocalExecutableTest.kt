package io.github.build.extensions.oss.gradle.plugins.helm.tests.functional

import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.GradleRunnerProvider
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.HelmExecutable.HelmExecutableParameter
import io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.HelmVersionToTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.file.containFile
import io.kotest.matchers.file.exist
import io.kotest.matchers.should
import io.kotest.matchers.string.contain
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class HelmSimpleRenderWithLocalExecutableTest {

    private val sourceDirectory = File("./src/functionalTest/resources/test/render-simple-local-executable")

    @TempDir
    private lateinit var testProjectDir: File

    @TempDir
    private lateinit var helmDirectory: File

    private lateinit var helmExecutable: File

    @BeforeEach
    fun setup() {
        sourceDirectory.copyRecursively(target = testProjectDir)
        helmExecutable = downloadHelm()
    }

    @ParameterizedTest
    @MethodSource("io.github.build.extensions.oss.gradle.plugins.helm.plugin.test.utils.DefaultGradleRunnerParameters#getDefaultParameterSetWithoutHelmVersion")
    fun helmRenderShouldRenderTheChart(parameters: DefaultGradleRunnerParameters) {
        // given
        val gradleRunner = GradleRunnerProvider.createRunner(
            parameters = parameters,
            projectDir = testProjectDir,
            // please note here - we override the helm chart. Initially it was used in init tests, however why not using it here as well
            arguments = listOf("helmRender", "--stacktrace", HelmExecutableParameter(helmExecutable).parameterValue),
        )

        // when
        val result = gradleRunner.build()

        // then
        val output = result.output

        output shouldContain "BUILD SUCCESSFUL"
        // check that the desired task had been executed
        output shouldContain "Task :helmRender"
        // check that the linting task had been executed
        output shouldContain "Task :helmLintMainChart"

        // check that the client wasn't downloaded
        result.tasks.filter {
            it.path.startsWith(":helmDownloadClient_") || it.path.startsWith(":helmExtractClient_")
        }.shouldBeEmpty()

        val folderWithChart = testProjectDir.resolve("build/helm/charts/helmRenderProjectName")
        folderWithChart should exist()
        // check that the main yaml file was copied
        folderWithChart should containFile("Chart.yaml")

        val renderedServiceYaml =
            testProjectDir.resolve("build/helm/render/main/default/helmRenderProjectName/templates/service.yaml")
        renderedServiceYaml should exist()
        // check that we render the project name into the file.
        // e.g. the line 'name: xxxxx{{ .Chart.Name }}' should be converted to what we check below
        renderedServiceYaml.readText() should contain("name: xxxxxhelmRenderProjectName")
    }

    // download the chart manually. Let's assume user does it outside of gradle helm plugin.
    // the code below might not be correct - however the most important if it works in tests.
    private fun downloadHelm(): File {
        val osName = System.getProperty("os.name")
        val operatingSystem = when {
            osName.startsWith("Windows", ignoreCase = true) -> "windows"
            osName.startsWith("Mac", ignoreCase = true) -> "darwin"
            osName.startsWith("Linux", ignoreCase = true) -> "linux"
            else -> error("Unsupported operating system: $osName")
        }
        val architecture = when (val osArch = System.getProperty("os.arch")) {
            "amd64", "x86_64" -> "amd64"
            "aarch64", "arm64" -> "arm64"
            "x86", "i386" -> "386"
            else -> error("Unsupported architecture: $osArch")
        }
        val osClassifier = "$operatingSystem-$architecture"
        val isWindows = osClassifier.startsWith("windows-")
        val archiveFormat = if (isWindows) "zip" else "tar.gz"
        val archive = helmDirectory.resolve("helm.${archiveFormat}")
        val version = HelmVersionToTest.defaultHelmVersionV3
        val connection = URI("https://get.helm.sh/helm-v$version-$osClassifier.$archiveFormat")
            .toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 30_000
            }
        connection.getInputStream().use { input ->
            archive.outputStream().use { output -> input.copyTo(output) }
        }

        val executableName = if (isWindows) "helm.exe" else "helm"
        val executable = helmDirectory.resolve("$osClassifier/$executableName")
        if (isWindows) {
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "$osClassifier/$executableName") {
                        executable.parentFile.mkdirs()
                        executable.outputStream().use { output -> zip.copyTo(output) }
                        break
                    }
                }
            }
        } else {
            val extraction = ProcessBuilder(
                "tar", "-xzf", archive.absolutePath, "-C", helmDirectory.absolutePath,
                "$osClassifier/$executableName",
            ).inheritIO().start()
            if (!extraction.waitFor(30, TimeUnit.SECONDS)) {
                extraction.destroyForcibly()
                error("Timed out extracting Helm from $archive")
            }
            check(extraction.exitValue() == 0) { "Unable to extract Helm from $archive" }
        }
        check(executable.isFile) { "Helm executable was not found in $archive" }
        check(executable.setExecutable(true)) { "Unable to make $executable executable" }
        return executable
    }
}
