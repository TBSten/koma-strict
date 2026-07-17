package me.tbsten.koma.strict

/**
 * Sentinel type for the last parameter of generated functions that take no trailing block:
 * the `actions(...)` functions of shared declarations (default blocks), and the degenerate
 * `states(...)` functions without any escape member.
 *
 * Because the (defaulted) last parameter is not a function type, the trailing-lambda syntax
 * becomes a compile error, keeping the param-name tree visible at the call site. Leaf
 * `actions(...)` and regular `states(...)` do not carry this sentinel: their trailing
 * parameter is the `configure` escape hatch (per-state raw koma DSL), which is meant to be
 * written as a trailing lambda.
 *
 * Not intended to be passed by users directly (generated code only).
 */
public data object PreventTrailingLambda
