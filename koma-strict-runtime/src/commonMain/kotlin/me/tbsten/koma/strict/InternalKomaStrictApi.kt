package me.tbsten.koma.strict

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@RequiresOptIn(message = "This API is used internally within koma-strict. Direct use is not recommended.")
public annotation class InternalKomaStrictApi
