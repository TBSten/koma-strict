---
paths:
  - koma-strict-ksp/src/test/**
  - koma-strict-ksp/snapshots/**
---

# KSP Compilation Tests (kctfork)

`koma-strict-ksp/src/test/kotlin/me/tbsten/koma/strict/ksp/` には
[kctfork](https://github.com/zacsweers/kotlin-compile-testing) (`dev.zacsweers.kctfork:core` / `:ksp`、
`useKsp2()`) を使った JVM 専用の end-to-end テストを置いている。
(cream の `.claude/rules/ksp-test.md` を koma-strict に移植したもの)

テストは [kotest](https://kotest.io) の `FreeSpec` スタイルで書く
(`internal class XxxSpec : FreeSpec({ "..." { ... } })`、ネストは `"group" - { "..." { ... } }`)。
無効化テストは `"...".config(enabled = false) { }`。assert は kotest matcher を使い、
**語順は `actual shouldBe expected`**。失敗時メッセージは `withClue(message) { ... }` で保持する。
**テスト名は日本語で intent を書き、連番 (01, p01 等) を入れない** — テスト名がそのまま
golden のパスになる。koma-strict-ksp は JVM のみなので `kotest-runner-junit5` +
`useJUnitPlatform()` で動く (KSP も io.kotest プラグインも不要)。

## レイアウト

```
koma-strict-ksp/src/test/kotlin/me/tbsten/koma/strict/ksp/
├── AllKotlinFilesTest.kt       # 全ファイル横断 Konsist（root 直下許可ファイル / 行数上限 300）
├── feature/
│   ├── ArchTest.kt             # feature 層レイヤリング Konsist
│   └── strictStore/
│       ├── ProcessStrictStoreSpec.kt       # generator 駆動 snapshot spec
│       └── scenario/StrictStoreScenarios.kt # 手書き SnapshotSource の scenario 群
├── core/ArchTest.kt            # core / util 層レイヤリング Konsist
└── testing/                    # テスト基盤 (feature 非依存。再発明せず必ず再利用する)
    ├── compile/                # KomaStrictCompilation (compileWithKomaStrict + useKsp2) /
    │                           # Result ラッパー / RunCompileSnapshotTest / options レンダリング
    ├── snapshot/SnapshotAssertion.kt  # golden 比較 (assertMatchesSnapshot / facet)
    ├── generator/              # Generator + cartesian / union / map / withRepresentativeValues
    ├── scenario/SnapshotScenario.kt
    ├── smoke/                  # 基盤が動くことの最小確認 3 spec
    └── konsist/KonsistSupport.kt      # Konsist scope / レイヤ判定 (3 つの ArchTest が共有)
```

golden は **モジュール直下 `koma-strict-ksp/snapshots/`** に置く (`koma.strict.snapshot.dir`
system property で絶対パスが渡る。IDE 実行の workingDir 差異対策)。

## Snapshot file format

golden は `*.md`。captured 値はすべて **facet** として宣言し、宣言順に `## <facet 名>` セクション +
fenced code block になる。infix `facetOf` は default `lang = "kt"`、別言語は `facet(name, content, lang)`。
fence は各セクション独立に「内部の最長 backtick run + 1」(最小 3) で拡張される。比較はファイル全体
(見出し + fence 込み) なので**フェンスや golden 本文を手で書き換えると壊れる — 手編集禁止**。

### ファイル階層

- `TestScope.assertMatchesSnapshot { ... }` (推奨) — 実行中のテストから
  `<Spec 名>/<ネストしたテスト名>/<テスト名>.md` を自動導出する。セグメントを直接連結するので
  テスト名に `.` が含まれても安全 (cream の「先頭 `.` を `/` に置換」方式からの意図的改善)。
- 明示名版 `assertMatchesSnapshot("Spec.case") { ... }` — 最初の `.` だけが `/` になる
  (`"Spec.case.output"` → `Spec/case.output.md`。診断テスト用の variant 接尾辞はそのまま残る)。

### runCompileSnapshotTest の固定 facet

`testing/compile/RunCompileSnapshotTest.kt` がコンパイル系 snapshot の入口。facet セットは固定:
`Input:<file>` / `KSP options` / `Output:ExitCode` / `Output:Console` / `Output:Generated sources`。
追加観測は `additionalFacets` で足す。**同一テスト内で別途 `assertMatchesSnapshot` を呼ばない**
(double-snapshot 禁止)。input source は `SnapshotSource` として 1 箇所に持ち、コンパイルと
`Input` facet の両方に同じ値を渡す (SSoT)。

### normalizedCompilerOutput()

`java.io.tmpdir` → `<TMPDIR>`、`Kotlin-CompilationNNN` → `Kotlin-Compilation<N>`、連続 stack frame →
`<stack trace omitted>` に正規化してから golden に入る。診断 snapshot で固定化するのは
「error message 本文」だけ。特定 frame の存在を見たいなら `result.messages shouldContain` を併用。

## 運用

### スナップショット更新

```bash
./gradlew :koma-strict-ksp:test -Dkoma.strict.snapshot.update=true
```

- 欠損 golden はフラグなしでは fail する (auto-create しない) — 仕様。新規テストは update で初回生成
- 更新後は**必ずフラグなしで再実行して green を確認**し、`git diff koma-strict-ksp/snapshots/` を
  目視レビューする。golden の diff がレビュー対象そのもの
- facet 内容に絶対パス・タイムスタンプ・実行順依存の値を入れない (決定性)

### build.gradle.kts の Test 設定 4 点 (壊さない)

`koma-strict-ksp/build.gradle.kts` の `tasks.named<Test>("test")`。どれも理由がある:

1. `useJUnitPlatform()` — 無いとテストが 0 件実行のまま green になる
2. `maxHeapSize = "2g"` + `forkEvery = 25` — kctfork の classloader 蓄積による OOM 対策
3. `providers.systemProperty(...)` による **`-D` の明示転送** — `-D` はテスト worker JVM に
   自動伝播しない。configuration-cache 有効のため providers API 経由が必須
4. `inputs.dir(snapshots/)` — 無いと golden 編集時に up-to-date 判定でテストがスキップされる

### 基盤を変えたら smoke 検証

`testing/` や上記 Test 設定を変更したら「落ちるべきテストが期待した理由で落ちるか」を
一時改変 → red 確認 → 復元 → green の往復で検証する (手順: `.local/design/20260710-test-infra-smoke-plan.md`)。

### 新しいテストを足すとき

- feature のテストは `feature/<name>/` に spec + `scenario/` を置く (`koma-strict-snapshot-test`
  skill 参照)。feature 横断の診断シナリオは cream の `MultipleDiagnosticsTest` 形式 (未作成、TODO)
- KSP 型に依存しない純ロジックは `:koma-strict-ksp:shared` の jvmTest に置く (コンパイル不要)
- 共有ヘルパーが必要になったら `testing/` 配下に追加し、本ドキュメントのレイアウトを更新する
- レイヤリングの正本は `.claude/rules/ksp-architecture.md` (Konsist 3 spec が自動強制)
