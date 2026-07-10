package me.tbsten.koma.strict.ksp.options

import me.tbsten.koma.strict.InternalKomaStrictApi

/**
 * 死にアクション (どの state もハンドルしないアクション) 診断の severity。
 *
 * TODO(doc/internal/generate-strict-store-factory-dsl.md): オプション名は実装時決定 (doc 未決事項 3)。
 *   DSL 実装着手時に名前を最終確認し、必要なら rename する。
 */
@InternalKomaStrictApi
public enum class DeadActionSeverity {
    WARNING,
    ERROR,
    ;

    public companion object {
        public val default: DeadActionSeverity = WARNING
    }
}
