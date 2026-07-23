# koma-strict

[![Maven Central](https://img.shields.io/maven-central/v/me.tbsten.koma.strict/koma-strict-runtime.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/me.tbsten.koma.strict/koma-strict-runtime)
[![CI](https://github.com/TBSten/koma-strict/actions/workflows/gradle.yml/badge.svg)](https://github.com/TBSten/koma-strict/actions/workflows/gradle.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**[koma](https://github.com/koma-kt/koma)(KMP の MVI 状態管理ライブラリ)向けの KSP プラグイン。**

State 定義から Store を typesafe に組み上げる DSL を自動生成する実験プロジェクト。 

---

## 「strict」の思想

koma の素の DSL には強制が一切ない ── state の網羅は要求されず、遷移先も emit する event も無制限。
koma-strict はこの規約を「レビューで守る」のではなく **コンパイルエラーで守る**:

> **① 宣言していない遷移・イベントは *書けない*。**
> **② 宣言した State / Action のハンドリングは *書き忘れられない* 仕組みを提供する。**

この 2 つを、実行時チェックではなく **すべてコンパイルエラー**として実現する。

## 仕組み

```
annotation 付き sealed State + Action / Event
  → KSP 解析 (koma-strict-ksp)
  → 生成: StoreBuilder への states()/actions() などの typesafe builder の生成
  → 利用者は koma 標準の Store {} 内で states() を呼ぶ(全 handler = 必須 named param)
  → 出来上がるのは素の koma Store<S, A, E>(入口から本物・エコシステムがそのまま使える)
```

思想は 4 本:**状態遷移とその実装を分離**/ **遷移は State 定義の近くに置く**(annotation のつけ外し =
扱える遷移の増減)/ **強い制約とシンプルな定義**(複雑さは生成コードが背負い、利用者の定義は素の
sealed interface + 少数の annotation)/ **エンジンを密閉しない**(入口は koma 標準の `Store {}` そのもの)。

## クイックスタート(LCE の例)

### 1. 宣言する(利用者が書く)

```kotlin
@StoreSpec(initial = [LceState.Loading::class])          // actions / events は宣言から推論
sealed interface LceState : State {
    companion object                                     // 生成拡張の生やし先

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
```

### 2. 使う(生成された DSL)

```kotlin
val store = createLceStore(                              // 生成 factory(型引数を書かない糖衣入口)
    initialState = LceState.Loading(),                   // 宣言済み initial 候補に型で絞り込まれる
    loading = LceState.Loading.actions(
        enter = {
            runCatching { fetchData() }.fold(
                onSuccess = { nextState.toContent(data = it) },  // 宣言済み遷移だけが生える
                onFailure = {
                    emitLoadFailed(it.message)                   // 宣言済み event だけが emit できる
                    nextState.toError(message = it.message)
                },
            )
        },
    ),
    content = LceState.Content.actions(reload = { nextState.toLoading() }),
    error = LceState.Error.actions(retry = { nextState.toLoading() }),
)
```

- `content` を渡し忘れる → **コンパイルエラー**(必須引数)
- 宣言していない遷移(`nextState.toXxx`)や event(`emitXxx`)は**そもそも生えない**ので書けない
- 出来上がる `store` は本物の `Store<LceState, LceAction, LceEvent>` ── koma-compose / koma-test 等がそのまま使える
- 生成 factory は 2 つ ── `createLceStore` の `initialState` は宣言済み `@StoreSpec(initial = ...)` 候補
  (`LceState.Loading`)に型で絞り込まれる。永続化 state からの復元やテストでの途中 state 起動には、
  任意の `LceState` を受け付ける `restoreLceStore` を使う

### 3. その他の例

このフレームワークの入出力は snapshot test されています。
snapshot は ./koma-strict-ksp/snapshots に格納されており、 特に [koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md のユースケースとオプションの全組み合わせ/option=Default](./koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md のユースケースとオプションの全組み合わせ/option=Default) は実際のユースケースを想定した入出力がまとまっているため、これを参照して以下のような具体的なユースケース別の生成例を確認できます。

- [基本的な LCE (Load-Content-Error) パターン](koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md%20のユースケースとオプションの全組み合わせ/option=Default/基本%20LCE.md)
- [中間 sealed(`Stable`)/ 条件付き遷移(`Stay`)/ prop 持ち越しを伴う LCE(pull-to-refresh + additional load)](koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md%20のユースケースとオプションの全組み合わせ/option=Default/LCE%20+%20pull-to-refresh%20+%20additional%20load.md)
- [root 共有アクション / 宣言ゼロ state / data object 宣言 / `E = Nothing`(タブ切替)](koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md%20のユースケースとオプションの全組み合わせ/option=Default/タブ切替.md)
- [stay + emit(バリデーション)/ 自己遷移との違い / prop の必須・デフォルト混在(フォームウィザード)](koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md%20のユースケースとオプションの全組み合わせ/option=Default/フォームウィザード.md)
- [`@OnRecover`(root 共有)/ `@OnExit` / 例外→宣言済み遷移の強制(認証 + セッション切れ)](koma-strict-ksp/snapshots/StoreSpecUseCasesTest/samples.md%20のユースケースとオプションの全組み合わせ/option=Default/認証%20+%20セッション切れ.md)

## 宣言 API(annotation)

package はすべて `me.tbsten.koma.strict` 直下。

| annotation | 付与先 | 役割 |
|---|---|---|
| `@StoreSpec(actions, events, initial)` | sealed root | store 仕様の起点。`actions` / `events` は宣言から推論(省略可) |
| `@OnEnter(nextState, emit)` | state | enter handler の宣言 |
| `@OnExit(emit)` | state / 中間 / root | exit handler の宣言(**遷移不可** ── emit のみ) |
| `@OnAction<A>(nextState, emit)` | leaf / 中間 / root | (state, action) handler。中間・root に付けると scope 共有アクション |
| `@OnRecover<E : Exception>(nextState, emit)` | state / 中間 / root | 例外 E 捕捉時の handler(`@OnAction` と相似形。Scope に `error: E`) |
| `@DefaultName(name)` | root / 中間 | 共有ブロックの引数名を変更(デフォルト `"default"`) |
| `Stay` | `nextState` の要素 | 「現状維持も可」の宣言(`koma.core.State` を実装する sentinel ── `nextState` の型境界を満たすためだけの存在で、実 state にはならない) |

**アクション能力ルール**: `nextState` リストがそのハンドラの全能力を決める ──
`[]`(省略)= `stayState()` のみ / `[X::class]` = `nextState.toX()` のみ / `[Stay::class, X::class]` = 両方(条件分岐)。
遷移先は具象 leaf のみ(`nextState: Array<KClass<out State>>` のため State 非実装型は型エラー、中間 sealed 型は KSP エラー)。


## IDE Plugin

<video src="./doc/sample/ide-plugin.mp4" />

## 網羅性はどう強制されるか(4+1 層)

Kotlin でコンパイル時に「全部書かせる」道具は **必須引数** と **abstract メンバー** の 2 つだけ。そこで:

| 層 | 強制の仕掛け |
|---|---|
| state 網羅 | root `states()` の必須 named param(`states(` を書いた瞬間に全 state 分が要求される) |
| アクション網羅 | `<State>.actions(...)` の必須 named param |
| 遷移ホワイトリスト | handler scope に宣言済みの `toXxx` **だけ**が生える |
| event ホワイトリスト | 宣言済み event ごとに `emit{Event}(...)` を生成(未宣言は関数自体が無い) |
| +1: 反応の強制 | handler の戻り値型 = per-handler Reaction。`toXxx()` / `stayState()` でしか作れない |

## 書き方は 1 つではない

同じ store を、好みに合わせて複数の形で書ける(混在も可):

- **生成 factory** ── `createLceStore(initialState, loading = ..., ...)`(型引数不要の糖衣入口。
  `initialState` は宣言済み initial 候補に絞り込まれる)/ 任意の state から始める `restoreLceStore`
- **koma 直入口** ── `Store<S, A, E>(initialState) { states(...) }`(正。型引数は明示)
- **値渡し / scope lambda** ── `loading = X.actions(...)` でも `loading = { actions(...) }` でも(両対応)
- **plus 合成** ── 自宣言 + 子を持つ中間 sealed は `X.actions(...) + X.states(...)`(片方忘れは型エラー)
- **builder 形式** ── `actions { reload { ... } }` / `states { idle { ... } }`
  (※ この形式のみ、アクション網羅は構築時 fail-fast に弱まる ── opt-in のトレードオフ)
- **エスケープハッチ** ── `actions(...) { /* per-state 素の koma DSL */ }` や `Store {}` 末尾の `configuration`
  で、v1 スコープ外の koma 機能(`launch {}` / `transaction {}` 等)を差し込める

詳細と全ユースケースは [`doc/internal/samples.md`](./doc/internal/samples.md) を参照。

## モジュール構成

| モジュール | 中身 | publish |
|---|---|---|
| `koma-strict-runtime` | annotation + `Stay` + `@KomaStrictDsl` + 生成コード共通機構(`dsl/`)。`koma-core` に api 依存 | ✓ |
| `koma-strict-ksp` | KSP processor(発見 → 検証 → 生成) | ✓ |
| `koma-strict-ksp:shared` | KSP 非依存の StoreSpec model / 命名 / codegen | ✓ |
| `integrationTest` | 実物 koma-core に KSP を適用した E2E 検証 | ✗ |

KSP 非依存の `shared`(model・命名・codegen)を核に、KSP は frontend に徹する層構成
(将来の Analysis API / compiler plugin 移行を見据えた設計。[`.claude/rules/ksp-architecture.md`](./.claude/rules/ksp-architecture.md))。

## 状態遷移図の生成(設計のみ・未実装)

`@StoreSpec` 一式は遷移グラフの全データを静的に持つので、**図生成は KSP 解析の副産物**として実現できる。
Mermaid / PlantUML の「図 + 遷移表」ペアを docs として出力し、CI の drift check で宣言との乖離を防ぐ構想。
設計は [`doc/internal/generate-state-diagrams.md`](./doc/internal/generate-state-diagrams.md)。
