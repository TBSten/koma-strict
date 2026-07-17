package me.tbsten.koma.strict.ksp.codegen

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * states() の trailing escape block (per-state の素の koma DSL) の codegen 単体テスト。
 * 入力 model は [StoreSpecCodegenFixtures] を共有し、escape scope・param・leaf ブロックへの
 * 適用 (内側 -> root の順・中間 sealed member の共有展開 + cast) を検証する。
 * 全文の固定と overload 解決の実証は kctfork ベースの golden (ProcessStoreSpecSpec) が担う。
 */
internal class GenerateStatesConfigureTest :
    FreeSpec({
        "root states() の末尾はセンチネルに代わり escape param になり scope が同ファイルに生成される" {
            val storeSpecFile =
                generateStoreSpecFiles(lceCodegenSpec)
                    .first { it.fileName == "LceState.storeSpec.generated" }
                    .content
            withClue(storeSpecFile) {
                storeSpecFile shouldContain "    configure: LceStateStatesConfigureScope.() -> Unit = {},"
                storeSpecFile shouldNotContain "preventTrailingLambda"
                // escape scope は member = 子 state 名 (宣言を持つ子のみ)。重複呼び出しは即 fail-fast
                storeSpecFile shouldContain
                    "@KomaStrictDsl\n@koma.core.KomaStoreDsl\npublic class LceStateStatesConfigureScope internal constructor() {"
                storeSpecFile shouldContain
                    "    public fun loading(block: koma.core.StoreBuilder.StateHandlerConfig" +
                    "<LceState, LceAction, LceEvent, LceState.Loading>.() -> Unit) {"
                storeSpecFile shouldContain
                    "        if (this.loading != null) throwDuplicateBuilderEntry(\"LceState\", \"loading\")"
                // escape block は 1 回だけ評価され、各 leaf ブロック末尾 (leaf configure の後) に適用される
                storeSpecFile shouldContain "    val configure = LceStateStatesConfigureScope().apply(configure)"
                storeSpecFile shouldContain "        loading.configure(this)\n        configure.loading?.invoke(this)"
                // KDoc に実測した重ね定義の合成規則 (先勝ち) を明記する (仕様)
                storeSpecFile shouldContain "the **first registered** one runs and later ones are silently ignored"
            }
        }

        "中間 sealed の states() escape は bundle が運搬し leaf ブロックへ内側から順に適用される" {
            val byName = generateStoreSpecFiles(flowCodegenSpec).associate { it.fileName to it.content }
            val refresh = byName.getValue("FlowState.Refresh.generated")
            withClue(refresh) {
                // 中間 companion states() (children-only / combined 両方) の末尾も escape param
                refresh shouldContain "    configure: RefreshStatesConfigureScope.() -> Unit = {},"
                // default ブロックの actions() のセンチネルは維持される (states() だけが置き換え)
                refresh shouldContain "public fun FlowState.Refresh.Companion.actions(\n" +
                    "    cancel: suspend RefreshCancelScope.() -> RefreshCancelReaction,\n" +
                    "    preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,"
                // 宣言ゼロの子 (Running) には member が生えない
                refresh shouldContain "    public fun failed(block: koma.core.StoreBuilder.StateHandlerConfig" +
                    "<FlowState, FlowAction, Nothing, FlowState.Refresh.Failed>.() -> Unit) {"
                refresh shouldNotContain "fun running(block:"
            }
            val storeSpecFile = byName.getValue("FlowState.storeSpec.generated")
            withClue(storeSpecFile) {
                // 中間 sealed member の共有 escape は S2 を leaf へ狭める cast で適用される (suppress 付き)
                storeSpecFile shouldContain "@Suppress(\"NAME_SHADOWING\", \"UNCHECKED_CAST\")"
                // leaf Failed: 内側 (Refresh の escape leaf member) -> root (共有 member) の順
                storeSpecFile shouldContain
                    "        refresh.configure.failed?.invoke(this)\n" +
                    "        configure.refresh?.invoke(this as koma.core.StoreBuilder.StateHandlerConfig" +
                    "<FlowState, FlowAction, Nothing, FlowState.Refresh>)"
                // 宣言ゼロの leaf (Running) にも共有宣言でブロックがあれば root の共有 escape だけが適用される
                storeSpecFile shouldContain
                    "    state<FlowState.Refresh.Running> {\n" +
                    "        action<FlowAction.Cancel> {"
                storeSpecFile shouldNotContain "refresh.configure.running"
            }
        }

        "escape member が無い states() はセンチネルを維持し escape scope を生成しない" {
            // 宣言ゼロの spec: 子 param ゼロ -> escape member ゼロ (空 escape は無意味 +
            // v4 登録 builder との単一 lambda の overload 解決が曖昧になるため)
            val emptySpec =
                lceCodegenSpec.copy(
                    root = lceCodegenSpec.root.copy(children = emptyList()),
                    initial = emptyList(),
                )
            val storeSpecFile = generateStoreSpecFiles(emptySpec).single().content
            withClue(storeSpecFile) {
                storeSpecFile shouldContain "preventTrailingLambda"
                storeSpecFile shouldNotContain "StatesConfigureScope"
            }
        }

        "生成 top-level 型名の列挙は escape scope を含む (衝突診断の入力)" {
            val lceNames = generatedTopLevelTypeNames(lceCodegenSpec)
            withClue(lceNames) {
                lceNames shouldContain "LceStateStatesConfigureScope"
            }
            val flowNames = generatedTopLevelTypeNames(flowCodegenSpec)
            withClue(flowNames) {
                flowNames shouldContain "FlowStateStatesConfigureScope"
                flowNames shouldContain "RefreshStatesConfigureScope"
            }
        }
    })
