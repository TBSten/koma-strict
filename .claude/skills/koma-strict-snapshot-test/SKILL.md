---
name: koma-strict-snapshot-test
description: >-
  Build or extend a golden snapshot test for a koma-strict KSP feature/annotation by mirroring
  the established strictStore pattern. Use whenever the user wants to add, build, or "横展開"
  (roll out) a snapshot test for a koma-strict annotation — including extending scenario
  families, adding a `<Feat>Spec`, regenerating goldens, or auditing golden coverage for
  koma-strict's generated store-factory code. Trigger even if the user just names a feature and
  says "snapshot test を足して". This is for the koma-strict repo (me.tbsten.koma.strict KSP
  plugin) specifically.
---

# koma-strict snapshot test 横展開

koma-strict は State 定義の annotation から strict な store factory を生成する KSP plugin。
feature ごとに `<Feat>Spec` が入力プログラムをコンパイルし、生成物を golden 比較する。
現在の live reference は **`feature/storeSpec/`** (@StoreSpec 本実装):

- spec: `koma-strict-ksp/src/test/kotlin/me/tbsten/koma/strict/ksp/feature/storeSpec/ProcessStoreSpecSpec.kt`
- scenario: 同 `feature/storeSpec/scenario/StoreSpec*Scenarios.kt` (basic / hierarchy / hook / diagnostics)
- 入力が参照する koma API は `testing/fixtures/KomaApiStub.kt` のスタブが compile harness で常に同梱される

この skill は cream の `cream-snapshot-test` skill の koma-strict 版。feature 数が増えて
scenario family が育ったら、cream 版の family 体系 (11 families + feature-specific) を参考に拡張する:
<https://github.com/TBSten/cream/blob/main/.claude/skills/cream-snapshot-test/SKILL.md>
(references/families.md / feature-profiles.md も同ディレクトリ)。

## The two non-negotiables

1. **共有基盤 (`testing/`) を再利用する — 再発明しない。** compile harness / snapshot assertion /
   generator / options generator はすべて `testing/{compile,snapshot,generator,scenario}` にある。
   feature 側で書くのは `scenario/` ファイルと `<Feat>Spec` だけ。compile harness や
   options generator を書き始めたら手を止めて既存を探す。

2. **Output-preserving by construction.** golden はテストクラス名 + テスト名がそのままパスになる。
   生成後に update フラグ**なし**で再実行して必ず pass させる。reject / error ケースは
   `COMPILATION_ERROR` golden として捕捉するのが正しい (error-as-golden)。golden の手編集は禁止。

## Process

### 1. 対象 feature の processor を分析する

`ksp/feature/<name>/Process<Name>.kt` と runtime 注釈を読み、以下を抽出する:

- **注釈サイト**: どの宣言に注釈が付き、何が参照されるか
- **生成ファイル名** (`storeFactoryFileName` 等の命名ロジック、shared 側) と関数名の衝突軸
- **診断分岐**: `logger.error` で reject されるのはどんな入力か (error-as-golden の対象)
- **options の影響**: `KomaStrictOptions` のどのフィールドが出力を変えるか

### 2. scenario family を選ぶ

scenario は「変化の軸」ごとに 1 ファミリ。現状 (placeholder) は手書き `SnapshotSource` の
1 ファミリのみだが、最低限入れる軸:

- 基本形 (sealed interface / 最小構成)
- 入れ子・階層 (nested sealed、同 simpleName 衝突、default package)
- options との直積 (`validKomaStrictOptions()`)
- 診断ケース (意図的 reject → `COMPILATION_ERROR` golden)

**golden 爆発に注意**: 直積は `representativeValues()` で代表点に絞る (cream は feature あたり
136〜244 golden で運用)。意図的にカバーしない case は `<Feat>Spec` クラスの KDoc に
「意図的に snapshot にしない case と理由」として箇条書きで残す。

### 3. spec を書く (inline 形式)

`ProcessStrictStoreSpec` の骨格をそのまま使う。共有の `snapshotMatrix` ヘルパへの抽出はしない:

```kotlin
internal class <Feat>Spec :
    FreeSpec({
        "オプションと入力の全組み合わせ" - {
            cartesian(
                <feat>Scenarios(),
                Generator.validKomaStrictOptions(),
                label = { scenarioLabel, optionsLabel -> "option=$optionsLabel/$scenarioLabel" },
            ).representativeValues()
                .forEach { (testCaseName, value) ->
                    val (scenario, options) = value
                    testCaseName!! {
                        runCompileSnapshotTest(inputs = scenario.sources, options = options)
                    }
                }
        }
    })
```

テスト名・scenario 名・golden 名は日本語 intent、連番禁止。

### 4. golden 生成 → output-preserving 検証

ログは `.local/tmp/<time>-<cmd>.log` に保存 (プロジェクト規約):

```bash
# 生成
./gradlew :koma-strict-ksp:test --tests '*.<Feat>Spec' -Dkoma.strict.snapshot.update=true
# 検証 (フラグなし → 必ず pass)
./gradlew :koma-strict-ksp:test --tests '*.<Feat>Spec'
# アーキテクチャガード込みのフル
./gradlew :koma-strict-ksp:test
```

### 5. golden の spot-check

生成ケース・reject ケース・options 影響ケースを最低 1 つずつ開いて確認する:

- `Output:Generated sources` が意図どおりか。**生成の quirk を見つけたら generator を直さず
  golden に固定して issue 化する** (capture-and-report。修正は別タスク)
- `Output:Console` に想定外の警告が混ざっていないか
- **`COMPILATION_ERROR` の出所を確認**: koma-strict の意図的 reject なら正しい golden。
  kotlinc の言語エラー (入力自体が不正 Kotlin) なら scenario のバグなので入力を直す

### 6. チェックリスト (完了報告に PASS/FAIL + 根拠を付ける)

- [ ] processor を分析した (注釈サイト / 命名・衝突軸 / 診断分岐 / options 影響を報告に明記)
- [ ] `testing/` を再利用し、feature 側に基盤コードを書いていない
- [ ] spec は inline 骨格、family 順序と golden ディレクトリが安定
- [ ] update フラグなしで green (output-preserving)
- [ ] Konsist (AllKotlinFilesTest / ArchTest×2) 含むフル `:koma-strict-ksp:test` green
- [ ] golden spot-check 済み (生成 / reject / options)
- [ ] 意図的な非カバーを spec KDoc に記録した
- [ ] golden を手編集していない / generator (src/main) に触れていない

## Gotchas

- **double-snapshot 禁止**: `runCompileSnapshotTest` が全 facet を出す。同一テストで
  `assertMatchesSnapshot` を重ねて呼ばない
- **Konsist の行数上限 300** はテストファイルにも効く。scenario ファイルは小さく分割
- **zsh は unquoted `$var` を word-split しない** — 複数ファイルのループは
  `find … | while IFS= read -r f; do …; done` で
- 詳細な snapshot format / 更新運用 / build.gradle.kts の前提は `.claude/rules/ksp-test.md`、
  レイヤリングは `.claude/rules/ksp-architecture.md` が正本
