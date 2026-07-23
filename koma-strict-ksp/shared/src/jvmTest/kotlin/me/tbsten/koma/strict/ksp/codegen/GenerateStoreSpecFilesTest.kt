package me.tbsten.koma.strict.ksp.codegen

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StateProp
import me.tbsten.koma.strict.ksp.model.StateVisibility
import me.tbsten.koma.strict.ksp.model.StoreSpec
import me.tbsten.koma.strict.ksp.model.TransitionSpec
import me.tbsten.koma.strict.ksp.model.TypeRef

/**
 * codegen (pure 関数) の単体テスト。samples.md の LCE ケースを手組み model で入力し、
 * 生成物の要点 (ファイル構成・可視性・ホワイトリスト) を検証する。
 * 全文の固定は kctfork ベースの golden (feature/storeSpec の ProcessStoreSpecSpec) が担う。
 */
internal class GenerateStoreSpecFilesTest :
    FreeSpec({
        // 手組み model は StoreSpecCodegenFixtures.kt を共有する (builder 形式は GenerateStoreSpecBuildersTest)
        val lceSpec = lceCodegenSpec

        val files = generateStoreSpecFiles(lceSpec)
        val byName = files.associate { it.fileName to it.content }

        "1 node = 1 file + storeSpec ファイルが生成される" {
            files.map { it.fileName } shouldContainExactly
                listOf(
                    "LceState.Loading.generated",
                    "LceState.Content.generated",
                    "LceState.storeSpec.generated",
                )
        }

        "interface 宣言 leaf には private Impl と factory が生成される" {
            val loading = byName.getValue("LceState.Loading.generated")
            loading shouldContain "private data object LoadingImpl : LceState.Loading"
            loading shouldContain
                "public operator fun LceState.Loading.Companion.invoke(): LceState.Loading = LoadingImpl"
        }

        "Stay 未宣言の Reaction には Stay が存在しない" {
            val loading = byName.getValue("LceState.Loading.generated")
            loading shouldContain "public sealed interface LoadingEnterReaction"
            // clearPendingActions の KDoc が "Staying (stayState)" に言及するため、
            // Reaction 要素と stayState 関数の非生成をピンポイントに見る
            loading shouldNotContain "data object Stay"
            loading shouldNotContain "public fun stayState"
        }

        "emit 宣言ゼロの Scope には eventSink 自体が生えない" {
            val content = byName.getValue("LceState.Content.generated")
            withClue(content) {
                content shouldContain "public class ContentReloadScope internal constructor("
                content shouldNotContain "eventSink"
            }
        }

        "per-handler Scope には KomaStrictDsl と koma の KomaStoreDsl が併記され runtime の HandlerScope 基底を継承する" {
            val loading = byName.getValue("LceState.Loading.generated")
            withClue(loading) {
                // DslMarker 併記の後に @OptIn(InternalKomaStrictApi) が付き、runtime 基底を継承する
                loading shouldContain
                    "@KomaStrictDsl\n@koma.core.KomaStoreDsl\n@OptIn(InternalKomaStrictApi::class)\npublic class LoadingEnterScope"
                // onClearPendingActions は private プロパティではなく基底へ渡す素の ctor param になる
                loading shouldContain "    onClearPendingActions: () -> Unit,"
                loading shouldContain ") : HandlerScope(onClearPendingActions)"
                // clearPendingActions passthrough 本体は runtime 基底が持つため生成側には出ない
                loading shouldNotContain "public fun clearPendingActions() {"
                loading shouldNotContain "private val onClearPendingActions"
            }
        }

        "leaf の actions() は末尾 optional configure を持ち Handlers が保持する" {
            val content = byName.getValue("LceState.Content.generated")
            withClue(content) {
                content shouldContain
                    "internal val configure: koma.core.StoreBuilder.StateHandlerConfig" +
                    "<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit,"
                content shouldContain
                    "    configure: koma.core.StoreBuilder.StateHandlerConfig" +
                    "<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit = {},"
                // leaf actions() の末尾は configure = trailing lambda 位置 (センチネルは付けない)
                content shouldNotContain "preventTrailingLambda"
            }
        }

        "root states() 拡張は scope lambda param + StoreBuilder receiver + @JvmName 付きで生成される" {
            val storeSpecFile = byName.getValue("LceState.storeSpec.generated")
            withClue(storeSpecFile) {
                storeSpecFile shouldContain "@kotlin.jvm.JvmName(\"lceStateStates\")"
                storeSpecFile shouldContain
                    "public fun koma.core.StoreBuilder<LceState, LceAction, LceEvent>.states("
                // param は receiver 付き関数型 (scope lambda と値渡しの両対応)
                storeSpecFile shouldContain "    loading: LoadingHandlersScope.() -> LoadingHandlers,"
                storeSpecFile shouldContain "state<LceState.Loading> {"
                storeSpecFile shouldContain "is LoadingEnterReaction.Transition -> nextState { r.next }"
                // configure は state ブロック末尾で呼ばれ、koma scope の clearPendingActions が配線される
                storeSpecFile shouldContain "loading.configure(this)"
                storeSpecFile shouldContain "::clearPendingActions"
            }
        }

        "root states() は各 scope lambda を同名 local へ 1 回だけ評価する (意図的 shadowing + 抑制)" {
            val storeSpecFile = byName.getValue("LceState.storeSpec.generated")
            withClue(storeSpecFile) {
                storeSpecFile shouldContain "@Suppress(\"NAME_SHADOWING\")"
                storeSpecFile shouldContain "    val loading = LoadingHandlersScope().loading()"
                storeSpecFile shouldContain "    val content = ContentHandlersScope().content()"
            }
        }

        "Handlers は (Scope) -> 自身 を実装し invoke が自己返しする (値渡しと scope lambda の両対応)" {
            val loading = byName.getValue("LceState.Loading.generated")
            withClue(loading) {
                // Kotlin/JS が function interface 実装を禁じるため、この supertype は runtime 基底に上げず
                // 生成側 (store のターゲットのみコンパイル) に残す
                loading shouldContain ") : (LoadingHandlersScope) -> LoadingHandlers {"
                loading shouldContain "    override fun invoke(p1: LoadingHandlersScope): LoadingHandlers = this"
            }
        }

        "HandlersScope には DslMarker 併記と companion actions() の同シグネチャミラーが生成される" {
            val content = byName.getValue("LceState.Content.generated")
            withClue(content) {
                content shouldContain
                    "@KomaStrictDsl\n@koma.core.KomaStoreDsl\npublic class ContentHandlersScope internal constructor() {"
                content shouldContain "    public fun actions("
                // ミラーも leaf は末尾 configure (センチネルなし)
                content shouldContain
                    "        configure: koma.core.StoreBuilder.StateHandlerConfig" +
                    "<LceState, LceAction, LceEvent, LceState.Content>.() -> Unit = {},"
                content shouldContain "    ): ContentHandlers = ContentHandlers(reload, configure)"
            }
        }

        "per-store factory 関数が states() と同一 param 列 + context + configuration で create/restore 両方生成される" {
            val storeSpecFile = byName.getValue("LceState.storeSpec.generated")
            withClue(storeSpecFile) {
                // create{Root}Store: initial 候補 (LceState.Loading) に絞り込まれた overload
                storeSpecFile shouldContain "public fun createLceStore("
                storeSpecFile shouldContain "    initialState: LceState.Loading,"
                // restore{Root}Store: root 型のまま (どの state からでも起動できる)
                storeSpecFile shouldContain "public fun restoreLceStore("
                storeSpecFile shouldContain "    initialState: LceState,"
                storeSpecFile shouldContain "    context: kotlin.coroutines.CoroutineContext? = null,"
                storeSpecFile shouldContain
                    "    configuration: koma.core.StoreBuilder<LceState, LceAction, LceEvent>.() -> Unit = {},"
                storeSpecFile shouldContain "): koma.core.Store<LceState, LceAction, LceEvent> ="
                storeSpecFile shouldContain
                    "    koma.core.Store<LceState, LceAction, LceEvent>(initialState = initialState, context = context) {"
                // states() へ引数をそのまま転送し、末尾で configuration を呼ぶ (escape hatch)
                storeSpecFile shouldContain "            loading = loading,"
                storeSpecFile shouldContain "        configuration()"
                // factory の末尾は configuration = 意図された trailing lambda (センチネルは付けない)
                storeSpecFile shouldNotContain "Store(\n    preventTrailingLambda"
            }
        }

        "initial 未宣言の storeSpec では create{Root}Store は生成されず restore{Root}Store だけが生成される" {
            val noInitialSpec = lceSpec.copy(initial = emptyList())
            val storeSpecFile =
                generateStoreSpecFiles(noInitialSpec)
                    .first { it.fileName == "LceState.storeSpec.generated" }
                    .content
            withClue(storeSpecFile) {
                storeSpecFile shouldNotContain "fun createLceStore("
                storeSpecFile shouldContain "public fun restoreLceStore("
                storeSpecFile shouldContain "    initialState: LceState,"
            }
        }

        "自宣言 + 子を持つ中間 sealed には合成型と plus と states() の 2 overload が生成される" {
            // FlowState { Idle; @OnAction(Cancel) sealed Refresh { Running(宣言ゼロ); Failed(@OnAction Retry) } } 相当
            val flowByName = generateStoreSpecFiles(flowCodegenSpec).associate { it.fileName to it.content }
            val refresh = flowByName.getValue("FlowState.Refresh.generated")
            withClue(refresh) {
                // GroupHandlers は「子のみ」の束 (default は含まない) + states() escape の運搬
                refresh shouldContain
                    "public class RefreshGroupHandlers internal constructor(\n" +
                    "    internal val failed: RefreshFailedHandlers,\n" +
                    "    internal val configure: RefreshStatesConfigureScope,\n" +
                    ") : (RefreshGroupHandlersScope) -> RefreshGroupHandlers {"
                // 合成型 = 親側 param 型 (default + 子)
                refresh shouldContain
                    "public class RefreshHandlers internal constructor(\n" +
                    "    internal val default: RefreshDefaultHandlers,\n" +
                    "    internal val failed: RefreshFailedHandlers,\n" +
                    "    internal val configure: RefreshStatesConfigureScope,\n" +
                    ") : (RefreshHandlersScope) -> RefreshHandlers {"
                // plus 合成だけが DefaultHandlers + GroupHandlers から合成型を作る (escape も引き継ぐ)
                refresh shouldContain
                    "public operator fun RefreshDefaultHandlers.plus(children: RefreshGroupHandlers): RefreshHandlers ="
                refresh shouldContain "    RefreshHandlers(this, children.failed, children.configure)"
                // 従来形 (default 込みの states()) は合成型を直接返す overload として併存
                refresh shouldContain "    default: RefreshDefaultHandlersScope.() -> RefreshDefaultHandlers,"
                refresh shouldContain "): RefreshHandlers ="
                // 合成 scope は actions / states 両ミラーを持つ (scope lambda 内の plus 合成用)
                refresh shouldContain "public class RefreshHandlersScope internal constructor() {"
                refresh shouldContain "    public fun actions("
                refresh shouldContain "    public fun states("
            }
            val storeSpecFile = flowByName.getValue("FlowState.storeSpec.generated")
            withClue(storeSpecFile) {
                // 親 (root) 側の param 型は合成型 — GroupHandlers 単独も DefaultHandlers 単独も渡せない
                storeSpecFile shouldContain "    refresh: RefreshHandlersScope.() -> RefreshHandlers,"
            }
        }

        "自宣言の無い中間 sealed は従来どおり GroupHandlers が親側 param 型で plus は生えない" {
            val generated = generateStoreSpecFiles(groupOnlyFlowCodegenSpec).associate { it.fileName to it.content }
            val refresh = generated.getValue("FlowState.Refresh.generated")
            withClue(refresh) {
                refresh shouldContain "public class RefreshGroupHandlers internal constructor("
                refresh shouldNotContain "class RefreshHandlers "
                refresh shouldNotContain ".plus("
            }
            generated.getValue("FlowState.storeSpec.generated") shouldContain
                "    refresh: RefreshGroupHandlersScope.() -> RefreshGroupHandlers,"
        }

        "同名 prop の持ち越し (property matching) はデフォルト値になる" {
            val spec =
                lceSpec.copy(
                    root =
                        lceSpec.root.copy(
                            children =
                                lceSpec.root.children.map { node ->
                                    if (node is LeafNode && node.simpleName == "Content") {
                                        node.copy(
                                            actions =
                                                node.actions.map { action ->
                                                    action.copy(
                                                        transition =
                                                            TransitionSpec.of(
                                                                targets = listOf(StatePath("Content")),
                                                                declaredStay = true,
                                                            ),
                                                    )
                                                },
                                        )
                                    } else {
                                        node
                                    }
                                },
                        ),
                )
            val content =
                generateStoreSpecFiles(spec)
                    .first { it.fileName == "LceState.Content.generated" }
                    .content
            withClue(content) {
                content shouldContain "data: String = state.data,"
                content shouldContain "public data object Stay : ContentReloadReaction"
                content shouldContain "public fun stayState(): ContentReloadReaction = ContentReloadReaction.Stay"
            }
        }

        "internal な spec では支援型・factory・states() 拡張が internal になる" {
            val internalSpec = lceSpec.copy(visibility = StateVisibility.INTERNAL)
            val generated = generateStoreSpecFiles(internalSpec).associate { it.fileName to it.content }
            val loading = generated.getValue("LceState.Loading.generated")
            withClue(loading) {
                loading shouldContain "internal operator fun LceState.Loading.Companion.invoke()"
                loading shouldContain "internal sealed interface LoadingEnterReaction"
                loading shouldContain "internal class LoadingEnterScope internal constructor("
                loading shouldContain "internal fun LceState.Loading.Companion.actions("
                // Impl は可視性ポリシーどおり private のまま
                loading shouldContain "private data object LoadingImpl"
                // top-level 宣言に public は残らない (メンバーは containing 型が制限するため public のまま)
                loading.lineSequence().none { it.startsWith("public ") } shouldBe true
            }
            val storeSpecFile = generated.getValue("LceState.storeSpec.generated")
            storeSpecFile shouldContain
                "internal fun koma.core.StoreBuilder<LceState, LceAction, LceEvent>.states("
            // per-store factory も可視性を継承する (create/restore 両方)
            storeSpecFile shouldContain "internal fun createLceStore("
            storeSpecFile shouldContain "internal fun restoreLceStore("
        }

        "leaf が継承 prop を狭い型で override すると leaf 側の型が生成に使われる (子勝ち)" {
            // Content が (仮想の) 祖先 prop `data: CharSequence` を `data: String` で override した形。
            // 祖先勝ちだと Impl が `override val data: CharSequence` になり
            // `LceState.Content` (data: String) を満たせずコンパイル不能になる。
            val spec =
                lceSpec.copy(
                    root =
                        lceSpec.root.copy(
                            props = listOf(StateProp(name = "data", type = "CharSequence")),
                        ),
                )
            val content =
                generateStoreSpecFiles(spec)
                    .first { it.fileName == "LceState.Content.generated" }
                    .content
            withClue(content) {
                content shouldContain "private data class ContentImpl(override val data: String) : LceState.Content"
                content shouldContain "public operator fun LceState.Content.Companion.invoke(data: String)"
            }
        }

        "宣言ゼロの storeSpec でも states() ファイルは生成される" {
            // 全 node 宣言ゼロは検証層で reject される (actions 推論不能) が、codegen 単体としては
            // 空の states() を生成する (防御的な仕様確認)
            val emptySpec =
                StoreSpec(
                    root = RootNode(type = TypeRef("example", "EmptyState"), companionName = "Companion", children = emptyList()),
                    actionsType = TypeRef("example", "EmptyAction"),
                    eventsType = null,
                )
            val generated = generateStoreSpecFiles(emptySpec)
            generated.map { it.fileName } shouldContainExactly listOf("EmptyState.storeSpec.generated")
            generated.single().content shouldContain
                "public fun koma.core.StoreBuilder<EmptyState, EmptyAction, Nothing>.states("
        }

        "storeSpec ファイル名はネストした root でも衝突しない" {
            val nested =
                StoreSpec(
                    root = RootNode(type = TypeRef("example", "FooScreen.State"), companionName = "Companion", children = emptyList()),
                    actionsType = TypeRef("example", "FooAction"),
                    eventsType = null,
                )
            generateStoreSpecFiles(nested).single().fileName shouldBe "FooScreen.State.storeSpec.generated"
        }
    })
