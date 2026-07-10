pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

// jvmToolchain(17) 用の JDK を自動プロビジョニングする (cream に倣う)。
// ローカルに JDK 17 が無い環境でも "No matching toolchains" で落ちなくなる。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "koma-strict"

include(":koma-strict-runtime")
include(":koma-strict-ksp")
include(":koma-strict-ksp:shared")
