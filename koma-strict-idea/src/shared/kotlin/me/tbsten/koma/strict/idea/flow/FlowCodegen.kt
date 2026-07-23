package me.tbsten.koma.strict.idea.flow

import me.tbsten.koma.strict.idea.AssertState
import me.tbsten.koma.strict.idea.DispatchAction
import me.tbsten.koma.strict.idea.GenerateDispatchActionAndAssertStateStatementsBlock
import me.tbsten.koma.strict.idea.GenerateDispatchActionAndAssertStateStatementsBlockContext
import me.tbsten.koma.strict.idea.RaiseException
import me.tbsten.koma.strict.idea.StateOrAction
import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel

// Code generation from a [RecordedFlow] (`ide-test-code.md`). Two outputs, both pure functions of the
// diagram model + flow so they are golden-testable without the IDE:
//   - generateFlowSpec  … the koma-strict-native @FlowSpec annotation (always valid, no TODOs).
//   - generateKomaTest  … a koma-test scaffold (real startAndAwait / dispatchAndAwait / currentState);
//     store construction stays a TODO since the plugin knows the flow's shape but not the store's behavior.

/** Package of the koma-strict annotations the generated `@FlowSpec` imports. */
private const val KOMA_STRICT_PKG = "me.tbsten.koma.strict"

/** The generated `@FlowSpec` annotation class plus the imports its insertion needs (`ide-test-code.md` F8). */
data class GeneratedFlowSpec(
    /** The annotation class name applied to the root, e.g. `RecordedFlow`. */
    val annotationClassName: String,
    /** The full `@FlowSpec(...) annotation class <Name>` declaration text (what the tab shows / Copy yields). */
    val declaration: String,
    /** Fully-qualified imports the target file needs (koma-strict annotations; deduped on insert). */
    val requiredImports: List<String>,
)

/**
 * Generates the `@FlowSpec` annotation for [flow]. [flowName] is the annotation class name (an
 * identifier, e.g. `RecordedFlow`); the `name = "..."` argument is its humanized form
 * ([humanizeFlowName]). State refs are rooted at the store's simple name (`FeedState.Stable.Idle`),
 * action / exception refs come from each transition's [FlowTransition.typeRef] (`FeedAction.Retry`).
 */
fun generateFlowSpec(model: StoreDiagramModel, flow: RecordedFlow, flowName: String): GeneratedFlowSpec {
    val root = model.root.simpleName
    val refs = flowStepRefs(root, flow)
    val steps = refs.joinToString("\n") { "        FlowStep($it::class)," }
    val declaration = buildString {
        append("@FlowSpec(\n")
        append("    name = \"").append(humanizeFlowName(flowName)).append("\",\n")
        append("    steps = [\n")
        append(steps).append('\n')
        append("    ],\n")
        append(")\n")
        append("annotation class ").append(flowName)
    }
    val imports = buildList {
        add("$KOMA_STRICT_PKG.FlowSpec")
        add("$KOMA_STRICT_PKG.FlowStep")
        if (flow.transitions.any { it.kind == EdgeKind.ENTER }) add("$KOMA_STRICT_PKG.OnEnter")
        if (flow.transitions.any { it.stay }) add("$KOMA_STRICT_PKG.Stay")
    }
    return GeneratedFlowSpec(annotationClassName = flowName, declaration = declaration, requiredImports = imports)
}

/**
 * Generates a koma-test scaffold for [flow] targeting [frameworkName] (a key of
 * [GenerateTestSnippetContext.All] / [GenerateDispatchActionAndAssertStateStatementsBlock.All], e.g.
 * `kotlin.test` or `kotest FreeSpec`). The recorded transitions are mapped to dispatch / assert / raise
 * statements ([buildStateAndActions]); the chosen framework's snippet wraps them and its asserts block
 * renders each state assertion. Store construction stays a `TODO` — the plugin can't synthesize behavior.
 */
fun generateKomaTest(
    model: StoreDiagramModel,
    flow: RecordedFlow,
    flowName: String,
    frameworkName: String = defaultTestFramework,
    testClassName: String? = null,
    testCaseName: String? = null,
): String {
    val root = model.root.simpleName
    val snippet = GenerateTestSnippetContext.All[frameworkName] ?: GenerateTestSnippetContext.All.values.first()
    val block = GenerateDispatchActionAndAssertStateStatementsBlock.All[frameworkName]
        ?: GenerateDispatchActionAndAssertStateStatementsBlock.All.values.first()
    val stateAndActions = buildStateAndActions(root, flow)
    return snippet.generate(
        GenerateTestSnippetContext(
            packageName = model.packageName,
            // testClassName / testCaseName はパネルの TextField 上書きを優先し、空なら派生デフォルト。
            testClassName = testClassName?.takeIf { it.isNotBlank() } ?: defaultTestClassName(model),
            testCaseName = testCaseName?.takeIf { it.isNotBlank() } ?: humanizeFlowName(flowName),
            storeType = storeType(root),
            initialStateName = flow.initial?.let { stateRef(root, it) } ?: "/* initial */",
            dispatchActionAndAssertStateStatementsBlock = { indent ->
                block.generate(
                    GenerateDispatchActionAndAssertStateStatementsBlockContext(
                        indent = indent,
                        storeName = STORE_VARIABLE,
                        stateAndActions = stateAndActions,
                    ),
                )
            },
        ),
    )
}

/** The generated store variable name shared across snippets. */
private const val STORE_VARIABLE = "store"

/** Framework names offered by the Test Code tab (keys of the snippet / asserts-block maps). */
internal val testFrameworkNames: List<String> = GenerateTestSnippetContext.All.keys.toList()

/** Default framework name for [generateKomaTest]. */
internal const val defaultTestFramework: String = "kotlin.test"

/** Default test class name for [model]'s store (seeds the panel field / file name): `FeedState` -> `FeedStoreTest`. */
internal fun defaultTestClassName(model: StoreDiagramModel): String = storeSimpleName(model.root.simpleName) + "Test"

/** File name for a generated test class named [testClassName], e.g. `FeedStoreTest` -> `FeedStoreTest.kt`. */
internal fun komaTestFileName(testClassName: String): String = "$testClassName.kt"

/** Maps the recorded [flow] transitions to the dispatch / assert / raise statements to generate. */
private fun buildStateAndActions(root: String, flow: RecordedFlow): List<StateOrAction> = buildList {
    for (t in flow.transitions) {
        val targetRef = t.target?.let { stateRef(root, it) }
        when (t.kind) {
            EdgeKind.ENTER -> targetRef?.let { add(AssertState(it)) }
            EdgeKind.ACTION -> {
                add(DispatchAction(t.typeRef ?: t.label, actionConstructorProperties = null))
                // stay は現状維持なので from を、それ以外は遷移先を assert する。
                val assertRef = if (t.stay) t.fromId?.let { stateRef(root, it) } else targetRef
                assertRef?.let { add(AssertState(it)) }
            }
            EdgeKind.RECOVER -> {
                add(RaiseException(t.typeRef ?: t.label))
                targetRef?.let { add(AssertState(it)) }
            }
            EdgeKind.INITIAL -> Unit
        }
    }
}

/** The atomic `FlowStep` references (state / OnEnter / action / Stay), in order, including the initial node. */
private fun flowStepRefs(root: String, flow: RecordedFlow): List<String> = buildList {
    flow.initial?.let { add(stateRef(root, it)) }
    for (t in flow.transitions) {
        add(
            when (t.kind) {
                EdgeKind.ENTER -> "OnEnter"
                EdgeKind.ACTION, EdgeKind.RECOVER -> t.typeRef ?: t.label
                EdgeKind.INITIAL -> "OnEnter" // 実際には INITIAL は記録されない (dropdown が initial を担う)
            },
        )
        add(if (t.stay) "Stay" else t.target?.let { stateRef(root, it) } ?: "Stay")
    }
}

/** A code reference to a state, rooted at the store's simple name: `FeedState` + `Stable.Idle` -> `FeedState.Stable.Idle`. */
private fun stateRef(root: String, id: StateId): String = if (id.isRoot) root else "$root.${id.dotted}"

/** Base name of the store family for a state root: `FeedState` -> `Feed`, `Tabs` -> `Tabs`. */
private fun storeBaseName(root: String): String = if (root.endsWith("State")) root.removeSuffix("State") else root

/** Default generated store class name (test class / file name base): `FeedState` -> `FeedStore`. */
private fun storeSimpleName(root: String): String = storeBaseName(root) + "Store"

/**
 * The generic koma store type of the generated `val store`, derived from the state root by pairing it
 * with the conventional Action / Event siblings: `FeedState` -> `Store<FeedState, FeedAction, FeedEvent>`.
 * A scaffold guess (the plugin only sees the State hierarchy); the developer fixes Event when it's `Nothing`.
 */
private fun storeType(root: String): String {
    val base = storeBaseName(root)
    return "Store<$root, ${base}Action, ${base}Event>"
}

/**
 * Humanizes an annotation-class name into the `@FlowSpec(name = "...")` value: split on camel-case
 * boundaries and lowercase (`RecordedFlow` -> `recorded flow`, `InitializeHappyPathFlow` ->
 * `initialize happy path flow`).
 */
fun humanizeFlowName(name: String): String {
    if (name.isBlank()) return name
    val sb = StringBuilder()
    for ((i, c) in name.withIndex()) {
        if (c.isUpperCase() && i > 0 && (name[i - 1].isLowerCase() || name[i - 1].isDigit())) sb.append(' ')
        sb.append(c.lowercaseChar())
    }
    return sb.toString()
}
