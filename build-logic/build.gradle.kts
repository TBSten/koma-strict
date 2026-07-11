plugins {
    `kotlin-dsl`
}

// precompiled script plugin (src/main/kotlin/*.gradle.kts) から各プラグインの DSL を
// 参照できるように、plugin marker (<id>:<id>.gradle.plugin:<version>) を implementation に載せる。
// バージョンは version catalog (gradle/libs.versions.toml) が SSoT。
dependencies {
    implementation(plugin(libs.plugins.kotlin.multiplatform))
    implementation(plugin(libs.plugins.kotlin.jvm))
    implementation(plugin(libs.plugins.android.kmp.library))
    implementation(plugin(libs.plugins.maven.publish))
}

fun plugin(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
