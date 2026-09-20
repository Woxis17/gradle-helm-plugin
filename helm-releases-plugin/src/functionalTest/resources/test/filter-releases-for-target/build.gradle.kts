plugins {
    id("io.github.build-extensions-oss.helm-releases") version "0.0.1"
}

helm {
    releases {
        create("application") {
            from("example/application")
            tags("application")
        }
        create("database") {
            from("example/database")
            tags("database")
        }
    }

    releaseTargets {
        create("production") {
            selectTags = "application"
        }
    }
}
