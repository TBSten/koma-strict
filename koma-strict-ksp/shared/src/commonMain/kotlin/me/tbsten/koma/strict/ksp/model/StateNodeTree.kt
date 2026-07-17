package me.tbsten.koma.strict.ksp.model

import me.tbsten.koma.strict.InternalKomaStrictApi

// StoreSpec model のツリー走査ヘルパ (pure 関数)。
// 検証・codegen・図 IR 構築が共有する「node と path のペア」列挙を一元化する。

/** Enumerates all nodes, including the root, in pre-order (source declaration order). */
@InternalKomaStrictApi
public fun RootNode.nodesWithPath(): List<Pair<StatePath, StateNode>> =
    buildList {
        fun visit(
            path: StatePath,
            node: StateNode,
        ) {
            add(path to node)
            if (node is StateParent) {
                node.children.forEach { child -> visit(path + child.simpleName, child) }
            }
        }
        visit(StatePath.root, this@nodesWithPath)
    }

/** Enumerates concrete leaves only, in pre-order (source declaration order). */
@InternalKomaStrictApi
public fun RootNode.leavesWithPath(): List<Pair<StatePath, LeafNode>> =
    nodesWithPath().mapNotNull { (path, node) ->
        if (node is LeafNode) path to node else null
    }

/** Whether this node itself has handler declarations (enter / exit / actions / recovers). */
@InternalKomaStrictApi
public fun StateNode.hasOwnHandlerDeclarations(): Boolean =
    (this as? LeafNode)?.enter != null || exit != null || actions.isNotEmpty() || recovers.isNotEmpty()

/**
 * Whether this node's subtree (including itself) has at least one handler declaration.
 * Nodes where this is false get no param on the facade (`states()`) (zero-declaration states).
 */
@InternalKomaStrictApi
public fun StateNode.hasAnyHandlerDeclarations(): Boolean =
    hasOwnHandlerDeclarations() ||
        (this as? StateParent)?.children?.any { it.hasAnyHandlerDeclarations() } == true

/** Returns the node [path] points to, or null if it does not exist. The empty path is the root itself. */
@InternalKomaStrictApi
public fun RootNode.nodeAt(path: StatePath): StateNode? {
    var current: StateNode = this
    for (segment in path.segments) {
        current = (current as? StateParent)
            ?.children
            ?.find { it.simpleName == segment }
            ?: return null
    }
    return current
}
