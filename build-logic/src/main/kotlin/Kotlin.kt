import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerToolOptions

fun KotlinCommonCompilerToolOptions.configureCommon() {
    freeCompilerArgs.addAll(
        "-Xcontext-parameters",
    )
}
