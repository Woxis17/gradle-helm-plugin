package io.github.build.extensions.oss.gradle.plugins.helm.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "See https://github.com/build-extensions-oss/gradle-helm-plugin/issues/208")
open class HelmCollectChartDependencies : DefaultTask() {

    @get:Inject
    internal open val fileSystemOperations: FileSystemOperations
        get() = throw UnsupportedOperationException()


    @get:Inject
    internal open val archiveOperations: ArchiveOperations
        get() = throw UnsupportedOperationException()


    @get:InputFiles
    // let's consider value files are defined in the same repository - and not in the shared file at the computer
    // Alternatively we will have a cache miss (which is correct)
    @PathSensitive(PathSensitivity.RELATIVE)
    var dependencies: FileCollection =
        project.layout.files()


    @get:OutputDirectory
    val outputDir: DirectoryProperty =
        project.objects.directoryProperty()


    @TaskAction
    fun collectDependencies() {

        val result = fileSystemOperations.sync { spec ->
            spec.includeEmptyDirs = false
            spec.into(outputDir)

            dependencies.forEach { dependencyPackageFile ->
                spec.from(archiveOperations.tarTree(dependencyPackageFile))
            }
        }

        didWork = result.didWork
    }
}
