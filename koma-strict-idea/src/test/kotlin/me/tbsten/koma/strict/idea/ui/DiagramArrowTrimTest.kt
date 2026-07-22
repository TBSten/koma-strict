package me.tbsten.koma.strict.idea.ui

import androidx.compose.ui.geometry.Offset
import me.tbsten.koma.strict.idea.ui.diagram.trimCubicEnd
import me.tbsten.koma.strict.idea.ui.diagram.trimPolylineEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * `ide-2.md` #3 の回帰: 線本体を arrowhead の付け根 (tip から barb 手前) で止めて矢じりとの二重描画を
 * 無くす純粋幾何 ([trimPolylineEnd] / [trimCubicEnd]) を検証する。半透明 stay で tip が濃くなる問題の要。
 */
class DiagramArrowTrimTest {

    private fun dist(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    @Test
    fun `polyline は最終点を barb だけ手前へ縮め 他の点は変えない`() {
        val pts = listOf(Offset(0f, 0f), Offset(100f, 0f))
        val trimmed = trimPolylineEnd(pts, 9f)
        assertEquals(Offset(0f, 0f), trimmed.first())
        // 最終点は tip(100,0) から barb=9 手前 = (91,0)。
        assertEquals(91f, trimmed.last().x, 0.01f)
        assertEquals(0f, trimmed.last().y, 0.01f)
    }

    @Test
    fun `polyline の最終区間が barb より短い時は最終点が手前ノードへ潰れる`() {
        val pts = listOf(Offset(0f, 0f), Offset(4f, 0f))
        val trimmed = trimPolylineEnd(pts, 9f)
        // cut は区間長 4 にクランプされ、最終点は手前点まで戻る (負方向へ突き抜けない)。
        assertEquals(0f, trimmed.last().x, 0.01f)
    }

    @Test
    fun `cubic はおよそ barb ぶん短い所で終わり 始点と最初の制御点は保つ`() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(30f, -40f)
        val p2 = Offset(70f, -40f)
        val p3 = Offset(100f, 0f)
        val cut = 9f
        val trimmed = trimCubicEnd(p0, p1, p2, p3, cut)
        // De Casteljau の左側部分曲線: 始点と第1制御点方向 (P0->P1 上の点) は不変 / 曲線として保たれる。
        assertEquals(p0, trimmed[0])
        // 縮めた終点は元の終点から曲線長でおよそ barb ぶん手前 (直線距離では barb 以下)。
        val newEnd = trimmed.last()
        assertTrue("終点が元の終点から離れている", dist(newEnd, p3) > 1f)
        assertTrue("縮めすぎていない (直線距離 <= barb*1.5)", dist(newEnd, p3) <= cut * 1.5f)
    }

    @Test
    fun `cubic が cut より短ければ元の制御点をそのまま返す`() {
        val p0 = Offset(0f, 0f)
        val p1 = Offset(1f, 0f)
        val p2 = Offset(2f, 0f)
        val p3 = Offset(3f, 0f)
        val trimmed = trimCubicEnd(p0, p1, p2, p3, 9f)
        assertEquals(listOf(p0, p1, p2, p3), trimmed)
    }
}
