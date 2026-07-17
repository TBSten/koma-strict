package me.tbsten.koma.strict.ksp.model

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * ツリー走査ヘルパ (nodesWithPath / leavesWithPath / nodeAt) の単体テスト。
 * fixture は doc/internal/samples.md ケース 2 (FeedState) を単純化した 2 段入れ子
 * + 同 simpleName leaf (`Loading` と `Refresh.Loading`) を含む形。
 */
internal class StateNodeTreeTest :
    FreeSpec({
        fun leaf(name: String): LeafNode =
            LeafNode(
                simpleName = name,
                declarationKind = StateDeclarationKind.INTERFACE,
                companionName = "Companion",
            )

        val refreshGroup =
            GroupNode(
                simpleName = "Refresh",
                companionName = "Companion",
                children = listOf(leaf("Loading"), leaf("Error")),
            )
        val stableGroup =
            GroupNode(
                simpleName = "Stable",
                companionName = "Companion",
                children = listOf(leaf("Idle"), refreshGroup),
            )
        val root =
            RootNode(
                type = TypeRef("example", "MyState"),
                companionName = "Companion",
                children = listOf(leaf("Loading"), stableGroup, leaf("Error")),
            )

        "nodesWithPath は root を含む pre-order (ソース宣言順) で列挙する" {
            val actual = root.nodesWithPath().map { (path, _) -> path.dotJoined() }
            actual shouldBe
                listOf(
                    "",
                    "Loading",
                    "Stable",
                    "Stable.Idle",
                    "Stable.Refresh",
                    "Stable.Refresh.Loading",
                    "Stable.Refresh.Error",
                    "Error",
                )
        }

        "leavesWithPath は具象 leaf のみを列挙し中間 sealed を含まない" {
            val actual = root.leavesWithPath().map { (path, _) -> path.dotJoined() }
            actual shouldBe
                listOf(
                    "Loading",
                    "Stable.Idle",
                    "Stable.Refresh.Loading",
                    "Stable.Refresh.Error",
                    "Error",
                )
        }

        "同 simpleName の leaf も path で区別される" {
            val topLoading = root.nodeAt(StatePath("Loading"))
            val nestedLoading = root.nodeAt(StatePath("Stable", "Refresh", "Loading"))
            topLoading.shouldBeInstanceOf<LeafNode>().simpleName shouldBe "Loading"
            nestedLoading.shouldBeInstanceOf<LeafNode>().simpleName shouldBe "Loading"
        }

        "nodeAt は空 path で root 自身を返す" {
            root.nodeAt(StatePath.root) shouldBe root
        }

        "nodeAt は中間 sealed も解決する" {
            root.nodeAt(StatePath("Stable", "Refresh")) shouldBe refreshGroup
        }

        "nodeAt は存在しない path に null を返す" {
            root.nodeAt(StatePath("Unknown")) shouldBe null
            // leaf の下は辿れない
            root.nodeAt(StatePath("Loading", "Unknown")) shouldBe null
        }
    })
