---
paths:
  - koma-strict-ksp/src/main/kotlin/me/tbsten/koma/strict/ksp/**/*.kt
---

# koma-strict-ksp Architecture (feature / core / util)

`koma-strict-ksp/src/main/kotlin/me/tbsten/koma/strict/ksp/**` は **3 層 + composition root + context**
で構成する (cream の `.claude/rules/ksp-architecture.md` を koma-strict に移植したもの)。
公開 API (runtime 注釈 / shared options) は変えない `internal` 実装の規約。

## Layers

| Layer | 場所 | 責務 |
|---|---|---|
| root | `ksp/*.kt` | KSP エントリ。**`KomaStrictSymbolProcessor` / `KomaStrictSymbolProcessorProvider` / `ProcessContext` の 3 ファイルのみ**。生成ロジック・ヘルパ・例外を直下に置かない (例外階層・options・命名は `:koma-strict-ksp:shared` 側) |
| feature | `ksp/feature/<name>/` | 注釈ごとの入口「発見 → 検証 → core 呼び出し」。生成ロジックは持たない。`feature.<name>` の 1 階層のみ (直下・深いネスト禁止) |
| core | `ksp/core/<sub>/` | koma-strict 固有の生成ロジック (`common` / `strictStore`)。`core/` 直下に .kt を置かない |
| util | `ksp/util/` | 汎用ヘルパのみ。直下は Kotlin-only、KSP 依存ヘルパは `util/ksp/`。koma-strict 固有型を参照しない |

## Dependency direction (one-way)

```
KomaStrictSymbolProcessor (root)
   ├─▶ feature/<name> ─▶ core/<sub> ─▶ util
   └─▶ ProcessContext (leaf)
feature ─▶ ProcessContext   （唯一の上向き依存。ProcessContext は leaf なので循環しない）
```

- **feature 間依存禁止** (他の `feature.<name>` を import しない)
- **core は `ProcessContext` にも composition root にも依存しない** — 必要な capability だけを
  context parameters で受ける (`options` / `logger` 等)
- **util は core / feature / koma-strict 固有型 (`me.tbsten.koma.strict.*`) を参照しない**。
  `util` 直下は KSP API も禁止 (`util/ksp/` へ)
- 境界は Konsist で自動強制: `AllKotlinFilesTest` (root 許可ファイル / 行数上限 300、
  `FILE_LINE_LIMIT_OVERRIDES` で個別 500 まで) / `feature/ArchTest` (feature 間依存・entry-point 署名) /
  `core/ArchTest` (core / util の下半分)。共有ヘルパは `testing/konsist/KonsistSupport.kt`。
  **この表やルールを変えたら同テストも更新すること**

## ProcessContext & context parameters

- Kotlin 2.4 で context parameters は **stable** (`-Xcontext-parameters` フラグ不要。付けると
  redundant 警告)。cream (Kotlin 2.2) との差分
- `ProcessContext = { resolver, options, codeGenerator, logger }`。`logger` は必須 (`KSPLogger?` 禁止)
- **層別 context (必要な capability だけ宣言)**:
  - feature: `context(processContext: ProcessContext) internal fun processXxx(): List<KSAnnotated>`
    (この署名は `feature/ArchTest` が文字列検査で強制する)
  - core: `context(logger: KSPLogger, options: KomaStrictOptions)` 等、絞った context
    (`util/With.kt` の `with(a, b) { }` で feature 側から橋渡し)
- per-call の値 (source class 等) は通常の関数引数のまま

## Naming

- feature: ファイル `Process<Name>.kt`、関数 `processXxx` (top-level / lowerCamel)
- core 生成関数: `appendXxx` (`Appendable` 拡張、文字列 append ベース。**KotlinPoet 不使用** —
  生成物が複雑な型を組むようになったら再検討)
- 書き出し口は `core/common/CodeGeneratorExt.kt` の `createNewKotlinFile` に一元化 (SSoT)

## Cross-cutting rules

- **診断**: ユーザー誤用は `throw` せず `logger.error(message, ksNode)` で clean な
  COMPILATION_ERROR。直後に `return` / `return@forEach` して部分生成を防ぐ
  (`asClassDeclarationOrReport` の「null 返し + 報告済み」パターン)。内部想定外のみ例外
  (`KomaStrictException` 階層、shared 側)
- **options は lazy パース** (`KomaStrictSymbolProcessor`): constructor で crash させると KSP が
  INTERNAL_ERROR として報告してしまう。process() 内で clean にエラー報告する
- **生成はトランザクショナル**: 先に buffer へ書き、空ならファイルを作らない
  (`createNewKotlinFile`)。`Dependencies(aggregating = true, containingFile)` を維持
- **`getSymbolsWithAnnotation().partition { it.validate() }`** — OK 側だけ生成し、NG 側を
  deferred として return する
- **SSoT**: 命名ロジック (`StoreFactoryName`)・options・例外階層は shared に一元化
- **ファイル分量** 10〜300 行目安・最大 500 (超過は責務分割。Konsist が強制)

## Adding a new feature (annotation)

1. `koma-strict-runtime` に注釈を定義 (`@Retention(SOURCE)`、KDoc 付き)
2. `feature/<name>/Process<Name>.kt` に `context(processContext: ProcessContext) internal fun processXxx()` を追加
3. 生成は core を再利用。足りなければ core 側に追加 (feature に生成ロジックを置かない)
4. `KomaStrictSymbolProcessor.process()` の `buildList` に `addAll(processXxx())` を追加
5. snapshot / diagnostic test を追加 (`ksp-test.md` と `koma-strict-snapshot-test` skill 準拠)

> 現状の feature は placeholder の `strictStore` のみ
> (doc/internal/generate-strict-store-factory-dsl.md の DSL 実装時に置き換える)。
