package me.tbsten.koma.strict.ksp.naming

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.koma.strict.ksp.model.StatePath

/** 遷移関数名 (source から見た相対名) の単体テスト。期待値は doc/internal/samples.md の実例。 */
internal class TransitionMethodNameTest :
    FreeSpec({
        "トップレベル間の遷移は target 名そのまま" {
            transitionMethodName(StatePath("Content"), StatePath("Loading")) shouldBe "toLoading"
        }

        "外から入れ子への遷移は path 連結になる" {
            transitionMethodName(StatePath("Loading"), StatePath("Stable", "Idle")) shouldBe "toStableIdle"
        }

        "同一 scope 内の遷移は共通 prefix を落とす" {
            transitionMethodName(StatePath("Stable", "Idle"), StatePath("Stable", "Refreshing")) shouldBe
                "toRefreshing"
            transitionMethodName(
                StatePath("Stable", "Refresh", "Error"),
                StatePath("Stable", "Refresh", "Loading"),
            ) shouldBe "toLoading"
        }

        "自己遷移は自身の名前になる" {
            transitionMethodName(StatePath("Search"), StatePath("Search")) shouldBe "toSearch"
        }

        "root 共有宣言 (source = root) からは root からの相対名になる" {
            transitionMethodName(StatePath.root, StatePath("LoggedOut")) shouldBe "toLoggedOut"
        }

        "中間 sealed の共有宣言からは親 scope からの相対名になる" {
            transitionMethodName(StatePath("Stable", "Refresh"), StatePath("Stable", "Idle")) shouldBe "toIdle"
        }
    })
