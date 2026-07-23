import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    // IDE ビルド向けの Kotlin。本体 (koma-strict) の Kotlin/AGP toolchain とは独立させる。
    kotlin("jvm") version "2.3.0"
    // @Composable を解釈する Compose Compiler plugin。plugin 本体 (bundled Jewel) / preview
    // (standalone Jewel) の両 source set の Kotlin コンパイルに効かせる。
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    // preview の standalone Compose Desktop 依存 (compose.desktop.currentOs) を引くための plugin。
    // plugin 本体は bundledModule の Compose を使うので、この plugin は preview 用のみ。
    id("org.jetbrains.compose") version "1.10.1"
    // version は指定しない: settings の org.jetbrains.intellij.platform.settings 2.18.1 が
    // 既に classpath に載せているため、ここで version を付けると衝突する。
    id("org.jetbrains.intellij.platform")
}

group = "me.tbsten.koma.strict"
version = "0.1.0"

// repositories は settings.gradle.kts の dependencyResolutionManagement に集約 (FAIL_ON_PROJECT_REPOS)。

// 図ツールウィンドウの Composable は plugin 本体 (bundled Jewel) と preview (standalone Jewel) で
// 共有する。同じ Jewel/Compose API なので src/shared/kotlin を両 source set の srcDir に足し、
// それぞれの Compose 依存でソース共有 (二重コンパイル) する。
sourceSets {
    main {
        kotlin.srcDir("src/shared/kotlin")
    }
    create("preview") {
        kotlin.srcDir("src/shared/kotlin")
    }
}

// preview source set 用の依存構成 (source set 作成時に自動生成される)。
val previewImplementation: Configuration by configurations.getting

dependencies {
    intellijPlatform {
        // build 261 = IntelliJ IDEA 2026.1 (Android Studio Quail 2026.1.1 と同世代) をビルド SDK にする。
        // 2025.3 (build 253) 以降 Community(IC) は個別配布されず統合 IDEA に一本化されたため
        // intellijIdeaCommunity ではなく intellijIdea(...) を使う。Android SDK / Java plugin には依存しない。
        intellijIdea("2026.1")
        // Analysis API (K2) を内包する Kotlin plugin。
        bundledPlugin("org.jetbrains.kotlin")
        // Jewel (Compose) UI を 261 同梱の bundled module から引く (自前 Compose を持たない)。
        // plugin.xml 側にも同名モジュールを <dependencies><module> で宣言してランタイム解決させる。
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        // compose.foundation.desktop は compile classpath に runtime を伝播しないので明示追加する
        // (@Composable = androidx.compose.runtime)。
        bundledModule("intellij.libraries.compose.runtime.desktop")
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.skiko")
        // ヘッドレス機能テストの基盤 (BasePlatformTestCase など)。
        testFramework(TestFrameworkType.Platform)
    }
    // BasePlatformTestCase は JUnit4 系。2.0.0-rc1 以降 Platform は JUnit4 を供給しないので明示追加する。
    testImplementation("junit:junit:4.13.2")
    // preview の純粋な出力ゲート (PreviewChecks: alpha / stale 削除) をテストから叩けるようにする。
    // output (compiled class) のみ追加し preview の standalone Compose 依存は載せないので、
    // bundled Compose との二重ロードは起きない (PreviewChecks は Compose に触れない)。
    testImplementation(sourceSets["preview"].output)

    // preview: standalone Compose Desktop + Jewel Int UI。plugin distribution には載せない。
    // renderComposeScene は compose.desktop.currentOs (Skiko 同梱) にある。
    previewImplementation(compose.desktop.currentOs)

    val jewelForIde = "261.26222.65"
    previewImplementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.37.0-$jewelForIde")
    // AllIconsKeys (IDE バンドルアイコン) を standalone preview の classpath にも載せる。
    // これが無いと preview では platform アイコンが解決できずマゼンタのプレースホルダになる
    // (Jewel README「Icons」参照)。実 plugin は platform から解決するので不要。
    previewImplementation("com.jetbrains.intellij.platform:icons:$jewelForIde")
}

kotlin {
    // JBR / jvmTarget 21 (koma 本体の jvmTarget 21 とも一致)。
    jvmToolchain(21)
}

intellijPlatform {
    // 小さな plugin なので Settings 検索用インデックス生成 (headless IDE 起動) は省く。
    buildSearchableOptions = false
    pluginConfiguration {
        ideaVersion {
            // floor = build 261 (AS Quail 2026.1.1 / IJ 2026.1)。
            sinceBuild = "261"
            // untilBuild は無指定: AS の追従遅れ・将来の 261.x を締め出さない (ide.md §10)。
            untilBuild = provider { null }
        }
    }
}

tasks.test {
    // Kotlin plugin を K2 モードで動かす (Analysis API を使うため)。
    systemProperty("idea.kotlin.plugin.use.k2", "true")
}

// preview: 共有 Composable を standalone Jewel + renderComposeScene で PNG に焼く (IDE 起動不要)。
// JBR21 の headless + Skiko SOFTWARE で回す (ide.dev.md の見た目自走ループ)。
// 同一マシンでは描画がバイト決定的なので、golden (snapshots/preview) との VRT ゲートを掛けられる:
//   updatePreview = 描画して golden を作り直す (gallery index.html も生成)
//   verifyPreview = 描画して golden と比較、差分があれば fail (report は build/preview/report)
fun registerPreviewTask(name: String, mode: String, desc: String) = tasks.register<JavaExec>(name) {
    group = "koma preview"
    description = desc
    mainClass.set("me.tbsten.koma.strict.idea.preview.PreviewMainKt")
    classpath = sourceSets["preview"].runtimeClasspath
    jvmArgs("-Djava.awt.headless=true", "-Dskiko.renderApi=SOFTWARE")
    args(mode)
}
registerPreviewTask(
    "updatePreview",
    "update",
    "Render preview PNGs, write the gallery, and force-refresh the golden snapshots under snapshots/preview.",
)
registerPreviewTask(
    "verifyPreview",
    "verify",
    "Render preview PNGs and fail the build if any differs from the golden snapshots (VRT gate).",
)
