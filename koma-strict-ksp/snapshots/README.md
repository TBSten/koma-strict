# Snapshot goldens

`:koma-strict-ksp` の facet snapshot テスト (`testing/snapshot/SnapshotAssertion.kt`) の golden ファイル置き場。

- パス規約: `snapshots/<Spec 名>/<ネストしたテスト名>/....md` (kotest のテスト名がそのままパスになる)
- 再生成: `./gradlew :koma-strict-ksp:test -Dkoma.strict.snapshot.update=true`
- golden の diff がレビュー対象そのもの。生成コードの変更は必ずこの diff で確認する
- golden ファイル名に連番 (01, p01 等) を入れない (プロジェクト規約)
