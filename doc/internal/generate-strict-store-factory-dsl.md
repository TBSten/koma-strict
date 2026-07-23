# Strict Store Factory DSL の生成

- ステータス: 設計確定・**KSP 実装済み**(integrationTest で実物 koma-core rc02 に対し検証済み。未 publish)
- 対象 backend: [koma](https://github.com/koma-kt/koma) v4 系(koma-core 4.0.0-rc02 で E2E 検証済み)
- 背景・経緯・却下案の記録は [story.md](./story.md)、具体的なユースケース例は [samples.md](./samples.md)

## 思想

**「strict」の約束 — 宣言していない遷移・イベントは*書けない*。
宣言した State / Action のハンドリングは*書き忘れられない*。**
この 2 つをコンパイルエラーとして実現する。

そのための思想:

- **状態遷移とその実装を分離する** —
  遷移の構造は annotation 宣言(SSoT)が持ち、実装は facade の引数として書く
- **遷移は State 定義の近くに置く** —
  annotation のつけ外しが、そのまま扱える状態・遷移の増減になる
- **強い制約とシンプルな定義** —
  制約を支える複雑さ(Reaction / Scope 等)は生成コードが引き受け、
  利用者の定義は素の sealed interface + 少数の annotation に保つ。省略できるものは推論する
- **エンジンを密閉しない** —
  入口は koma 標準の `Store {}` そのもの。エコシステム
  (koma-compose / koma-test / koma-logging 等)がそのまま使え、
  v1 外の機能は同じ builder に素の koma DSL で併記できる

(実現の仕掛けは §アクション能力ルール・§網羅性の 4+1 層 を参照)

## 全体像

```
利用者の宣言 (annotation 付き sealed State + Action/Event)
  → KSP 解析 (koma-strict-ksp)
  → 生成: StoreBuilder への states() 拡張 + 支援型 (Impl / factory / Scope / Transitions / Reaction / Handlers)
  → 利用者が koma 標準の Store {} 内で states() を呼ぶ (全 handler = 必須 named param)
  → 出来上がるのは素の koma Store<S, A, E>(入口から本物)
```

モジュール構成(想定・詳細未確定): `koma-strict-runtime`(annotation + `Stay` マーカーのみ。
`koma-core` に api 依存 — backend 中立制約は撤廃済み。`Stay : koma.core.State` と
`nextState: Array<KClass<out State>>` の境界のため)/ `koma-strict-ksp`(processor)/
図生成系は [generate-state-diagrams.md](./generate-state-diagrams.md) 参照。

## 宣言 API (annotation)

package は全て **`me.tbsten.koma.strict` 直下**。

| annotation | 付与先 | 役割 |
|---|---|---|
| `@StoreSpec(actions, events, initial)` | sealed root | store 仕様の起点。`actions` / `events` は省略時に宣言から推論(明示も可・矛盾は KSP エラー)。`initial` は optional な Array(複数可) |
| `@OnEnter(nextState, emit)` | state | その state への enter handler を宣言 |
| `@OnExit(emit)` | state / 中間 sealed / root | その state からの exit handler を宣言。**nextState パラメータは無い**(koma の ExitScope が遷移不可のため)。能力 = emit のみ。handler param 名は `exit` |
| `@OnRecover<E : Exception>(nextState, emit)` | state / 中間 sealed / root | 例外 E 捕捉時の handler を宣言。repeatable。`@OnAction<A>` と相似形(Scope に `error: E`)。CancellationException / Error 系は捕捉対象外(koma の recover と同じ)。handler param 名は `recover{Exception}`(仮確定) |
| `@OnAction<A>(nextState, emit)` | leaf / 中間 sealed / root | (state, action) ペアの handler を宣言。repeatable。中間・root に付けると scope 共有アクション(= default ブロック)になる |
| `@DefaultName(name)` | root / 中間 sealed | default ブロックの引数名を変更(デフォルト `"default"`)。leaf 名との衝突回避用 |
| `Stay`(マーカー object) | `nextState` の要素 | 「現状維持も可」の宣言。空リストは `[Stay::class]` の糖衣。`koma.core.State` を実装する(`nextState: Array<KClass<out State>>` の境界を満たすための sentinel。実 state にはならず生成コードにも現れない) |

- `@OnAction<A>` の型引数はマーカー専用(generic annotation は Kotlin 2.2+ でコンパイル可を実機検証済み。
  型引数はバイトコードに残らず source/metadata のみ = KSP/FIR のソースレベル解析専用)
- `@Emits`(scope 共通 emit)は **v1 見送り**(将来温度感 70%)

### State 宣言の 2 形式

- **interface 宣言**(推奨): shape だけ宣言し、impl(internal data class)と
  factory(`operator fun Companion.invoke(...)`)を生成する。factory はコンストラクタと構文互換なので
  data class からの移行で利用側コードが変わらない。equality は impl の data class 由来
- **data class / data object 宣言**: 従来通り可(両対応)
- 手書きの `companion object` が生成拡張(`actions()` / `states()` / factory)の生やし先。
  **宣言を持つ node に必須**。companion の無い interface は factory 生成をスキップ
  (利用者が直接構築しない state の opt-out スイッチ。例: 遷移でのみ到達する `LoggedOut`)

### 宣言例

```kotlin
@StoreSpec(initial = [MyState.Loading::class])       // actions / events は宣言から推論
@OnAction<MyAction.Logout>(nextState = [MyState.LoggedOut::class]) // root 共有 = default ブロック
sealed interface MyState : State {                   // koma.core.State
    companion object                                 // 生成拡張の生やし先

    @OnEnter(nextState = [Stable.Idle::class, Error::class], emit = [MyEvent.LoadFailed::class])
    interface Loading : MyState {                    // interface 宣言 → impl + factory を生成
        val query: String
        companion object
    }

    sealed interface Stable : MyState {              // 中間 sealed。共有 prop は 1 回だけ宣言
        val query: String
        val data: String
        companion object

        @OnAction<MyAction.Refresh>(nextState = [Stay::class, Refresh.Loading::class]) // 条件付き遷移
        @OnAction<MyAction.UpdateQuery>(nextState = [Idle::class])                     // 自己遷移
        interface Idle : Stable { val isFresh: Boolean; companion object }

        @DefaultName("refreshCommon")                // この scope の default ブロック名を変更
        @OnAction<MyAction.CancelRefresh>(nextState = [Idle::class]) // scope 共有アクション
        sealed interface Refresh : Stable {
            companion object
            @OnEnter(nextState = [Idle::class, Error::class], emit = [MyEvent.RefreshFailed::class])
            interface Loading : Refresh { companion object }
            @OnAction<MyAction.Retry>(nextState = [Loading::class])
            interface Error : Refresh { val message: String?; companion object }
        }
    }

    @OnAction<MyAction.Retry>(nextState = [Loading::class])
    interface Error : MyState { val query: String; val message: String?; companion object }

    interface LoggedOut : MyState                    // 宣言ゼロ + companion なし → 引数も factory も生えない
}
// MyAction / MyEvent は data class/object の sealed 階層(interface 化は見送り済み)
```

### アクション能力ルール(`nextState` リストが全能力を決める)

| 宣言 | handler が書けること |
|---|---|
| `nextState = []`(または省略) | `stayState()` のみ(現状維持 + emit だけのアクション用) |
| `nextState = [X::class]` | `nextState.toX()` のみ。stay 不可 |
| `nextState = [Stay::class, X::class]` | `stayState()` か `nextState.toX()`(条件付き遷移) |

- 「あえて何もしない」も宣言して明示する(`nextState` 未宣言 + `{ stayState() }`)。
  **暗黙に無視される入力を作らない**
- `@OnEnter` / `@OnRecover` にも同じルールを適用。要素は**具象 leaf のみ**(中間 sealed 型は KSP エラー)
- `@OnExit` だけは例外: koma の ExitScope が遷移不可のため**遷移能力そのものが無い**
  (annotation に nextState パラメータが無く、handler は `suspend Scope.() -> Unit`)。
  emit ホワイトリストのみ適用
- stay の実装 = **koma の `nextState` を呼ばないだけ**(文書化された挙動。インスタンス生成なし・identity 保存)
- 自己遷移(自クラスを `nextState` に含める)は stay とは**別物**(状態を作り直す)

## 利用側 DSL — koma 標準 `Store {}` + 生成 `states()` 拡張 + per-store factory

```kotlin
// (a) 生成 per-store factory 経由(型引数を書かない糖衣入口。命名 = root 名の末尾 State を strip +
//     create/restore + Store。initialState を宣言済み initial 候補に絞り込む createMyStore を使う。
//     任意の MyState から始めたいなら同一 param 列の restoreMyStore を使う)
val store = createMyStore(
    initialState = MyState.Loading(query = ""),
    // 以下は states() と同一の handler param 列。
    // param 名 = state 名(decapitalize)、値 = companion 拡張 actions() / states() が作る Handlers
    default = MyState.actions(logout = { nextState.toLoggedOut() }),   // root 共有 = default(先頭)
    loading = MyState.Loading.actions(
        enter = {                            // enter/action は suspend コンテキスト
            suspend { fetchData(state.query) }.runCatchingFold( // ユーザー側 util(CancellationException を透過)
                onSuccess = { nextState.toStableIdle(data = it, isFresh = true) },
                onFailure = {
                    emitLoadFailed(it.message)              // 宣言済み event のみ emit できる
                    nextState.toError(message = it.message) // 宣言済み遷移のみ呼べる
                },
            )
        },
    ),
    stable = MyState.Stable.states(          // 自宣言の無い中間 sealed は states() 語彙(子も named param)
        idle = MyState.Stable.Idle.actions(
            refresh = { if (state.isFresh) stayState() else nextState.toRefreshLoading() },
            updateQuery = { nextState.toIdle(query = action.query, isFresh = false) },
        ),
        // 自宣言(@OnAction 等)+ 子を持つ中間 sealed は actions(...) + states(...) の plus 合成。
        // 親 param 型 = 合成型なので、共有 handler の足し忘れも子の束ね忘れも型エラーになる
        refresh = MyState.Stable.Refresh.actions(
            cancelRefresh = { nextState.toIdle(isFresh = false) },  // query/data は中間型から持ち越し
        ) + MyState.Stable.Refresh.states(
            loading = MyState.Stable.Refresh.Loading.actions(enter = { /* 再フェッチ */ }),
            error = MyState.Stable.Refresh.Error.actions(retry = { nextState.toLoading() }),
        ),
    ),
    error = MyState.Error.actions(retry = { nextState.toLoading() }) {
        // leaf actions() の末尾 trailing lambda = per-state configure(エスケープハッチ)。
        // 生成される state<MyState.Error> {} ブロックの末尾に素の koma DSL を差し込む
        launch { /* v1 スコープ外の機能はここに書ける */ }
    },
) {
    // 末尾 configuration = store 全体のエスケープハッチ(生成 handler 登録の後に素の koma DSL を追記)
    simpleLogging()
}

// (b) koma 標準の Store {} + states() 拡張(正・従来どおり)。さらに各 param は
//     値渡しに代えて旧 scope lambda 形式でも書ける(両対応・混在も可)
val store = Store<MyState, MyAction, MyEvent>(initialState = MyState.Loading(query = "")) {
    // ↑ 入口は koma 標準の Store ファクトリ(生成物ではない)。型引数は常に明示する
    states(
        default = { actions(logout = { nextState.toLoggedOut() }) },     // scope lambda 形式(ミラー actions())
        loading = MyState.Loading.actions(enter = { /* (a) と同じ */ }), // 値渡し(混在可)
        stable = {
            states(                                                      // ミラー states()
                idle = { actions(refresh = { /* ... */ }, updateQuery = { /* ... */ }) },
                refresh = {
                    actions(cancelRefresh = { /* ... */ }) + states(     // ミラー同士でも plus 合成が書ける
                        loading = { actions(enter = { /* ... */ }) },
                        error = { actions(retry = { /* ... */ }) },
                    )
                },
            )
        },
        error = MyState.Error.actions(retry = { nextState.toLoading() }),
    ) {
        // states() の trailing block = per-state escape(member = 宣言を持つ子 state 名)。
        // 同一 trigger は先勝ち(生成 handler が常に先)なので、宣言でカバーしない trigger 用
        loading { exit { /* Loading は exit 未宣言 → 素の koma DSL が書ける */ } }
        stable { /* 中間 sealed member = subtree 全 leaf へ展開される共有 escape */ }
    }
    simpleLogging()   // 素の koma DSL を同じ builder に併記できる(store 全体のエスケープハッチ)
}

// (c) builder 形式(第 3 の書き方)。actions { } / states { } の別 overload(単一 lambda)で、
//     宣言済み handler / 子 state ごとの member 関数で登録する。
//     この形式に限り、網羅チェックは構築時(store 生成時)の fail-fast に弱まる(§網羅性)
val store = Store<MyState, MyAction, MyEvent>(initialState = MyState.Loading(query = "")) {
    states(
        default = MyState.actions { logout { nextState.toLoggedOut() } },
        // enter / exit 宣言を持つ node には actions {} overload 自体が生えない → named-param 形式のみ
        loading = MyState.Loading.actions(enter = { /* (a) と同じ */ }),
        stable = MyState.Stable.states {
            idle { refresh { /* ... */ }; updateQuery { /* ... */ } }   // 子 state 名 member(nested builder)
            refresh(                                                    // 値渡しでも登録できる(2 overload)
                MyState.Stable.Refresh.actions(cancelRefresh = { /* ... */ }) + MyState.Stable.Refresh.states(
                    loading = MyState.Stable.Refresh.Loading.actions(enter = { /* ... */ }),
                    error = MyState.Stable.Refresh.Error.actions { retry { nextState.toLoading() } },
                ),
            )
        },
        error = MyState.Error.actions {
            retry { nextState.toLoading() }
            configure { launch { /* ... */ } }   // builder 内でも per-state escape hatch を書ける
        },
    )
}
```

- **入口は 2 つ**。① **koma 標準の `Store(initialState) { ... }` + 生成 `states(...)` 拡張(正)**:
  **receiver(`StoreBuilder<MyState, MyAction, MyEvent>`)の型引数がどの store の states() かを選ぶ**
  ため、`Store` の型引数は常に明示する(A / E は initialState から推論できず、S は leaf 型に
  推論されてしまう)。② **生成 per-store factory(追加の糖衣。同じ store を作る)**:
  関数名 = root 名の末尾 `State` を strip して `create` / `restore` + `Store` の**2 つ**
  (`MyState` → `createMyStore` / `restoreMyStore`、`LceState` → `createLceStore` /
  `restoreLceStore`。ネスト root は underPackageName 連結: `FooScreen.State` →
  `createFooScreenStore` / `restoreFooScreenStore`)。**`create{Root}Store` は
  `@StoreSpec(initial = ...)` の宣言候補ごとに 1 overload 生成され、`initialState` がその候補
  自身の型に絞り込まれる(コンパイル時強制)。`initial` が未宣言なら生成されない
  (絞り込む先が無いため)。`restore{Root}Store` は `initialState` が root 型のまま
  (任意の state から開始できる)で、`initial` の宣言有無に関わらず常に生成される** —
  永続化 state からの復元やテストでの途中 state 起動に使う(2026-07-23 決定)。シグネチャは
  どちらも `initialState` → `states()` と同一の handler param 列 →
  `context: CoroutineContext? = null`(koma `Store` へ passthrough)→
  末尾 `configuration: StoreBuilder<S, A, E>.() -> Unit = {}`
  (trailing lambda 位置 = store 全体のエスケープハッチ。生成 handler 登録の後に呼ばれる)。
  koma 直入口が正のまま併存するので、エコシステムの前提は変わらない
- **param 名 = state 名の decapitalize、param 型 = `{束}HandlersScope.() -> {束}Handlers`
  (receiver 付き関数型)で値渡し / scope lambda の両対応**。
  値渡し `loading = MyState.Loading.actions(...)` は、束クラス(Handlers / GroupHandlers /
  合成型 / DefaultHandlers)が「**自分を返す `(Scope) -> Self`**」(`invoke = this`)を
  実装しているためそのまま渡る(extension function type は supertype にできないため、実装は
  素の Function1・param 側だけ receiver 付き — 相互代入可・実測)。
  scope lambda `loading = { actions(...) }` は生成 HandlersScope のミラー `actions()` / `states()`
  (companion 拡張と同シグネチャ = configure・センチネル込み)を呼ぶ。混在も可。
  値の構築経路は companion 拡張とミラーのみ(constructor は internal)。
  (scope lambda 採用 → 値渡し一本化 → 両対応、という経緯・CSR 不成立の実測・
  エントリポイント案の比較は [story.md](./story.md))
- **中間 sealed の束ね**: 自宣言の無い中間は companion 拡張 `states(...)`(子のみ)が作る
  `GroupHandlers` が親側 param 型。**自分の共有宣言(@OnAction / @OnRecover / @OnExit)と
  子の両方を持つ中間は親側 param 型が合成型 `{Path}Handlers`** になり、
  `actions(...)`(→ 自宣言束 `{Path}{DefaultName}Handlers`)と `states(子のみ)`(→ `{Path}GroupHandlers`)の
  **`plus` 合成だけが合成型を作る** = どちらか単独では親 param に渡せない(共有 handler の
  足し忘れ・子の束ね忘れの両方が型エラー)。従来形 `states(refreshCommon = ..., 子...)`
  (default named param 込み・合成型を直接返す overload)も併存(両対応の原則)
- **builder 形式(第 3 の書き方・2026-07-17)**: `actions { ... }` / `states { ... }` の
  **別 overload(単一 lambda)**として typesafe builder も生成する(receiver = 生成
  `{Path}ActionsBuilder` / `{Path}GroupBuilder`。@KomaStrictDsl + @KomaStoreDsl 併記)。
  member 名 = named param 名と同一(action 名 / `recover{Exception}` / leaf は `configure` も。
  states builder では default 名 + 子 state 名で、値渡しと nested builder の 2 overload)。
  **enter / exit は builder member を生成しない**(確定 2026-07-17。configure 内で使える
  koma 素の enter / exit と同語で紛らわしいため)。さらに **enter / exit 宣言を持つ node には
  `actions {}` overload 自体を生成しない**(builder で網羅を満たせないため。named-param 形式のみ。
  対案「builder では `onEnter {}` / `onExit {}` に改名して全 node で生成」は提示の上で不採用 —
  変更点は実装側に局所化済みで将来再訪は容易)。
  **但し書き: この形式に限り、宣言済み handler の網羅チェックは構築時(store 生成時)の
  fail-fast に弱まる**(不足・重複登録は actionable なメッセージ付き `IllegalStateException`。
  §網羅性)。自宣言つき中間の `states {}` は**合成型を返す単一 overload のみ**
  (children-only builder は overload 解決が曖昧になるため意図的に非生成。
  plus は `actions {} + states(named...)` で可)。overload 解決は実測で確定:
  builder(単一 lambda)と named(必須 param + configure)は曖昧性なく解決・
  erasure clash なし(@JvmName 不要)。**挙動変化に注意**: 従来 positional 単一 lambda
  (`X.actions { ... }`)は named の handler param に束縛されたが、今後は builder overload に
  解決される(§未決)。戻り値は従来と同じ Handlers / GroupHandlers / 合成型 →
  値渡し・plus・factory・scope ミラーとそのまま合成できる
- 網羅性: `states(...)` / `actions(...)` の全 handler param が**必須 named param**。
  **strict の約束は `states(` を書いた瞬間から効く**(states() を呼ばずに素の koma で書く自由は
  残る = エンジンを密閉しない)
- **leaf `actions(...)` の末尾は optional な `configure`(default `{}`)= per-state エスケープハッチ**。
  receiver は koma の `StoreBuilder.StateHandlerConfig<S, A, E, S2>`(`state<S2> {}` ブロックの
  receiver と同一型)で、生成コードが各 `state<...> {}` ブロックの末尾で呼ぶ。trailing lambda として
  書ける。default ブロック/中間自身の `actions()` には無い(単一の koma ブロックに対応しないため。
  中間・root からの per-state escape は次項の `states()` trailing block)
- **`states()`(root 拡張・中間 companion・そのミラー)の末尾は optional な escape block param
  (`configure`・default `{}`)= per-state escape(確定 2026-07-17)**。receiver は生成
  `{Prefix}StatesConfigureScope`(@KomaStrictDsl + @KomaStoreDsl 併記)で、
  **member = 宣言を持つ子 state のみ**: leaf member の receiver = `StateHandlerConfig<S, A, E, そのleaf>`、
  **中間 sealed member = subtree 全 leaf へ展開する共有 escape**(共有アクション展開と同型)。
  宣言ゼロ leaf は `state<X>{}` ブロック自体が無いため member も無し(素の DSL は store-level
  configuration で可)。named の必須 param 群は不変 = **コンパイル時網羅は無傷**。trailing lambda
  として書け、同一 member の二重呼び出しは構築時 fail-fast(`IllegalStateException`)。
  escape は GroupHandlers / 合成型が `internal val configure` として運搬し、`plus` 合成でも
  引き継がれる。per-store factory に per-state escape param は無い(末尾 = store-level
  `configuration` のまま)。重ね定義の合成規則(先勝ち・適用順)は §主要セマンティクス
- **センチネル**: **escape member ゼロの `states()`**(とそのミラー)と default ブロック/中間自身の
  `actions()` は末尾に `PreventTrailingLambda`(optional・非関数型)を持ち、**誤った trailing lambda は
  コンパイルエラー**になる(param 名の木を呼び出し側に保つ。member ゼロの states() で維持するのは、
  空 escape の無意味さ + 子宣言ゼロ group での v4 登録 builder との曖昧性を構造的に回避するため)。
  **末尾が意図された escape の場所には置かない**: leaf `actions()` は trailing lambda が configure に、
  escape member を持つ `states()` は escape block に、per-store factory は `configuration` に束縛される
  (必須 named param の欠落は変わらずコンパイルエラー = 網羅性維持)
- 生成 Scope(handler Scope・HandlersScope とも)は @KomaStrictDsl(@DslMarker)+ koma の
  `@KomaStoreDsl` 併記。入れ子 lambda から外側 scope のメンバーや koma builder API を誤って呼べない
- 深い階層では qualified receiver(`MyState.Stable.Refresh.Loading.actions(...)`)を書くが、
  **param 名の木 = 宣言の木**と root の束ねも中間 sealed と同じ `states()` 語彙(対称)は維持
  (scope lambda 形式なら qualified receiver 自体が消える)
- 切り出し・再利用は Handlers 値をそのまま変数に置けば良い
  (`val loadingHandlers = MyState.Loading.actions(...)` → `loading = loadingHandlers`。
  値は Function1 実装によりそのまま scope lambda 型 param に渡る)
- **引数順序**: default(宣言があれば先頭)→ 子 state の**ソース宣言順**。
  named なら順序自由だが、生成順は「宣言を見た順に埋めれば良い」直感を保証する
  (factory は先頭に `initialState`、末尾に `context` / `configuration` が付くだけで
  handler param 列は同一)
- 同名 `states()` 同士の JVM signature clash を避けるため
  `@JvmName`(`myStateStates` 形・仮)を一律付与(commonMain で使えることは実測済み)
- `states()` の**呼び忘れ・二重呼び出しはコンパイル時に検出しない**
  (素の koma で書く自由と表裏。二重呼び出しで重複した同一 trigger は koma の先勝ちで
  最初の登録だけが動く — §主要セマンティクス の合成規則・実測済み)
- 生成拡張(`states` / `actions`)・factory は宣言と同 package に生える。別 package から使う場合は
  import が必要

## 網羅性の 4+1 層(コンパイル時強制の仕掛け)

Kotlin でコンパイル時に「全部書かせる」道具は必須引数と abstract メンバーの 2 つだけ
(「ラムダ内でこの関数を必ず呼べ」は言語上強制できない)。そこで:

| 何を | どう強制 |
|---|---|
| state 網羅 | root `states()`(StoreBuilder 拡張)の**必須 named param**。`states(` を書いた瞬間に全 state 分の handler が要求される |
| アクション網羅 | `Handlers` の構築手段は **companion 拡張 `actions(...)` のみ**(constructor は internal)で、全 handler が**必須 named param** |
| 遷移ホワイトリスト | handler scope の `nextState`(Transitions)には宣言した `toXxx` **だけ**が生える |
| event ホワイトリスト | 宣言済み event ごとに `emit{Event}(ctor 引数)` を生成。未宣言 event は関数自体が無い |
| 反応の強制 | handler の戻り値型 = per-handler Reaction 型。構築手段は `nextState.toXxx()` と(`Stay` 宣言時のみ生える)`stayState()` だけ |

koma の `event()` が文スタイルなので「emit は文(koma に委譲)・遷移/stay は戻り値(型で強制)」の
分担が自然に成立する。

自宣言つき中間 sealed の `plus` 合成も同じ道具立ての延長: 親 param 型 = 合成型は
`actions(...) + states(...)` でしか作れず、「共有 handler の足し忘れ」「子の束ね忘れ」の
どちらも型エラーになる(§利用側 DSL)。

**例外は builder 形式(第 3 の書き方)**: `actions { ... }` / `states { ... }` を選んだ場合に限り、
アクション網羅(と states builder の階層網羅)は**構築時(store 生成時)の fail-fast に弱まる**
(「ラムダ内でこの関数を必ず呼べ」は言語上強制できないため。不足・重複登録は runtime の
BuilderFailFast ヘルパが actionable なメッセージ付き `IllegalStateException` を投げる)。
named-param 形式のコンパイル時網羅が正のまま併存し、builder 形式は **opt-in の書き味と
引き換えのトレードオフ**として明記する(§利用側 DSL)。

## 生成物(`<qualified state>.generated.kt`、1 node = 1 file)

| 生成物 | 役割 |
|---|---|
| `Impl`(private data class/object) | interface 宣言 state の実体。遷移も利用者も公開 factory 経由で構築するため file 内に閉じる。**companion なし(factory なし)の state のみ internal**(他 file の遷移が Impl を直接構築) |
| `operator fun Companion.invoke(...)` | factory。コンストラクタと構文互換 |
| per-handler `Reaction`(sealed: Transition / Stay) | 宣言された反応しか構築できない戻り値型 |
| per-handler `Transitions`(`nextState.toXxx(...)`) | 宣言済み遷移だけが生える。**デフォルト値は state の同名 prop のみ**(cream property matching 再利用) |
| per-handler `Scope`(state / action・error / nextState / stayState / emitXxx / clearPendingActions) | ホワイトリストの面。`clearPendingActions()` は koma への passthrough(全フック Scope に生成・pending 意味論の KDoc 付き。**rc02 で全 scope に実在確認済み 2026-07-15 → 生成解禁**)。scope 型には @KomaStrictDsl に加え **koma の @KomaStoreDsl を併記**(builder API の leak 遮断・実測根拠あり) |
| per-node `Handlers` + `<State>.Companion.actions(...)` | 必須 named param = アクション網羅の強制。**全束クラスは「自分を返す `(Scope) -> Self`」(`invoke = this`)を実装** = 値渡し / scope lambda 両対応の仕掛け。leaf の `Handlers` は末尾 optional の `configure`(per-state escape hatch)も `internal val` として保持し、コンパイルダウンが `state<...> {}` ブロック末尾で呼ぶ |
| per-束 `{束}HandlersScope`(全束クラスに対で生成) | scope lambda 形式(`{ actions(...) }` / `{ states(...) }`)の receiver。companion 拡張と同シグネチャの `actions()` / `states()` **ミラー**(configure・センチネル込み)を持つ。builder overload(下記)もミラーに同居する。@KomaStrictDsl + koma の @KomaStoreDsl を併記 |
| `{Path}ActionsBuilder` + `actions {}` overload(companion 拡張・ミラー両方) | builder 形式(第 3 の書き方)の receiver。member = 宣言済み action / `recover{Exception}`(+ leaf は `configure`)。網羅チェックは構築時 fail-fast(不足・重複は `IllegalStateException`)。**enter / exit 宣言を持つ node には builder も overload も非生成**(named-param 形式のみ) |
| 中間 node の `{Path}GroupBuilder` + `states {}` overload | states builder の receiver。member = default 名 + 子 state 名(値渡し / nested builder の 2 overload。ActionsBuilder の無い子は値渡しのみ)。自宣言つき中間は**合成型を返す単一 overload のみ**(children-only builder は非生成) |
| (runtime)`BuilderFailFast.kt` の `throwDuplicateBuilderEntry` / `throwMissingBuilderEntries` | builder 形式の構築時 fail-fast の実体・**メッセージの SSoT**。生成 builder が owner(state 参照)と entry 名だけを渡して呼ぶ「生成コード専用 API」(PreventTrailingLambda と同じ文化)。runtime 配置なので単体テスト可能・重複生成なし |
| 中間 node の `GroupHandlers` + `<State>.Companion.states(...)`(子のみ) | 子 state が必須 named param = 階層網羅。自宣言の無い中間はこれがそのまま親側 param 型。末尾 optional の escape block(`configure`)は `internal val configure`(StatesConfigureScope 値)として運搬し、root `states()` が leaf ブロックへ適用する |
| 自宣言つき中間の `{Path}{DefaultName}Handlers` / 合成型 `{Path}Handlers` / `operator fun plus` | 中間自身の `actions(...)` が作る自宣言束と `states(子のみ)` の `GroupHandlers` を `plus` が合成型(親側 param 型)へ束ねる。片方忘れは型エラー。従来形 `states(default名 = ..., 子...)` overload は合成型を直接返す。escape block(`configure`)は `plus` でも GroupHandlers 側から合成型へ引き継がれる |
| `{Prefix}StatesConfigureScope` + `states()` の escape param(`configure`) | `states()` の trailing escape block(per-state escape)の receiver。member = **宣言を持つ子 state 名のみ**(leaf member = その `state<...> {}` ブロック末尾へ、中間 sealed member = subtree 全 leaf へ展開する共有 escape)。同一 member の二重呼び出しは runtime の `throwDuplicateBuilderEntry` で fail-fast。**member ゼロの states() には生成せずセンチネル維持**。KDoc に重ね定義の合成規則(先勝ち・escape の位置づけ)を英語で明記 |
| root `states()` 拡張(`MyState.storeSpec.generated.kt`) | `StoreBuilder<S, A, E>` への拡張(受け手の型引数が store を選ぶ)。state 網羅(必須 named param)+ koma `state<X>{}` へのコンパイルダウン(冒頭で scope lambda 型 param を `{束}HandlersScope().param()` で Handlers 値へ解決)。各 `state<X>{}` ブロック末尾で **leaf configure → 内側 states() escape → root escape** の順に適用(中間 sealed member は `StateHandlerConfig<..., 中間型>` への cast 適用・`@Suppress("UNCHECKED_CAST")`)。同名 clash は `@JvmName` で回避。**唯一の全体依存ファイル** |
| per-store factory 関数(同じく storeSpec ファイル内) | `create{root名 - State}Store` / `restore{root名 - State}Store` の 2 つ(いずれも `<states() と同一 param 列>, context = null, configuration = {})`)。koma `Store()` + `states()` を包む糖衣。`create` は `@StoreSpec(initial = ...)` 候補ごとに `initialState` を候補の型へ絞り込んだ 1 overload(`initial` 未宣言なら非生成)、`restore` は `initialState` が root 型のまま(常に生成)。関数名は生成名衝突検出の対象(§KSP 静的検証) |

ファイル分割は KSP incremental の依存単位に一致: node の宣言変更 → その node のファイルだけ再生成。
構造変更(state 増減)→ 親の `states()` ファイルと storeSpec ファイルが再生成。

**可視性ポリシー**(確定 2026-07-12): 利用モジュールが `explicitApi()` でも通るよう**全て明示**する。

- 支援型・facade は `public`(state 宣言の可視性を継承)+ **`internal constructor`** で構築経路を封鎖
- facade だけが読む中身(Reaction の遷移先・Handlers の handler プロパティ)は `internal` = 利用者には不透明
- `Impl` は `private`(上記の表を参照。factory なし state のみ internal)
- emit 宣言ゼロの Scope には eventSink 自体を生成しない

## 主要セマンティクス

- **共有アクション(default ブロック)**: root / 中間 sealed への `@OnAction` がその scope の
  default ブロックになり、生成時に**全 leaf の `state<X>{}` ブロックへ展開**する
  (koma の階層 dispatch には依存しない)。祖先・子孫への同一アクション重複宣言は KSP エラー
- **重ね定義の合成規則(規約・実測 2026-07-17)**: koma rc02 は同一 state ブロック内の同一 trigger を
  **先勝ち**で dispatch する(`firstOrNull` 実装。koma 自身の KDoc にも明記)。後続の登録は
  黙って無視される(エラーでも両方でも last-wins でもない)。koma-strict の登録順 =
  **生成 handler → leaf の `actions(configure = ...)` → 内側 `states()` の escape → root の escape**
  (ソース記述順)。帰結: **生成 handler は escape で上書きできない**(宣言済み挙動を escape が
  奪えない = strict 的に好都合)、**escape = 宣言でカバーしない trigger 用**という契約
  (生成 escape scope の英語 KDoc に明記。integrationTest の `LceOverlappingHandlersTest` /
  `FeedStatesEscapeTest` で固定)
- **initial**: ① 到達不能分析の起点(全要素) ② 遷移図の `[*] -->` エッジ。
  initialState は koma 標準の `Store` に直接渡すため**デフォルト引数の生成は無し**・
  **実行時の型強制もしない**(process restore・テストでの途中 state 起動を可能に保つ)
- **actions / events の推論**: `@OnAction` 型引数 / emit 宣言の共通 sealed supertype。
  アクション宣言ゼロは推論不能 → KSP エラーで明示を要求。emit ゼロなら `E = Nothing`
- **デフォルト値の源は state のみ**: `action` の prop からの自動取り込みは意図的にしない
  (`query = action.query` は手書き = データフローの境界を可視化)
- 未宣言アクションの実行時 dispatch は koma が reject する(意図された guard。diagnostics 対象外)

## KSP 静的検証

- `@OnAction` のアクションが actions 階層の subtype か / `emit` の event が events 階層の subtype か
- `nextState` の要素が同一 sealed 階層の**具象 leaf** か `Stay::class` か
  (State 非実装型はそもそも `nextState: Array<KClass<out State>>` の境界に合わずコンパイルエラー =
  KSP 診断が担当するのは「State だが階層外 / 中間 sealed」のケース)
- 同一 (state, action) ペアへの重複 `@OnAction` はエラー / 祖先・子孫の重複宣言はエラー
- `@OnRecover` の型引数が `Exception` の subtype か / 同一 state への同一例外型の重複はエラー
  (継承関係のある複数宣言の扱いはスパイク (d) 後に確定)
- 中間 sealed の companion は **subtree に宣言があれば必須**(KSP エラー。値渡し形式の
  構築経路 = companion 拡張 `states()` を欠かせないため(scope lambda 形式のミラーは
  HandlersScope 側に生える)。従来は自身に宣言を持つ node のみ必須だった)
- 到達不能 state は警告(`initial` 未宣言時は起点が無いため到達不能分析をスキップ)/
  **死にアクション**(どの state も handle しない)はデフォルト warning、
  KSP オプションで error 昇格可(オプション名は実装時決定)
- 型パラメータ付き state は v1 では明示的に拒否(KSP エラー)
- state 名の decapitalize が root `states()` の予約 param 名(`default`)と衝突しないか
  (衝突はエラー + rename 案内。`@DefaultName` で回避可能)
- 生成名の衝突(同一 package 内)は同一 KSP round 内で横断検出してエラー
  (別モジュール間は kotlinc に委譲)。対象は**生成 top-level 型の全量**
  (Impl / Reaction / Transitions / Scope / Handlers / HandlersScope / GroupHandlers /
  合成型とその Scope / **builder 型名(ActionsBuilder / GroupBuilder)**/
  **escape scope(StatesConfigureScope)**)+
  **生成 top-level 関数(per-store factory)**。
  型は別 @StoreSpec 階層の同名 leaf や path 連結の一致(`Stable.Idle` と `StableIdle`)で衝突する。
  factory は root 名の `State` strip で衝突する: 同一 package の `PlayerState` と `Player` は
  型は衝突しないがどちらも `playerStore` になるためエラー(kotlinc 的には overload として
  合法でも、入口の曖昧さを避けて拒否)
- (既知の v1 制約)public な state 階層 + internal な action / event 型の組み合わせは
  KSP では検出せず、生成コードの kotlinc エラーに委譲する

## v1 スコープ外(素の koma DSL を `Store {}` に併記して逃がす)

`launch {}` + `transaction {}`(非同期 collect からの遷移は Reaction モデルを素通りする、
一番難しい 20%)/ `stateSaver` / plugin 登録 / `@Emits`。

## 設計原則(今後の判断基準)

1. **識別子の短縮より backend 語彙との橋を優先**(`nextState.toXxx` は koma の `nextState{}` と概念対応)
2. **状態の継続は暗黙(copy 意味論)・入力の取り込みは明示**(デフォルト値の源は state のみ)
3. **宣言の成長で呼び出しの形が変わる sugar は避ける**(却下事例は [story.md](./story.md))

## koma v4 実測メモ(実装・テストに効く)

- koma-core 4.0.0-rc02 / rc03 とも Maven Central 公開済み(2026-07-15 実測。当面 rc02 を使用)/
  Kotlin 2.3.20 ビルド / **JVM target 21**(inline fun の利用側も jvmTarget 21 必須)。
  Kotlin 2.4.0 からの 2.3.20 klib 読み(iOS)は実測で問題なし
- **exit / recover の実 API は設計仮定どおり**(2026-07-15 integrationTest 実測):
  `exit(dispatcher = null, block: suspend ExitScope<S, E, S2>.() -> Unit)` /
  `recover<T : Exception>(dispatcher = null, block: suspend RecoverScope<S, E, S2, T>.() -> Unit)`。
  `clearPendingActions()` は全 handler scope に実在
- koma は `@KomaStoreDsl`(@DslMarker)を builder / 全 scope に付与。マーカーが異なると
  生成 handler lambda 内から koma builder API が leak する(実測)→ 生成 scope に併記して遮断
- 同値での自己遷移は StateFlow の equality conflation により観測不能(実測。
  「自己遷移 = 状態を作り直す」は同値ケースでは stay と識別できない)
- **同一 state ブロック内の同一 trigger handler は先勝ち**(2026-07-17 実測。`firstOrNull` 実装で
  koma の KDoc にも明記。後続登録は黙って無視)→ 「生成 handler → escape」の登録順と合わせて
  「escape は宣言でカバーしない trigger 用」の契約が成立(§主要セマンティクス の合成規則)
- **起動時に初期 state の enter が発火する**(`initializeIfNeeded` → `onStateEntered`)
- `event()` は suspend / event は `MutableSharedFlow(replay = 0)`(購読前の emit は消える)
- `store.state` × `Flow.first{}` はハングする(rc02 の実装問題。upstream issue 候補)
- pending アクション: デフォルト policy が破棄するのは「別クラスへの遷移」時のみ。
  stay と自己遷移では消えない(KDoc 確認済み)→ 生成する `stayState()` / `toXxx()` の KDoc に明記する
- テストは koma-test が正解: `startAndAwait()` / `dispatchAndAwait()` は同期遷移チェーンまで待つ。
  `StoreRecorder`(plugin)で event 購読レース回避 + 中間 state の通過検証。ポーリング・delay は不要

## 未決事項・残スパイク

1. ~~KSP が `@OnAction<A>` の型引数を解決できるか~~ → **解消**(kctfork 実測で完全解決。
   恒久 smoke テストあり。フォールバック不要)
2. enter 付き state での自己遷移 → enter が再発火するか(stay 設計の裏取り。再発火しない寄りの見込み。
   なお同値の自己遷移は conflation で観測不能と実測済み — 実測メモ参照)
3. ~~exit / recover の rc02 実物 API 確認(スパイク (d))~~ → **ほぼ解消**(2026-07-15
   integrationTest 実測: シグネチャ仮定どおり / RecoverScope の `error` 確認 / exit の emit 実挙動 green /
   DslMarker は要併記(実測メモ参照)/ clearPendingActions 全 scope 実在)。
   ~~同一 state の重複宣言(`states()` 二重呼び出し相当)の koma 側挙動~~ → **解消**
   (2026-07-17 実測: 同一 trigger は先勝ち・後続は黙って無視 — §主要セマンティクス の合成規則。
   `LceOverlappingHandlersTest` で固定)。**残り**: 継承関係のある複数 recover のディスパッチ順
4. 命名残り: `@StoreSpec` 名の最終確認(仮確定)/ severity オプション名 /
   遷移・emit 関数の qualified 命名詳細(同名 leaf/event の衝突解決)/
   recover handler の param 名 `recover{Exception}`(仮確定)/
   `states()` 拡張の `@JvmName` 名(仮: `{root}States`)/
   per-store factory 名 `{root - State}Store` と末尾 param 名 `configuration`(仮確定)/
   予約 param 名 `configure`(leaf `actions()` に加え **escape block 化で `states()` にも拡大**)/
   `preventTrailingLambda`(および factory の
   `initialState` / `context` / `configuration`)と同名アクション・state 名の
   衝突診断は未実装(生成コードの kotlinc エラーに委譲)。
   アクション名 `Configure` / `Build` は builder member(`configure` / `build()`)と、
   **state 名 `Configure` は `states()` の escape param・escape scope member と**
   衝突する同族の未診断エッジ(同じく kotlinc エラーに委譲)
5. **(注意・挙動変化)builder overload 追加により positional 単一 lambda の解決が変わった**:
   従来 `X.actions { ... }` は named 形式の handler param に束縛されたが、
   今後は builder overload に解決される(実測・意図された変化。既存コードが positional で
   単一 handler を渡していた場合は挙動が変わるため記録)
6. ~~利用側 DSL(koma 標準 `Store {}` + `states()` 拡張)のプロトタイプ再検証~~ →
   **解消**(両対応 param(値渡し / scope lambda)+ per-state configure +
   plus 合成 + per-store factory 形式で実装済み 2026-07-15/16。builder 形式(第 3 の書き方)・
   `states()` の trailing escape block も実装済み 2026-07-17。kctfork snapshot / :integrationTest E2E とも green。
   拡張解決・@JvmName(commonMain 含む)・E : Nothing はスパイク済み。story.md 参照)
7. **companion 必須ルールの見直し**: companion が必要な理由は factory(interface 宣言 state)と
   Handlers / GroupHandlers 構築用の companion 拡張 `actions()` / `states()`
   (値渡し形式の構築経路。中間 sealed は subtree に宣言があれば必須に拡張済み —
   §KSP 静的検証)。v1 で「宣言を持つ node に必須」を維持するか、
   宣言も factory も不要な state では省略可に緩和するか
8. **将来構想: Analysis API(IDE/CLI)/ K2 compiler plugin ベースへの移行**。実装時に
   「KSP 非依存の *StoreSpec model* を核に置き、検証・コード生成・図 IR 構築は model のみに依存、
   KSP は model を構築する frontend に徹する」layering になっているかをチェックする
   (KSP 型を import しない層を Konsist で強制。図生成の IR/renderer 分離と同型の原則)。
   宣言 API は SOURCE retention のソースレベル解析専用なので KSP / FIR / Analysis API
   いずれでも読める = 移行に中立
   (詳細な移行耐性評価とチェックリスト: `.local/design/migration-analysis-api.md`・未コミット)
