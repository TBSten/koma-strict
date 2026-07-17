# 状態遷移図の生成

- ステータス: 設計確定・実装未着手
- 前提: [generate-strict-store-factory-dsl.md](./generate-strict-store-factory-dsl.md) の宣言 API
  (`@StoreSpec` / `@OnEnter` / `@OnExit` / `@OnAction` / `@OnRecover` / `Stay` / initial)
- 背景・経緯・却下した描画案・実レンダリング検証の記録は [story.md](./story.md)、
  具体的なユースケース例は [samples.md](./samples.md)

## 思想

- `@StoreSpec` 一式は遷移グラフの全データ(states / (state, action) → nextState / Stay /
  emit / initial / 共有アクション)を静的に持つ。**図生成は KSP 解析の自然な副産物**であり追加解析は不要
- 図は docs として commit し、**CI の drift check** で宣言との乖離を防ぐ
  (「golden + 再生成コマンド」のsnapshot テスト文化と同型)
- **図は嘘をつきうる**(ラベル重なりで宣言済み遷移が図から不可視になりうる)→
  生成物は**「図 + 遷移表」のペアが既定**。表が正・図は可視化。silent truncation を許さない

## アーキテクチャ

解析は facade 生成で既にやっている KSP processor に一本化する
(standalone Analysis API / standalone KSP の埋め込みはしない):

```
koma-strict-diagram (仮)        … pure KMP モジュール(KSP/compiler 依存ゼロ)。
                                   IR データモデル + Mermaid/PlantUML レンダラー。
                                   将来の IDE plugin がそのまま依存する
koma-strict-ksp                 … @StoreSpec 解析(facade 生成と同一パス)→ IR 構築 →
                                   オプション有効時に IR JSON を副産物出力
koma-strict-gradle-plugin (仮)  … 解析ゼロ。IR JSON → render → docs/ へ sync +
                                   CI 用 drift check(生成物と committed 図の一致検査)
IDE plugin(将来)                … IDE 内は IntelliJ の PSI/resolve で IR を構築し
                                   renderer(koma-strict-diagram)を再利用。
                                   初期は build 生成済みの IR JSON を読むだけでも成立
```

- 「IDE に切り出せる」の実体 = **IR と renderer を pure モジュールに隔離**すること
- トレードオフ: 図の生成はビルド(KSP 実行)に紐づく。ライブ更新は IDE plugin の守備範囲として割り切る

## フォーマット

renderer 非依存の**遷移グラフ IR を核**にし、v1 から **Mermaid / PlantUML 両対応**。
KSP オプションで選択(`mermaid | plantuml | all`)。

- Mermaid: `` ```mermaid `` フェンス入りの **.md** で出力(GitHub は素の .mmd をレンダリングしない)
- PlantUML: 素の **.puml**(IDE の PlantUML Integration プラグインが直接描画)
- **ビルド内での画像化はやらない**: mermaid-cli は Node + headless Chromium (~300MB) で論外級。
  画像が欲しい場合の将来案は plantuml.jar + Smetana(純 JVM)の optional task。
  IDE 表示は既存プラグイン(IntelliJ Markdown プレビュー / PlantUML Integration)で足りる

## 描画規約

| 要素 | 描き方 |
|---|---|
| 中間 sealed 階層 | **composite state**(`state Stable { ... }`)。**root は箱にしない** |
| Stay | **自己ループ矢印 + ラベルに `(stay)`**。自己遷移(ラベル無印)とはラベルで区別 |
| 条件付き遷移 `[Stay, X]` | stay ループ + X への通常エッジの 2 本で誠実に描く |
| 共有アクション | **破線スタイルの擬似ノード「any state」**から遷移先へ 1 本。scope ごとに一般化: 中間 sealed scope の共有は composite 内に「any Stable」等を置く(root と相似形で再帰) |
| emit | エッジラベル `action / Event`(Mealy 記法。例: `onEnter / LoadFailed`) |
| initial | `@StoreSpec.initial` の**各要素へ** `[*] -->` を 1 本ずつ。未宣言なら描かない |
| 到達不能 state | 警告色スタイル(mermaid `classDef` / PlantUML skinparam)で強調 |
| leaf の表示名 | 短名(`Idle`)、内部 id は qualified(`state "Idle" as Stable_Idle`。名前衝突回避) |
| ラベル言語 | **英語をデフォルト**(日本語はレンダラーによって文字化けする) |

- **禁止形**: composite から自身の子への直接エッジ(`MyState --> LoggedOut` 等)は
  mermaid でレンダリングが破綻する(ラベル潰れ・エッジ消失)。
  **生成器はこの形を出力してはならない**
- stay と自己遷移の区別はラベル `(stay)` のみが担う → 擬似ノードの意味と合わせて
  **凡例をファイル末尾に付ける**
- **store ごとの direction オプションは v1 必須**。`direction LR` でラベル埋没が解消する。
  デフォルト方向は実装時に全ゴールデンのスナップショット比較で決める

## 未決事項

1. **ファイル粒度・命名・配置**(仮確定): 1 store = 1 ファイル、パッケージディレクトリ構造維持、
   `{StateName}Diagram.md` / `{StateName}.puml`
2. **IR JSON スキーマ**(仮確定): v1 は internal 扱い(互換性保証なし)。
   IDE plugin 着手時に公開契約化を判断
3. **オプション名・タスク名**: 原案は cream 時代の `cream.store.diagram` /
   `creamDiagramsSync` / `creamDiagramsCheck`。koma-strict 系
   (例: `koma.strict.diagram` / `komaStrictDiagramsSync` / `komaStrictDiagramsCheck`)に
   読み替えて実装時に確定
4. **`@OnRecover` / `@OnExit` の描画規約**:
   recover の遷移エッジのラベル規約(`on {Exception} / Event` のような Mealy 形?)・
   scope 共有 recover を「any state」擬似ノード相似形で描けるか・
   exit の emit(エッジを持たない event 発行)の表現。
   recover / exit 入りの実ケースを足して実レンダリング検証してから確定する
