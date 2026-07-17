package me.tbsten.koma.strict.ksp.codegen

// 生成関数の末尾センチネル引数。最後の引数が関数型でなくなるため trailing lambda 記法が
// コンパイルエラーになり、全 param が named 引数として書かれることを保つ。
// 現在の使いどころは「共有宣言 (default ブロック) の actions()」と「escape member が
// 1 つも無い縮退した states()」のみ:
// - states() の末尾は per-state escape の `configure` block に置き換わった (trailing lambda が
//   意図された入力になった)。escape member ゼロの states() だけセンチネルを維持する
//   (空 escape は無意味 + v4 登録 builder と単一 lambda の overload 解決が曖昧になるため)
// - leaf の actions() には付けない — 末尾は per-state escape hatch の `configure`
// - per-store factory 関数にも付けない — 末尾は store-level escape hatch の `configuration`
// 型は koma-strict-runtime の me.tbsten.koma.strict.PreventTrailingLambda
// (生成ファイル先頭の `import me.tbsten.koma.strict.*` で解決される)。

/** The trailing sentinel parameter of the generated functions that take no trailing block (callers prepend their own indent). */
internal const val SENTINEL_PARAM: String =
    "preventTrailingLambda: PreventTrailingLambda = PreventTrailingLambda,"
