package me.tbsten.koma.strict.idea.flow

import me.tbsten.koma.strict.idea.ir.EdgeKind
import me.tbsten.koma.strict.idea.model.StateId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure flow-model tests (`ide-test-code.md`): cursor / append / truncate / initial reset. */
class RecordedFlowTest {

    private fun action(from: StateId, to: StateId?, stay: Boolean = false) =
        FlowTransition(EdgeKind.ACTION, typeRef = "A.X", label = "x", fromId = from, target = to, stay = stay)

    @Test
    fun `cursor は initial から始まり非 stay の target へ進む`() {
        val a = StateId("A")
        val b = StateId("B")
        val flow = RecordedFlow(initial = a).append(action(a, b))
        assertEquals(b, flow.cursor)
    }

    @Test
    fun `stay は cursor を動かさない`() {
        val a = StateId("A")
        val b = StateId("B")
        val flow = RecordedFlow(initial = a)
            .append(action(a, b))
            .append(action(b, null, stay = true))
        assertEquals(b, flow.cursor)
    }

    @Test
    fun `空の flow の cursor は initial`() {
        val a = StateId("A")
        assertEquals(a, RecordedFlow(initial = a).cursor)
        assertTrue(RecordedFlow(initial = a).isEmpty)
    }

    @Test
    fun `truncateFromRow は行以降を切り詰める`() {
        val a = StateId("A"); val b = StateId("B"); val c = StateId("C")
        val flow = RecordedFlow(initial = a)
            .append(action(a, b)) // row 1
            .append(action(b, c)) // row 2
        // row 2 削除 = transitions.take(1)
        assertEquals(1, flow.truncateFromRow(2).transitions.size)
        // row 1 削除 = transitions.take(0)
        assertEquals(0, flow.truncateFromRow(1).transitions.size)
        // row 0 (initial) 削除 = 全 transition クリア (initial は残る)
        assertEquals(a, flow.truncateFromRow(0).initial)
        assertTrue(flow.truncateFromRow(0).isEmpty)
    }

    @Test
    fun `withInitial は transition を全部捨てる`() {
        val a = StateId("A"); val b = StateId("B"); val c = StateId("C")
        val flow = RecordedFlow(initial = a).append(action(a, b))
        val reset = flow.withInitial(c)
        assertEquals(c, reset.initial)
        assertTrue(reset.isEmpty)
    }

    @Test
    fun `cleared は initial を保って transition だけ捨てる`() {
        val a = StateId("A"); val b = StateId("B")
        val flow = RecordedFlow(initial = a).append(action(a, b))
        val cleared = flow.cleared()
        assertEquals(a, cleared.initial)
        assertTrue(cleared.isEmpty)
    }
}
