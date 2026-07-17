package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier

// 継承関係の走査ヘルパ (actions / events 推論・subtype 検証・dead action 分析が共有)。

/** The supertype closure (excluding the receiver itself). BFS, nearest first, deduplicated by qualifiedName. */
internal fun KSClassDeclaration.supertypeClosure(): List<KSClassDeclaration> {
    val result = mutableListOf<KSClassDeclaration>()
    val visited = mutableSetOf<String>()
    var frontier = listOf(this)
    while (frontier.isNotEmpty()) {
        frontier =
            frontier.flatMap { declaration ->
                declaration.superTypes.mapNotNull { superType ->
                    superType.resolve().resolveToClassDeclaration()
                }
            }.filter { superDeclaration ->
                val fqn = superDeclaration.qualifiedName?.asString() ?: return@filter false
                visited.add(fqn)
            }
        result += frontier
    }
    return result
}

/** Whether the receiver itself is [other] or its supertype closure contains [other]. */
internal fun KSClassDeclaration.isSubtypeOfOrSelf(other: KSClassDeclaration): Boolean {
    val otherFqn = other.qualifiedName?.asString() ?: return false
    return isSubtypeOfOrSelf(otherFqn)
}

/** Whether the receiver itself or its supertype closure contains a type with the FQN [fqn]. */
internal fun KSClassDeclaration.isSubtypeOfOrSelf(fqn: String): Boolean =
    qualifiedName?.asString() == fqn ||
        supertypeClosure().any { it.qualifiedName?.asString() == fqn }

/**
 * Whether this is a subtype of `kotlin.Exception`. On the JVM, kotlin.Exception is a
 * typealias of `java.lang.Exception`, so both FQNs are accepted.
 */
internal fun KSClassDeclaration.isExceptionSubtype(): Boolean =
    isSubtypeOfOrSelf("kotlin.Exception") || isSubtypeOfOrSelf("java.lang.Exception")

/**
 * Sealed supertype candidates (including the receiver itself if it is sealed). BFS, nearest first.
 * The candidate sequence for the "common sealed supertype" inference of actions / events.
 */
internal fun KSClassDeclaration.sealedCandidates(): List<KSClassDeclaration> =
    (listOf(this) + supertypeClosure()).filter { Modifier.SEALED in it.modifiers }
