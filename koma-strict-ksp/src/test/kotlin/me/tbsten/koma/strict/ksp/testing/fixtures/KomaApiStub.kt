package me.tbsten.koma.strict.ksp.testing.fixtures

import org.intellij.lang.annotations.Language

/**
 * kctfork 入力に常に含める koma API の最小スタブ。
 *
 * 由来: [koma](https://github.com/koma-kt/koma) v4 (koma-core 4.0.0-rc02) の **実物 sources jar と
 * 突き合わせ済み** (2026-07-15、スパイク (d) 解消)。忠実化した軸:
 * - scope の型引数順は実物どおり (`EnterScope<S, E, S2>` / `ActionScope<S, A, E, S2>` /
 *   `ExitScope<S, E, S2>` / `RecoverScope<S, E, S2, T>`)
 * - per-state builder は `StoreBuilder.StateHandlerConfig<S, A, E, S2>` (StoreBuilder の nested class)
 * - 全 handler scope に `clearPendingActions()` (非 suspend・引数なし)
 * - @DslMarker `koma.core.KomaStoreDsl` を builder / StateHandlerConfig / 全 scope に付与
 *   (生成 scope への併記が leak を遮断することのコンパイル検証に必要)
 * - `Store()` factory のシグネチャ (`initialState: S? = null` / `context: CoroutineContext? = null`)
 *   — 生成される per-store factory 関数が context を転送するため実物どおりに保つ
 *
 * 実物との意図的な差 (生成コードのコンパイル検証に不要な範囲):
 * - `enter` / `action` / `exit` / `recover` の optional `dispatcher` param は省略
 *   (kotlinx-coroutines へのクラスパス依存を避ける。生成コードは dispatcher を渡さない)
 * - `state` / `action` / `recover` は実物では inline reified だが、生成コードは型引数を明示するため
 *   非 inline で代替
 * - `launch` / `transaction` / plugin / policy 系は未収録
 *
 * 実物の koma-core を classpath に足さないのは hermetic なテストのため (Kotlin/JVM target 差異や
 * ネットワーク依存を避ける)。実物での E2E は :integrationTest が担う。
 */
@Language("kotlin")
internal val komaApiStubCode: String =
    """
    package koma.core

    public interface State
    public interface Action
    public interface Event

    @DslMarker
    @Retention(AnnotationRetention.BINARY)
    @Target(AnnotationTarget.CLASS)
    public annotation class KomaStoreDsl

    public class Store<S : State, A : Action, E : Event> internal constructor()

    @Suppress("FunctionName", "UNUSED_PARAMETER")
    public fun <S : State, A : Action, E : Event> Store(
        initialState: S? = null,
        context: kotlin.coroutines.CoroutineContext? = null,
        builder: StoreBuilder<S, A, E>.() -> Unit,
    ): Store<S, A, E> {
        StoreBuilder<S, A, E>().builder()
        return Store()
    }

    @KomaStoreDsl
    public class StoreBuilder<S : State, A : Action, E : Event> internal constructor() {
        @Suppress("UNUSED_PARAMETER")
        public fun <S2 : S> state(block: StateHandlerConfig<S, A, E, S2>.() -> Unit) {
        }

        @KomaStoreDsl
        public class StateHandlerConfig<S : State, A : Action, E : Event, S2 : S> internal constructor() {
            @Suppress("UNUSED_PARAMETER")
            public fun enter(block: suspend EnterScope<S, E, S2>.() -> Unit) {
            }

            @Suppress("UNUSED_PARAMETER")
            public fun exit(block: suspend ExitScope<S, E, S2>.() -> Unit) {
            }

            @Suppress("UNUSED_PARAMETER")
            public fun <A2 : A> action(block: suspend ActionScope<S, A2, E, S2>.() -> Unit) {
            }

            @Suppress("UNUSED_PARAMETER")
            public fun <T : Exception> recover(block: suspend RecoverScope<S, E, S2, T>.() -> Unit) {
            }
        }
    }

    @KomaStoreDsl
    public class EnterScope<S : State, E : Event, S2 : S> internal constructor() {
        public val state: S2 get() = throw UnsupportedOperationException("stub")

        @Suppress("UNUSED_PARAMETER")
        public suspend fun event(event: E) {
        }

        @Suppress("UNUSED_PARAMETER")
        public fun nextState(block: () -> S) {
        }

        public fun clearPendingActions() {
        }
    }

    @KomaStoreDsl
    public class ActionScope<S : State, A : Action, E : Event, S2 : S> internal constructor() {
        public val state: S2 get() = throw UnsupportedOperationException("stub")
        public val action: A get() = throw UnsupportedOperationException("stub")

        @Suppress("UNUSED_PARAMETER")
        public suspend fun event(event: E) {
        }

        @Suppress("UNUSED_PARAMETER")
        public fun nextState(block: () -> S) {
        }

        public fun clearPendingActions() {
        }
    }

    @KomaStoreDsl
    public class ExitScope<S : State, E : Event, S2 : S> internal constructor() {
        public val state: S2 get() = throw UnsupportedOperationException("stub")

        @Suppress("UNUSED_PARAMETER")
        public suspend fun event(event: E) {
        }

        public fun clearPendingActions() {
        }
    }

    @KomaStoreDsl
    public class RecoverScope<S : State, E : Event, S2 : S, T : Exception> internal constructor() {
        public val state: S2 get() = throw UnsupportedOperationException("stub")
        public val error: T get() = throw UnsupportedOperationException("stub")

        @Suppress("UNUSED_PARAMETER")
        public suspend fun event(event: E) {
        }

        @Suppress("UNUSED_PARAMETER")
        public fun nextState(block: () -> S) {
        }

        public fun clearPendingActions() {
        }
    }
    """.trimIndent()

/** スタブの入力ファイル名。利用側テストの入力ファイル名と衝突しないよう固定名を予約する。 */
internal const val KOMA_API_STUB_FILE_NAME: String = "KomaApiStub.kt"
