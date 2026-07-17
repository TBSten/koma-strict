import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// 実物の koma-core / koma-test に対して koma-strict の生成コードをコンパイル・実行検証する
// 統合テストモジュール (publish しない・cream の test/ モジュール文化の koma-strict 版)。
// convention plugin (koma.strict.kmp) は publish 前提 (explicitApi / android / 全ターゲット) のため
// 使わず bespoke 構成にしている。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    // koma-core は JVM target 21 でビルドされており、inline fun (state/action) の利用側も 21 が必要。
    // foojay-resolver-convention (settings.gradle.kts) が JDK 21 を自動プロビジョニングする。
    jvmToolchain(21)

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_21 }
    }
    // ゲート B: koma の klib は Kotlin 2.3.20 ビルド。2.4.0 コンパイラからの後方互換読みの実測
    // (compile のみ。simulator 実行はしない)。
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":koma-strict-runtime"))
            implementation(libs.koma.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.koma.test)
        }
    }
}

dependencies {
    // commonMain の宣言だけを metadata コンパイルで 1 回処理し、生成物を全ターゲットで共有する。
    // per-platform (kspJvm 等) には配線しない (下の workaround 参照)。
    add("kspCommonMainMetadata", project(":koma-strict-ksp"))
}

// ---- cream の KSP×KMP workaround (ksp-plugin-project-setup/references/build-toolchain.md が正) ----
// kspCommonMainKotlinMetadata の出力を commonMain の srcDir に追加し、
// 全コンパイルを metadata KSP の後に走らせる。
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}
tasks.configureEach {
    if (name.startsWith("ksp") && name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.named("kspCommonMainKotlinMetadata"))
        // per-platform *main* の重複生成だけ無効化 (*Test の ksp タスクは温存)。
        // 本モジュールは per-platform ksp を配線していないため実質 no-op だが、cream の形を維持する
        if (!name.contains("Test")) enabled = false
    }
}
// srcDir は素の path 追加のため、compile タスク側から明示的に依存を張る
// (KSP2 の KspAATask は KotlinCompilationTask ではないので循環しない)
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn(tasks.named("kspCommonMainKotlinMetadata"))
    }
}
