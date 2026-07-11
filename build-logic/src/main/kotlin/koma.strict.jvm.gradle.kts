// JVM only モジュール (KSP processor 等) の共通構成。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    explicitApi()
    jvmToolchain(17)

    compilerOptions {
        configureCommon()
    }
}
