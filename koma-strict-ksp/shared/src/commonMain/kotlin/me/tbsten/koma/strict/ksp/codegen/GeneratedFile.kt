package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * Content of one generated Kotlin file (KSP-independent).
 *
 * [content] holds declarations only — the `package` line and import boilerplate are
 * owned by the writer (`createNewKotlinFile` in :koma-strict-ksp).
 * The package is always the same as the declarations (the root state), so it is not held here.
 */
@InternalKomaStrictApi
public data class GeneratedFile(
    /** File name without `.kt` (`LceState.Loading.generated` form). */
    val fileName: String,
    val content: String,
)
