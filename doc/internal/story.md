# 設計の背景と歩み

現時点の最終設計は [generate-strict-store-factory-dsl.md](./generate-strict-store-factory-dsl.md) /
[generate-state-diagrams.md](./generate-state-diagrams.md) が正。
このファイルは「なぜそうなったか」— 背景・経緯・却下案 — の記録を引き受ける。
壁打ちの全記録(全決定・却下・スパイク結果の原本)は `.local/design/`(未コミット)にある。

## 動機

koma v4 の素の DSL には強制が一切ない — state 網羅は要求されず、遷移先も emit できる event も
無制限。この規約を「レビューで守る」のではなく「コンパイルエラーで守る」ために、
annotation を SSoT として網羅的・型安全な facade を KSP 生成するのが koma-strict。
(「strict」の約束 2 つは設計ドキュメント冒頭を参照)

## 歩み

| 日付 | 出来事 |
|---|---|
| 2026-07-10 | 1 セッションの壁打ち(→ grill → 図設計 → プロトタイプ → アイデア判定)で骨格を設計確定。動くプロトタイプ(手書きの生成物想定コード + jvmTest 8 本 green)を cream repo に作成し「生成物の仕様書」とした。package = `me.tbsten.koma.strict` 直下、repo 名 = koma-strict も決定 |
| 2026-07-10 | 状態遷移図の描画規約を Mermaid / PlantUML の実レンダリングで検証・確定(下記「実ケース検証」) |
| 2026-07-11 | `@OnExit` / `@OnRecover<E>` を v1 入り(当初は configure 逃しの予定だった)。recover handler の param 名は `recover{Exception}` 仮確定、`clearPendingActions()` passthrough 採用 |
| 2026-07-12 | facade 呼び出しを named param + scope lambda 形式に変更(下記「named argument 化の経緯」) |
| 2026-07-12 | 生成コードの可視性ポリシー確定(samples.md のセルフレビュー由来): 全て明示(explicitApi 対応)/ 支援型 public + internal constructor / Impl は private(factory なし state のみ internal)/ facade だけが読む中身は internal |
| 2026-07-12 | エントリポイントを per-store facade 関数(`feedStateStore`)から **koma 標準 `Store {}` + 生成 `states()` 拡張**に変更(下記「エントリポイントの経緯」) |
| 2026-07-15 | `:integrationTest` 新設(実物 koma-core / koma-test rc02 に KSP 実適用・behavior 28 本 green)。**スパイク (d) 通過**(exit / recover の実 API は設計仮定どおり・clearPendingActions 全 scope 実在 → passthrough 解禁)・**klib 前方互換 実測 OK**(2.4.0 → 2.3.20 klib)・**DslMarker leak を実測**(→ 生成 scope に koma の @KomaStoreDsl を併記へ)。rc03 が Central 公開済みであることも判明 |
| 2026-07-15 | integrationTest での実利用を受け **Handlers 値渡し**(`loading = LceState.Loading.actions(...)`)を導入。同時に `actions()` 末尾 lambda の **per-state エスケープハッチ**(state<X>{} ブロックへの素の koma DSL 差し込み)を追加 |
| 2026-07-16 | 07-15 の「値渡しへ一本化」はユーザー意図の取り違えと判明し、**scope lambda 形式との両対応**に訂正。実現は「Handlers が `(Scope) -> Self` を実装し `invoke = this`」トリック(extension function type は supertype 不可のため素の Function1 を実装・スパイク実測)。HandlersScope 系が復活し、`states()` のセンチネルが誤 trailing lambda 防止として本領を発揮する形に |
| 2026-07-16 | 自宣言 + 子を持つ中間 sealed に **`actions(...) + states(...)` 合成形**を追加(従来の states() 内 default param 形と併存)。合成型を親 param 型にすることで「共有 handler の足し忘れ / 子の束ね忘れ」の両方を型エラー化 |
| 2026-07-16 | **per-store factory 関数(`lceStore(...)`)の生成を追加**。07-12 に supersede した facade 関数の復活だが、当時と違い koma 直入口 + states() 拡張を正としたまま**追加の糖衣**として(`Store<S, A, E>` の型引数の手間を解消)。両対応文化の帰結 |
| 2026-07-17 | **builder 形式(第 3 の書き方)を追加** — `actions { reload { ... } }` / `states { idle { ... } }`。07-10 に「ラムダ内の必須呼び出しを強制できない」ため却下した形の復活だが、**builder 形式に限り網羅チェックを構築時 fail-fast に弱める**トレードオフを明記した opt-in として。named-param 形式(コンパイル時網羅)が正のまま。builder の enter/exit member は koma 素の enter/exit との同語衝突を理由に非生成(対案 onEnter/onExit は提示の上で不採用) |
| 2026-07-17 | 「named + trailing block 併用」の block の意味を 3 択(handler 別置き場 / per-state escape / 両方)で確認し、**per-state escape に確定**(一時案の overload B = union 登録 + fail-fast は取りやめ)。states() の named param は全必須のまま = コンパイル時網羅が無傷で残る最良の着地。中間 sealed member は subtree 全 leaf への共有 escape(共有アクション展開と同型)。センチネルは escape param に役目を譲る。重ね定義(生成 handler × escape 内素 DSL)の合成規則は koma 側挙動を実測して規約化する方針 → **実測で確定: rc02 は同一 trigger 先勝ち**(生成 handler は escape で上書き不可・escape は宣言外 trigger 用という契約に。残スパイク「重複宣言の koma 側挙動」も解消) |
| 2026-07-18 | 中間 escape member のネスト化(`stable { idle { } }` + 共有は `common {}`)を提案の上で**却下・現状維持**(実利用感) |
| 2026-07-18 | 生成コード量削減のため **runtime `me.tbsten.koma.strict.dsl` への共通機構抽象化**を決定。利用側 API 不変・ホワイトリスト面(toXxx / emitXxx / stayState の生成条件)は生成側に残し、配管を @InternalKomaStrictApi ガード付き runtime API へ。**runtime が koma-core に api 依存**(「koma 依存可」の行使)・jvmTarget 21 へ |
| 2026-07-23 | **per-store factory 関数(`lceStore(...)`)を `create{Root}Store` / `restore{Root}Store` の 2 つに分離**。`create` は `@StoreSpec(initial = ...)` 候補ごとに `initialState` をその型へ絞り込んだ overload(未宣言なら非生成)、`restore` は `initialState` が root 型のまま(常に生成・restore / テスト起動用)。Kotlin に union 型が無いため複数 initial 候補は overload 複数生成で表現(詳細は `.local/design/facade-named-arguments.md` 追記) |

## named argument 化の経緯(2026-07-12)

- 最初の壁打ちでは `loading = MyState.Loading.actions(...)` が「loading が 2 回出る」ため却下され、
  全引数の型が異なることを利用した positional 形式に落ち着いていた
- 再訪し、理想形 `loading = actions(enter = ...)`(期待型からの無修飾解決)を
  context-sensitive resolution (KEEP-379) で実現できるか Kotlin 2.4.0 実機でスパイク:

| 検証 | 結果 |
|---|---|
| フラグなし(2.4.0 デフォルト) | CSR 自体が無効(enum entry すら解決しない) |
| `-Xcontext-sensitive-resolution` + enum entry | ✅ 解決 |
| 同 + companion `val` | ✅ 解決 |
| 同 + companion `fun` 呼び出し(`actions(...)`) | ❌ Unresolved |
| 同 + companion `val` + `operator invoke`(callee position) | ❌ Unresolved |

- → CSR は value position の property / enum entry 専用で、関数呼び出しには効かず不成立。
  名前空間を scope lambda の receiver に移す案を採用した
- 将来 KEEP が関数呼び出しへ拡張されたら `loading = actions(...)` を再訪する価値あり
  (生成物側は Handlers companion にメンバーを足すだけで前方互換に対応できる)

## エントリポイントの経緯(2026-07-12)

per-store の facade 関数名(`feedStateStore`)は一意でわかりやすい一方、store ごとに
入口の語彙が変わる。2 案を Kotlin 2.4.0 実機スパイクで検証した:

**案 1: 生成 `store<S, A, E> { ... }` 関数**(スパイク成立・supersede)

| 検証 | 結果 |
|---|---|
| store spec ごとの `store` オーバーロード(型引数は phantom・upper bound で選別) | ✅ 型引数から正しいオーバーロードに解決 |
| 同名オーバーロードの JVM signature clash | ❌ 発生 → **`@JvmName`(バイナリ名 = 旧 facade 名)で解消** ✅ |
| 型引数なし + 同一スコープに 2 store | ❌ overload resolution ambiguity(明快なエラー) |
| event 宣言ゼロ store の `E : Nothing` bound | ✅ コンパイル可(型引数の数を常に 3 に揃えられる) |

**案 2(採用): koma 標準 `Store {}` + 生成 `states()` 拡張** —
そもそも store 関数を生成せず、`StoreBuilder<S, A, E>` への拡張として root の
`states(...)` だけを生成する。receiver の型引数が store を選ぶため phantom 型引数の
トリックが不要(generic receiver への拡張解決 / @JvmName での erasure clash 回避 /
@JvmName が KMP commonMain で使えること、をそれぞれ実測)。
initialState は koma の `Store` に直接渡す形になり **initial のデフォルト引数生成は廃止**、
configure パラメータも廃止(素の koma DSL を builder に併記すればよい)。
トレードオフ: strict の約束は「`states(` を書いた瞬間から」効く(states() を呼ばない
自由は残る = エンジンを密閉しない側に倒した)。
副産物として root の束ねが中間 sealed と同じ `states(...)` 語彙になり対称化。

## 却下・見送りの記録(store DSL)

| 案 | 理由 |
|---|---|
| CSR による無修飾 `actions(...)` | 上記実測で不成立 |
| top-level per-state 関数(`loading(enter = ...)`) | receiver 名前空間で構造的に消した「長い連結名・名前衝突」問題の再導入(`stableRefreshLoading(...)`) |
| top-level `actions` オーバーロード群(named arg 名で解決) | 同名アクションだけを持つ state が 2 つになった時点で ambiguity = 宣言の成長で呼び出しが壊れる(設計原則 3 違反) |
| builder DSL(`myStateStore { loading { ... } }`) | 「ラムダ内でこの関数を必ず呼べ」は言語上強制できず、網羅性(strict の約束 ②)が崩れる |
| 単一 handler 糖衣・flat mode | 宣言の成長で呼び出しの形が変わる(設計原則 3 の由来になった却下) |
| `@Emits`(scope 共通 emit) | v1 見送り(将来温度感 70%)。プロトタイプに実装+テスト済みのまま残置 |
| exit / recover の configure 逃し | 2026-07-11 に撤回し `@OnExit` / `@OnRecover<E>` として v1 入り(機械が小さい / OnAction の完全再利用で、見送る理由が薄かった) |

## 状態遷移図の実ケース検証(2026-07-10)

LCE / ページング / タブ / ウィザード / 認証 + 2 段入れ子 の 5 ケースで
**表現できないパターンが無いことを確認済み**。
唯一、ウィザードの stay ループが大回りに描かれる点だけを mermaid の限界として許容。
「図は嘘をつきうる」(ラベル重なりで宣言済み遷移が図から不可視になる)実例もここで確認され、
「図 + 遷移表」ペア既定の根拠になった。日本語ラベルの文字化け・
composite から自身の子への直接エッジの描画破綻(禁止形)・`direction LR` によるラベル埋没の解消も
この検証での実機確認に基づく。

## 却下した描画案(状態遷移図)

| 案 | 結果 |
|---|---|
| root を composite で包み境界から 1 本 | mermaid で描画破綻(禁止形)。却下 |
| 全 leaf 展開(`logout (shared)` × N) | ラベル数が 共有アクション × 遷移元 で増殖。却下 |
| join バー集約 | ゴースト箱アーティファクト + 並行 fork/join 記号の意味借用。却下 |
| choice ダイヤ集約 | 有力だったが追加記号の意味借用を嫌い擬似ノード案に敗退 |
| 箱内テキスト(internal transition 風) | mermaid の `State : text` は state 名を置換して破綻 = PlantUML とのパリティ崩壊。却下 |
| note で説明・エッジゼロ | 遷移をエッジで追えない。却下 |

## 資料の所在

- `.local/design/current.md` … 壁打ち全記録の索引(原本リスト付き)
- `.local/design/koma-integration-design.md` … store 設計の原本
- `.local/design/diagram-design.md` … 図生成設計の原本(実レンダリング検証の記録)
- `.local/design/hook-annotations.md` … `@OnExit` / `@OnRecover` の検討・決定記録
- `.local/design/facade-named-arguments.md` … named argument 化の検討・スパイク実測
- `.local/design/migration-analysis-api.md` … Analysis API 移行構想とチェックリスト
- 動くプロトタイプ(生成物の仕様書・jvmTest 8 本 green): cream repo の
  `test/src/commonMain/kotlin/me/tbsten/cream/test/prototype-ui-state-management/`
  (全コードは current.md §12 に転載。facade 部分は named param + scope lambda 化により stale・要再検証)
