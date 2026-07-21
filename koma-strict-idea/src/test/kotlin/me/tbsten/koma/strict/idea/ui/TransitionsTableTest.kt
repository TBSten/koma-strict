package me.tbsten.koma.strict.idea.ui

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 遷移表 (図の「正」) を model から抽出する transitionRows を素の JUnit で検証する。 */
class TransitionsTableTest {

    @Test
    fun `LCE の遷移表は各 trigger を From-Trigger-To-Emit で列挙する`() {
        val rows = SampleModels.lce().transitionRows()

        // onEnter は target ごとに 1 行。emit はイベント名。
        assertTrue(rows.any { it.from == "Loading" && it.trigger == "onEnter" && it.to == "Content" && it.emit == "LoadFailed" && !it.stay })
        assertTrue(rows.any { it.from == "Loading" && it.trigger == "onEnter" && it.to == "Error" && it.emit == "LoadFailed" })
        // action は decapitalize したトリガ名。
        assertTrue(rows.any { it.from == "Content" && it.trigger == "reload" && it.to == "Loading" })
        assertTrue(rows.any { it.from == "Error" && it.trigger == "retry" && it.to == "Loading" })
    }

    @Test
    fun `stay トリガは To に from(stay) を畳み込み scope 共有アクションは any 起点で出る`() {
        // 条件付き [Stay, LoadingMore]: 通常行 + stay 行の 2 本。stay 行の To は「<from> (stay)」。
        val feed = SampleModels.feed().transitionRows()
        assertTrue(feed.any { it.from == "Idle" && it.trigger == "loadMore" && !it.stay && it.to == "LoadingMore" })
        assertTrue(feed.any { it.from == "Idle" && it.trigger == "loadMore" && it.stay && it.to == "Idle (stay)" })

        // root 共有アクション selectTab は from="any"、stay は「any (stay)」行。
        val tabs = SampleModels.tabs().transitionRows()
        assertTrue(tabs.any { it.from == "any" && it.trigger == "selectTab" && it.to == "Home" })
        assertTrue(tabs.any { it.from == "any" && it.trigger == "selectTab" && it.stay && it.to == "any (stay)" })
    }

    @Test
    fun `recover は any 起点行になり exit は遷移なし行として表に出る`() {
        val rows = SampleModels.auth().transitionRows()

        // root 共有 recover: from="any"、To=LoggedOut、emit=SessionExpired。
        assertTrue(rows.any { it.from == "any" && it.trigger == "on SessionExpiredException" && it.to == "LoggedOut" && it.emit == "SessionExpired" })

        // exit は To="—"(遷移なし) だが emit を隠さない。
        assertTrue(rows.any { it.from == "Authenticating" && it.trigger == "exit" && it.to == "—" && it.emit == "AuthAttemptFinished" && !it.stay })
    }

    // 行クリック遷移用: leaf 行は leaf source を、scope 共有行 (any / any <Group>) は scope の source を運ぶ。
    @Test
    fun `leaf 行と scope 共有行がそれぞれ自身の宣言 source を運ぶ`() {
        val leafAnchor = object : SourceAnchor {}
        val rootAnchor = object : SourceAnchor {}
        val groupAnchor = object : SourceAnchor {}
        val group = GroupState(
            simpleName = "G",
            id = StateId("G"),
            children = listOf(LeafState("C", StateId("G", "C"))),
            // group 共有アクション: any G 行になる。
            actions = listOf(ActionTrigger("GroupShared", targets = listOf(StateId("A")))),
            source = groupAnchor,
        )
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "A",
                    StateId("A"),
                    actions = listOf(ActionTrigger("Go", targets = listOf(StateId("C")))),
                    source = leafAnchor,
                ),
                group,
            ),
            // root 共有アクション: any 行になる。
            actions = listOf(ActionTrigger("RootShared", targets = listOf(StateId("A")))),
            source = rootAnchor,
        )
        val rows = StoreDiagramModel(root = root, initial = listOf(StateId("A"))).transitionRows()

        assertSame("leaf 行は leaf 宣言を指す", leafAnchor, rows.first { it.from == "A" }.source)
        assertSame("any 行は root 宣言を指す", rootAnchor, rows.first { it.from == "any" }.source)
        assertSame("any G 行は group 宣言を指す", groupAnchor, rows.first { it.from == "any G" }.source)
    }

    // P1-01 / P1-02: 空 nextState 正規化 (stay=true, targets=[]) は (stay) 行になり "—" 行を作らない。
    // 未解決 target は ?付きで表 (= 正) に残す (silent truncation を許さない)。
    @Test
    fun `stay 正規化は stay 行になり 未解決 target はマーク付きで表に残る`() {
        val root = RootState(
            simpleName = "S",
            children = listOf(
                LeafState(
                    "A",
                    StateId("A"),
                    actions = listOf(
                        // 空 nextState の正規化形: stay=true, targets=[]。
                        ActionTrigger("Stayer", targets = emptyList(), stay = true),
                        // 未解決 target のみ (foreign / error type)。
                        ActionTrigger("Goer", targets = emptyList(), unresolvedTargets = listOf("?Foreign")),
                    ),
                ),
            ),
        )
        val rows = StoreDiagramModel(root = root, initial = listOf(StateId("A"))).transitionRows()

        assertTrue("stay 行になる", rows.any { it.from == "A" && it.trigger == "stayer" && it.stay && it.to == "A (stay)" })
        assertFalse("stay トリガは — 行を作らない", rows.any { it.trigger == "stayer" && it.to == "—" })
        assertTrue("未解決 target 行が ?付きで残る", rows.any { it.from == "A" && it.trigger == "goer" && it.to == "?Foreign" })
        assertFalse("未解決だけのトリガも — 行にしない", rows.any { it.trigger == "goer" && it.to == "—" })
    }
}
