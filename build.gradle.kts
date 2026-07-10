plugins {
    alias(libs.plugins.kotlin.multiplatform).apply(false)
    alias(libs.plugins.kotlin.jvm).apply(false)
    alias(libs.plugins.android.kmp.library).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
}

allprojects {
    group = "me.tbsten.koma.strict"
    version = rootProject.libs.versions.koma.strict.get()
}
