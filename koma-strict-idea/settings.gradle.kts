import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

// ルート koma-strict とは完全独立のビルド。ルート settings.gradle.kts には include しない。
pluginManagement {
    repositories {
        // Compose Gradle plugin (org.jetbrains.compose) / Jewel standalone は JetBrains の
        // compose dev maven に置かれているので pluginManagement 側にも足す (preview 用)。
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // settings 側で IntelliJ Platform のリポジトリ (defaultRepositories) を集中管理するための plugin。
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
    // jvmToolchain(21) 用の JDK を foojay から自動解決する (root build と同じ方針)。
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // repo を settings に集約するので project 側の repositories {} は禁止する。
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // preview (standalone Jewel + Compose desktop) の成果物取得元。
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "koma-strict-idea"
