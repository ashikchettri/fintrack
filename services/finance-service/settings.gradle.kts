rootProject.name = "finance-service"

// share the monorepo-root version catalog (libs.*) — one place for versions
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
