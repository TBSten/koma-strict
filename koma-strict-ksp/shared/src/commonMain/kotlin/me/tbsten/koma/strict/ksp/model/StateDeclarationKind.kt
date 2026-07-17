package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

/** Declaration kind of a leaf state (doc/internal/generate-strict-store-factory-dsl.md, "two forms of state declaration"). */
@InternalKomaStrictApi
public enum class StateDeclarationKind {
    /** interface declaration (recommended). Generates an Impl and, when a companion exists, a factory. */
    INTERFACE,

    /** data class declaration (legacy form). No Impl / factory is generated. */
    DATA_CLASS,

    /** data object declaration (legacy form). No Impl / factory is generated. */
    DATA_OBJECT,
}
