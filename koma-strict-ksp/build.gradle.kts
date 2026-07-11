plugins {
    id("koma.strict.jvm")
    id("koma.strict.snapshot-test")
    id("koma.strict.publish")
}

kotlin {
    compilerOptions.optIn.addAll(
        "com.google.devtools.ksp.KspExperimental",
        "me.tbsten.koma.strict.InternalKomaStrictApi",
    )
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
