package me.tbsten.koma.strict.ksp.options

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * Severity of the dead-action diagnostic (an action handled by no state).
 *
 * TODO(doc/internal/generate-strict-store-factory-dsl.md): the option name is to be decided
 *   at implementation time (doc open issue 3). Re-confirm the name when starting the DSL
 *   implementation and rename if needed.
 */
@InternalKomaStrictApi
public enum class DeadActionSeverity {
    WARNING,
    ERROR,
    ;

    public companion object {
        public val default: DeadActionSeverity = WARNING
    }
}
