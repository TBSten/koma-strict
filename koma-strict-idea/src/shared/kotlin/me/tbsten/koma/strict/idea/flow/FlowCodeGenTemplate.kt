package me.tbsten.koma.strict.idea.flow

internal abstract class SnippetGenerator<GetVariableContext> {
    abstract fun generate(context: GetVariableContext): String
}

internal typealias SnippetGeneratorVoid = SnippetGenerator<Unit>

internal fun SnippetGeneratorVoid.generate(template: String): String = generate(Unit)
