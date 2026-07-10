package me.tbsten.koma.strict

/**
 * TODO Placeholder annotation to prove the koma-strict KSP pipeline end-to-end.
 *
 * 本命の宣言 API (`@StoreSpec` / `@OnEnter` / `@OnAction` / `@DefaultName` / `Stay`) の設計は
 * doc/internal/generate-strict-store-factory-dsl.md を参照。DSL 実装着手時にこの annotation を
 * 削除し、上記 API へ置き換える。
 *
 * 現状は `@StrictStore` を付けた class ごとに `<ClassName>StoreFactory.kt` (stub 関数入り) が
 * koma-strict-ksp によって生成される。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class StrictStore
