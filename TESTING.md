* This is the dedicated file covering plugin testing only. It should be ignored for production code.
* All real tests are in `functionalTest` Gradle configuration.
    * They use Gradle Test Kit to run the plugin in various
      Gradle versions. New feature verification must be done through them (e.g. via integrational tests), especially if
      Gradle methods and interfaces are involved.
    * Use `HelmSimpleRenderTest.kt` or `OnlyHelmPublishPluginTest.kt` as integration tests examples. It checks multiple helm versions and run on multiple
      Gradle versions. GitHub actions will run them on different operating systems and on different Java versions.
* Unit tests are in `test` Gradle configuration. Rules:
    * Tests must use JUnit to start them. Do not use either Kotest or Spec as an engine. Why: it is harder to run these
      tests in IDEs. Use test `FileLockTest` as a unit test example.
    * If test needs to be parameterised - use the same approach with `DataSizeFormatterTest.kt`.
    * Tests using `spec` framework (by using imports like `import org.spekframework.spek2`, including the indirect one)
      are considered as obsolete. They exist only because they aren't replaced to integration tests yet. They might not
      run on Windows.