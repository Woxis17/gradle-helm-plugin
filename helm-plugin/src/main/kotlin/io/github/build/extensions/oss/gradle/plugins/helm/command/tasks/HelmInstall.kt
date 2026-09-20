package io.github.build.extensions.oss.gradle.plugins.helm.command.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import build.extensions.oss.gradle.pluginutils.property
import org.gradle.work.DisableCachingByDefault


/**
 * Installs a chart into the cluster. Corresponds to the `helm install` CLI command.
 */
@DisableCachingByDefault(because = "See https://github.com/build-extensions-oss/gradle-helm-plugin/issues/208")
abstract class HelmInstall : AbstractHelmInstallationCommandTask() {

    /**
     * If `true`, re-use the given release name, even if that name is already used.
     */
    @get:Internal
    val replace: Property<Boolean> =
        project.objects.property()


    @TaskAction
    fun install() {
        execHelm("install") {
            args(releaseName)
            args(chart)
            option("--version", version)
            flag("--replace", replace)
        }
    }
}
