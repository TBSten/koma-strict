package me.tbsten.koma.strict.dsl

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.throwDuplicateBuilderEntry

/**
 * A write-once slot used by the generated builder receivers (`actions { ... }` / `states { ... }`)
 * to hold one registered handler / child state / escape block.
 *
 * Assigning twice fails fast via [throwDuplicateBuilderEntry] with [owner] / [entry] — this is how
 * the builder form reports a duplicate registration. Missing-entry (exhaustiveness) reporting
 * stays in the generated `build()` so every still-unset name can be collected in declaration order
 * for a single [me.tbsten.koma.strict.throwMissingBuilderEntries] message; [isSet] and [getOrNull]
 * are the hooks it uses.
 *
 * This unifies the per-member `if (x != null) throwDuplicateBuilderEntry(...)` boilerplate the
 * generated builders used to repeat, integrating it with the existing `BuilderFailFast` helpers.
 *
 * This is generated-code-only plumbing. It is guarded by @[InternalKomaStrictApi]: opting in and
 * constructing / using it by hand is unsupported and entirely at your own risk.
 *
 * @param owner Source reference of the declaring state (see [throwDuplicateBuilderEntry]).
 * @param entry The builder member this slot backs (a handler / child-state / `configure` name).
 */
@InternalKomaStrictApi
public class SetOnceSlot<T : Any>(
    private val owner: String,
    private val entry: String,
) {
    private var stored: T? = null

    /** Whether [set] has already stored a value. */
    public val isSet: Boolean get() = stored != null

    /** Stores [value], or fails fast via [throwDuplicateBuilderEntry] if a value was already stored. */
    public fun set(value: T) {
        if (stored != null) throwDuplicateBuilderEntry(owner, entry)
        stored = value
    }

    /** The stored value, or `null` if [set] was never called. */
    public fun getOrNull(): T? = stored
}
