plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    compilerOptions.optIn.addAll(
        "me.tbsten.koma.strict.InternalKomaStrictApi",
    )

    jvm()
    js {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // shared の公開 API に付ける @InternalKomaStrictApi (opt-in marker) を参照する
            implementation(project(":koma-strict-runtime"))
        }
        commonTest.dependencies {
            // TODO: io.kotest Gradle plugin (KSP ベースの per-target spec launcher 生成) を配線し、
            //       kotest-framework-engine を入れてテストを commonTest に移し js/wasmJs でも実行する。
            //       Kotlin 2.4.0 + KSP 2.3.10 との互換を確認できるまで shared のテストは jvmTest のみで書く。
        }
        jvmTest.dependencies {
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.runner.junit5)
            implementation(libs.kotest.property)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

//Publishing your Kotlin Multiplatform library to Maven Central
//https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
mavenPublishing {
    publishToMavenCentral()
    coordinates(group.toString(), "koma-strict-ksp-shared", version.toString())

    pom {
        name = "koma-strict"
        description = "Kotlin Multiplatform library"
        url = "github url" //todo

        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }

        developers {
            developer {
                id = "" //todo github nickname
                name = "" //todo full name
                email = "" //todo email
            }
        }

        scm {
            url = "github url" //todo
        }
    }
    // ローカル publish 時のみ署名をスキップ。CI (Maven Central publish) は
    // ORG_GRADLE_PROJECT_signingInMemoryKey* を注入する設計なので常に署名する (cream イディオム)
    if (!(gradle.startParameter.taskNames.contains("publishToMavenLocal"))) signAllPublications()
}
