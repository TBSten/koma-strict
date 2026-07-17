package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

// KSAnnotation の引数読み取りヘルパ (KSP2 実測に基づく):
// - KClass 単体引数の value は KSType
// - Array<KClass<*>> 引数の value は List<KSType>
// - 省略された引数は defaultArguments 側から補完する

internal fun KSAnnotation.argumentValue(name: String): Any? =
    arguments.firstOrNull { it.name?.asString() == name }?.value
        ?: defaultArguments.firstOrNull { it.name?.asString() == name }?.value

/** Reads an `Array<KClass<*>>` argument as a list of KSTypes (empty when unspecified). */
internal fun KSAnnotation.kClassArrayArgument(name: String): List<KSType> =
    (argumentValue(name) as? List<*>).orEmpty().filterIsInstance<KSType>()

/** Reads a single `KClass<*>` argument (null when unspecified). */
internal fun KSAnnotation.kClassArgument(name: String): KSType? = argumentValue(name) as? KSType

/**
 * Reads a single `KClass<*>` argument as a class declaration.
 * `Nothing::class` (the annotation-side "unspecified" sentinel) is reduced to null.
 * On KSP2 (JVM), `Nothing::class` resolves to `java.lang.Void` (empirically verified),
 * so both are checked.
 */
internal fun KSAnnotation.kClassArgumentDeclaration(name: String): KSClassDeclaration? =
    kClassArgument(name)
        ?.resolveToClassDeclaration()
        ?.takeUnless { it.qualifiedName?.asString() in setOf("kotlin.Nothing", "java.lang.Void") }

/**
 * The class declaration of a generic annotation's type argument
 * (`@OnAction<A>` / `@OnRecover<E>`).
 * That KSP2 can resolve type arguments of SOURCE-retention generic annotations is
 * empirically verified by GenericAnnotationTypeArgumentSmokeTest.
 */
internal fun KSAnnotation.typeArgumentClassDeclaration(): KSClassDeclaration? =
    annotationType
        .resolve()
        .arguments
        .firstOrNull()
        ?.type
        ?.resolve()
        ?.resolveToClassDeclaration()
