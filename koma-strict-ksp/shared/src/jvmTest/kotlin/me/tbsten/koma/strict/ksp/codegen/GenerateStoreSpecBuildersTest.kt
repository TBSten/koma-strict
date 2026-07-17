package me.tbsten.koma.strict.ksp.codegen

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.GroupNode

/**
 * builder 形式 (第 3 の書き方) の codegen 単体テスト。
 * 入力 model は [StoreSpecCodegenFixtures] を共有し、生成物の要点 (builder 型・overload・
 * fail-fast 配線・生成条件 = enter / exit 宣言の有無) を検証する。
 * 全文の固定と overload 解決の実証は kctfork ベースの golden (ProcessStoreSpecSpec) が担う。
 */
internal class GenerateStoreSpecBuildersTest :
    FreeSpec({
        val lceByName = generateStoreSpecFiles(lceCodegenSpec).associate { it.fileName to it.content }

        "enter や exit の宣言が無い leaf には actions builder と builder overload が生成される" {
            val content = lceByName.getValue("LceState.Content.generated")
            withClue(content) {
                // builder 型は @DslMarker 併記 + @OptIn(runtime SetOnceSlot 利用) + internal constructor
                content shouldContain
                    "@KomaStrictDsl\n@koma.core.KomaStoreDsl\n@OptIn(InternalKomaStrictApi::class)\n" +
                    "public class ContentActionsBuilder internal constructor() {"
                // 登録先は runtime の SetOnceSlot。重複 fail-fast は set() が持つ
                content shouldContain
                    "    private val reload = SetOnceSlot<suspend ContentReloadScope.() -> ContentReloadReaction>" +
                    "(\"LceState.Content\", \"reload\")"
                // companion 拡張とミラーの両方に builder overload (単一 lambda) が生える
                content shouldContain
                    "public fun LceState.Content.Companion.actions(build: ContentActionsBuilder.() -> Unit): " +
                    "ContentHandlers = ContentActionsBuilder().apply(build).build()"
                content shouldContain
                    "    public fun actions(build: ContentActionsBuilder.() -> Unit): " +
                    "ContentHandlers = ContentActionsBuilder().apply(build).build()"
                // member = 宣言済み handler と同名 + configure。登録は slot.set() へ委譲
                content shouldContain
                    "    public fun reload(handler: suspend ContentReloadScope.() -> ContentReloadReaction) " +
                    "{ reload.set(handler) }"
                content shouldContain
                    "    public fun configure(block: koma.core.StoreBuilder.StateHandlerConfig" +
                    "<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit) { configure.set(block) }"
                // build() は不足を宣言順に列挙して fail-fast し、named 形式と同じ Handlers を構築する
                content shouldContain "            \"reload\".takeIf { !reload.isSet },"
                content shouldContain
                    "        if (missing.isNotEmpty()) throwMissingBuilderEntries(\"LceState.Content\", missing)"
                content shouldContain "        return ContentHandlers(reload.getOrNull()!!, configure.getOrNull() ?: {})"
                // KDoc に「網羅チェックは構築時」を明記する (仕様)
                content shouldContain "checked **at build time**"
            }
        }

        "enter 宣言を持つ leaf には builder overload 自体が生成されない" {
            val loading = lceByName.getValue("LceState.Loading.generated")
            withClue(loading) {
                loading shouldNotContain "LoadingActionsBuilder"
                loading shouldNotContain "build:"
            }
        }

        "自宣言つき中間 sealed の states builder は default 名 member を含み合成型を組む" {
            val refresh =
                generateStoreSpecFiles(flowCodegenSpec)
                    .first { it.fileName == "FlowState.Refresh.generated" }
                    .content
            withClue(refresh) {
                refresh shouldContain
                    "@KomaStrictDsl\n@koma.core.KomaStoreDsl\n@OptIn(InternalKomaStrictApi::class)\n" +
                    "public class RefreshGroupBuilder internal constructor() {"
                refresh shouldContain
                    "public fun FlowState.Refresh.Companion.states(build: RefreshGroupBuilder.() -> Unit): " +
                    "RefreshHandlers = RefreshGroupBuilder().apply(build).build()"
                // 値渡し member (default 名 + 子 state 名) と builder ネスト overload の両方が生える
                refresh shouldContain "    public fun default(handlers: RefreshDefaultHandlers) { default.set(handlers) }"
                refresh shouldContain "    public fun default(build: RefreshDefaultActionsBuilder.() -> Unit) {"
                refresh shouldContain "    public fun failed(handlers: RefreshFailedHandlers) { failed.set(handlers) }"
                refresh shouldContain "    public fun failed(build: RefreshFailedActionsBuilder.() -> Unit) {"
                // 宣言ゼロの子 (Running) は member 自体が生えない
                refresh shouldNotContain "fun running("
                // v4 登録 builder は escape を集めない (空 scope で埋めて構築する)
                refresh shouldContain
                    "        return RefreshHandlers(default.getOrNull()!!, failed.getOrNull()!!, RefreshStatesConfigureScope())"
            }
        }

        "自宣言の無い中間 sealed の states builder は GroupHandlers を組む (default member なし)" {
            val refresh =
                generateStoreSpecFiles(groupOnlyFlowCodegenSpec)
                    .first { it.fileName == "FlowState.Refresh.generated" }
                    .content
            withClue(refresh) {
                refresh shouldContain
                    "public fun FlowState.Refresh.Companion.states(build: RefreshGroupBuilder.() -> Unit): " +
                    "RefreshGroupHandlers = RefreshGroupBuilder().apply(build).build()"
                refresh shouldContain
                    "        return RefreshGroupHandlers(failed.getOrNull()!!, RefreshStatesConfigureScope())"
                refresh shouldNotContain "fun default("
            }
        }

        "exit 宣言を持つ default ブロックには actions builder が生えず値渡し member だけになる" {
            // Refresh の自宣言に exit を足した variant — builder の生成条件が「enter / exit 宣言の有無」に
            // 依存することの検証 (一元 policy は AppendHandlersBuilder.kt)
            val refreshWithExit =
                flowCodegenSpec.copy(
                    root =
                        flowCodegenSpec.root.copy(
                            children =
                                flowCodegenSpec.root.children.map { node ->
                                    if (node is GroupNode) node.copy(exit = ExitHandler()) else node
                                },
                        ),
                )
            val refresh =
                generateStoreSpecFiles(refreshWithExit)
                    .first { it.fileName == "FlowState.Refresh.generated" }
                    .content
            withClue(refresh) {
                // default ブロックの builder 型・actions(build) overload・builder ネスト overload は生えない
                refresh shouldNotContain "RefreshDefaultActionsBuilder"
                // group の states builder 自体は生え、default は値渡し member のみで登録する
                refresh shouldContain "states(build: RefreshGroupBuilder.() -> Unit)"
                refresh shouldContain "    public fun default(handlers: RefreshDefaultHandlers) {"
            }
        }

        "生成 top-level 型名の列挙は builder 型を含む (衝突診断の入力)" {
            val lceNames = generatedTopLevelTypeNames(lceCodegenSpec)
            withClue(lceNames) {
                lceNames shouldContain "ContentActionsBuilder"
                // enter 宣言つき Loading の builder は生成されないので列挙にも含まれない
                lceNames shouldNotContain "LoadingActionsBuilder"
            }
            val flowNames = generatedTopLevelTypeNames(flowCodegenSpec)
            withClue(flowNames) {
                flowNames shouldContain "RefreshGroupBuilder"
                flowNames shouldContain "RefreshDefaultActionsBuilder"
                flowNames shouldContain "RefreshFailedActionsBuilder"
                flowNames shouldContain "IdleActionsBuilder"
            }
        }
    })
