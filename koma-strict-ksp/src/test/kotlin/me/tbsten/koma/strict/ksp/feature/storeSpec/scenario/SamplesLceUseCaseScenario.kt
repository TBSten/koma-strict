package me.tbsten.koma.strict.ksp.feature.storeSpec.scenario

import me.tbsten.koma.strict.ksp.testing.compile.SnapshotSource
import me.tbsten.koma.strict.ksp.testing.scenario.SnapshotScenario
import org.intellij.lang.annotations.Language

// doc/internal/samples.md ケース「基本 LCE」の忠実写経 (StoreSpecUseCasesTest 用)。
//
// samples.md からの調整点:
// - package 宣言 (samples.lce) と import を追加 (samples.md は誌面上省略の前提のため)
// - 利用側が呼ぶ fetchData() / log() をスタブ (LceStubs.kt) として追加

@Language("kotlin")
private val samplesLceDeclaration =
    """
    package samples.lce

    import koma.core.Action
    import koma.core.Event
    import koma.core.State
    import me.tbsten.koma.strict.OnAction
    import me.tbsten.koma.strict.OnEnter
    import me.tbsten.koma.strict.StoreSpec

    @StoreSpec(initial = [LceState.Loading::class])   // actions / events は宣言から推論
    sealed interface LceState : State {
        companion object

        @OnEnter(nextState = [Content::class, Error::class], emit = [LceEvent.LoadFailed::class])
        interface Loading : LceState { companion object }

        @OnAction<LceAction.Reload>(nextState = [Loading::class])
        interface Content : LceState { val data: String; companion object }

        @OnAction<LceAction.Retry>(nextState = [Loading::class])
        interface Error : LceState { val message: String?; companion object }
    }

    sealed interface LceAction : Action {
        data object Reload : LceAction
        data object Retry : LceAction
    }

    sealed interface LceEvent : Event {
        data class LoadFailed(val message: String?) : LceEvent
    }
    """.trimIndent()

@Language("kotlin")
private val samplesLceUsage =
    """
    package samples.lce

    import koma.core.Store

    // 主: 生成 per-store factory 経由(型引数を書かない糖衣入口。命名 = root 名の末尾 State を strip +
    // create/restore + Store。initialState を宣言済み initial 候補に絞り込む createLceStore を使う)
    val store = createLceStore(
        initialState = LceState.Loading(),
        loading = LceState.Loading.actions(
            enter = {
                runCatching { fetchData() }.fold(
                    onSuccess = { nextState.toContent(data = it) },
                    onFailure = {
                        emitLoadFailed(it.message)
                        nextState.toError(message = it.message)
                    },
                )
            },
        ),
        content = LceState.Content.actions(reload = { nextState.toLoading() }) {
            // actions() の末尾 trailing lambda = configure(per-state エスケープハッチ)。
            // 生成される state<LceState.Content> {} ブロックの末尾に素の koma DSL として差し込まれる
            exit { log("content left") }
        },
        error = LceState.Error.actions(retry = { nextState.toLoading() }),
    ) {
        // 末尾 configuration = store 全体のエスケープハッチ(生成 handler 登録の後に素の koma DSL を追記)
        state<LceState.Error> { exit { log("error left") } }
    }

    // koma 標準の Store {} + states() 拡張(正)も従来どおり使える。param は両対応 —
    // 旧 scope lambda 形式({ actions(...) })と値渡し(LceState.Content.actions(...))を混在できる
    fun buildLceStoreWithKomaEntry(): Store<LceState, LceAction, LceEvent> =
        Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {   // 型引数は常に明示
            states(
                loading = { actions(enter = { nextState.toContent(data = fetchData()) }) },   // scope lambda 形式
                content = LceState.Content.actions(reload = { nextState.toLoading() }),       // 値渡し
                error = { actions(retry = { nextState.toLoading() }) },
            )
        }

    // builder 形式(第 3 の書き方)。宣言済み handler ごとの member 関数で登録する。
    // この形式に限り網羅チェックは構築時 fail-fast(不足・重複登録は実行時エラー)。
    // enter 宣言を持つ Loading には actions {} overload 自体が生えない(named-param 形式のみ)
    fun buildLceStoreWithBuilderForm(): Store<LceState, LceAction, LceEvent> =
        Store<LceState, LceAction, LceEvent>(initialState = LceState.Loading()) {
            states(
                loading = LceState.Loading.actions(enter = { nextState.toContent(data = fetchData()) }),
                content = LceState.Content.actions {
                    reload { nextState.toLoading() }
                    configure { exit { log("content left") } }   // builder 内でも per-state escape hatch を書ける
                },
                error = { actions { retry { nextState.toLoading() } } },   // scope ミラー側の builder overload
            )
        }
    """.trimIndent()

@Language("kotlin")
private val samplesLceStubs =
    """
    package samples.lce

    // samples.md には現れないダミー実装 (利用側コードのコンパイル証明用)

    suspend fun fetchData(): String = "data"

    fun log(message: String) {}
    """.trimIndent()

/** samples.md「基本 LCE」: 宣言 + 利用側 + fetchData / log スタブ。 */
internal fun samplesLceUseCase(): SnapshotScenario =
    SnapshotScenario(
        SnapshotSource(fileName = "LceState.kt", code = samplesLceDeclaration),
        SnapshotSource(fileName = "LceStoreUsage.kt", code = samplesLceUsage),
        SnapshotSource(fileName = "LceStubs.kt", code = samplesLceStubs),
    )
