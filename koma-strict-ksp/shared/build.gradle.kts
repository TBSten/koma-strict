plugins {
    // ターゲット構成が koma.strict.kmp (android/ios 込み) と異なる (jvm/js/wasmJs + nodejs) ため
    // KMP 構成はこのファイルに直書きし、publish だけ convention plugin に寄せる
    alias(libs.plugins.kotlin.multiplatform)
    id("koma.strict.test")
    id("koma.strict.publish")
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
