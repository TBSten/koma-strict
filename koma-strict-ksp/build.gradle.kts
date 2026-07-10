plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    compilerOptions.optIn.addAll(
        "com.google.devtools.ksp.KspExperimental",
        "me.tbsten.koma.strict.InternalKomaStrictApi",
    )
    // NOTE: cream (Kotlin 2.2) では -Xcontext-parameters が必要だったが、Kotlin 2.4 では
    //       context parameters が stable 化されフラグは冗長 (警告が出る) ため付けない。
    sourceSets.named("test") {
        // kctfork の KotlinCompilation API が ExperimentalCompilerApi を要求するため test のみ opt-in
        languageSettings.optIn("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

dependencies {
    implementation(project(":koma-strict-runtime"))
    implementation(project(":koma-strict-ksp:shared"))
    implementation(libs.ksp.api)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kctfork.core)
    testImplementation(libs.kctfork.ksp)
    testImplementation(libs.konsist)
    testImplementation(libs.kotlinpoet)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // kctfork の in-process compile は classloader が蓄積し、デフォルト 512m の worker heap では
    // suite 全体を回すと OOM が無関係なテストに波及する。heap を増やし worker JVM を定期リサイクル。
    maxHeapSize = "2g"
    forkEvery = 25L
    // golden をモジュール直下 snapshots/ に置くため、絶対パスを worker JVM へ渡す (IDE 実行の workingDir 差異対策)
    systemProperty("koma.strict.snapshot.dir", layout.projectDirectory.dir("snapshots").asFile.absolutePath)
    // `-Dkoma.strict.snapshot.update=true` で golden snapshot を再生成可能にする。
    // -D はテスト worker JVM に自動伝播しないため明示転送が必須。
    // configuration-cache=true のため providers API 経由で読む (-D 値変更は CC 入力として検知される)。
    providers.systemProperty("koma.strict.snapshot.update").orNull?.let {
        systemProperty("koma.strict.snapshot.update", it)
    }
    // golden は test runtime classpath 外 (src/test/resources ではない) なので、明示 input 宣言が無いと
    // golden 編集時に build cache / up-to-date 判定でテストがスキップされる
    inputs.dir(layout.projectDirectory.dir("snapshots"))
        .withPropertyName("komaStrictSnapshotGoldens")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

//Publishing your Kotlin Multiplatform library to Maven Central
//https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
mavenPublishing {
    publishToMavenCentral()
    coordinates(group.toString(), "koma-strict-ksp", version.toString())

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
