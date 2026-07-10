package me.tbsten.koma.strict

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@RequiresOptIn(message = "This API is used internally within koma-strict. Direct use is not recommended.")
public annotation class InternalKomaStrictApi
