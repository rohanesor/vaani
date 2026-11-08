import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        ivy {
            name = "SherpaOnnxOfficialReleases"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7")
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.k2fsa", "sherpa-onnx") }
        }
    }
}

rootProject.name = "VAANI"
include(":app")
