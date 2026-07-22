package me.tbsten.koma.strict.idea.layout

import me.tbsten.koma.strict.idea.SampleModels
import me.tbsten.koma.strict.idea.ir.DiagramGraph
import me.tbsten.koma.strict.idea.ir.GraphLowering
import me.tbsten.koma.strict.idea.layout.edge.EdgeRouting
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression bounds for edge crossings after the barycenter coordinate pull
 * ([LayeredLayout.pullTowardNeighbors]): the grid placement left long full-canvas diagonals in
 * dense figures. Pulling nodes toward their neighbours' median rows straightens those edges;
 * these bounds pin the achieved level so a future layout change cannot silently regress it.
 * (auth == 0 is pinned separately in EdgeRoutingTest.)
 */
class CrossBaselineTest {
    private fun properlyIntersects(a1: Point, a2: Point, b1: Point, b2: Point): Boolean {
        fun cross(o: Point, p: Point, q: Point): Double =
            (p.x - o.x) * (q.y - o.y) - (p.y - o.y) * (q.x - o.x)
        val eps = 1e-9
        val d1 = cross(b1, b2, a1); val d2 = cross(b1, b2, a2)
        val d3 = cross(a1, a2, b1); val d4 = cross(a1, a2, b2)
        return ((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) &&
            ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))
    }

    private fun crossings(graph: DiagramGraph): Int {
        val layout = LayeredLayout.layout(graph)
        val routes = EdgeRouting.routeAll(graph, layout)
        val edges = graph.edges.filter { it.fromId != it.toId }.mapNotNull { routes[it] }
        var c = 0
        for (i in edges.indices) for (j in i + 1 until edges.size) {
            val a = edges[i]; val b = edges[j]
            for (s in 0 until a.size - 1) for (t in 0 until b.size - 1) {
                if (properlyIntersects(a[s], a[s + 1], b[t], b[t + 1])) c++
            }
        }
        return c
    }

    @Test
    fun `feed-branch は交差ゼロを保つ`() {
        val c = crossings(GraphLowering.lower(SampleModels.feedBranch()))
        assertTrue("crossings=$c", c == 0)
    }
}
