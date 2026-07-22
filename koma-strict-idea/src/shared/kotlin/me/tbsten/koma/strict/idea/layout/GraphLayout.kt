package me.tbsten.koma.strict.idea.layout

import me.tbsten.koma.strict.idea.ir.NodeId

/** Result of the layered layout: a rectangle for every node and composite box, plus the canvas size. */
data class GraphLayout(
    val direction: LayoutDirection,
    /** Layer index (0-based, along the direction axis) of each node. Exposed for testing / debugging. */
    val layers: Map<NodeId, Int>,
    val nodeRects: Map<NodeId, Rect>,
    val compositeRects: Map<NodeId, Rect>,
    val canvasSize: Size,
) {
    fun rect(id: NodeId): Rect? = nodeRects[id]
}
