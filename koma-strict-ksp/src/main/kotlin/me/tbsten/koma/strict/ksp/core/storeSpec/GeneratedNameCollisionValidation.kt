package me.tbsten.koma.strict.ksp.core.storeSpec

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.tbsten.koma.strict.ksp.codegen.generatedTopLevelFunctionNames
import me.tbsten.koma.strict.ksp.codegen.generatedTopLevelTypeNames
import me.tbsten.koma.strict.ksp.core.common.fullName
import me.tbsten.koma.strict.ksp.model.StoreSpec

/**
 * Collision validation of generated top-level names (across all @StoreSpec in the round).
 *
 * Because leaf generated-type prefixes do not include the root
 * ([me.tbsten.koma.strict.ksp.naming.generatedTypePrefix]), within **one package**:
 *
 * - same-named leaves in different @StoreSpec hierarchies (`FooScreenState.Loading` and
 *   `BarScreenState.Loading`)
 * - coinciding path concatenations within one hierarchy (`Stable.Idle` and `StableIdle`)
 *
 * make `LoadingHandlers` / `LoadingImpl` etc. redeclarations, and without a KSP diagnostic
 * the generated code would fail to compile. This explicitly rejects them as KSP errors and
 * makes the involved specs skip generation entirely (no partially generated files).
 *
 * The per-store factory function names ([generatedTopLevelFunctionNames]) join the same
 * uniqueness pool: two roots whose names differ only by the stripped `State` suffix
 * (`Lce` and `LceState` -> `lceStore`) would merely overload for kotlinc, but are rejected
 * to keep the generated entry points unambiguous.
 *
 * Only collisions within the same round (= the same module) can be detected. Cases where
 * another module generates a same-named leaf in the same package are left to kotlinc's
 * redeclaration error (best effort).
 *
 * @return The set of rootDecls of the specs involved in collisions (the caller skips their generation).
 */
context(logger: KSPLogger)
internal fun reportGeneratedTypeNameCollisions(specs: List<Pair<KSClassDeclaration, StoreSpec>>): Set<KSClassDeclaration> {
    val diagnostics = StoreSpecDiagnostics(logger)
    val collided = mutableSetOf<KSClassDeclaration>()

    specs
        .groupBy { (_, spec) -> spec.root.type.packageName }
        .forEach { (packageName, packageSpecs) ->
            // 生成名 -> その名前を生成する rootDecl 列 (同一 spec 内の重複は同じ rootDecl が 2 回並ぶ)
            val owners = mutableMapOf<String, MutableList<KSClassDeclaration>>()
            packageSpecs.forEach { (rootDecl, spec) ->
                (generatedTopLevelTypeNames(spec) + generatedTopLevelFunctionNames(spec)).forEach { name ->
                    owners.getOrPut(name) { mutableListOf() }.add(rootDecl)
                }
            }
            val duplicated = owners.filterValues { it.size > 1 }
            if (duplicated.isEmpty()) return@forEach

            duplicated.keys.sorted().forEach { name ->
                val involved = duplicated.getValue(name)
                collided += involved
                diagnostics.error(
                    message =
                        "Generated declaration '$name' in package '$packageName' is generated more than once " +
                            "(by ${involved.map { "'${it.fullName}'" }.distinct().joinToString(" and ")}). " +
                            "Generated helper type names are derived from the state path without the root, " +
                            "and the store factory function name from the root name (with a trailing 'State' " +
                            "stripped), so nearby names collide within one package.",
                    solution =
                        "Rename one of the conflicting states (or roots), or move one @StoreSpec hierarchy to " +
                            "another package so the generated names stay unique.",
                    node = involved.first(),
                )
            }
        }

    return collided
}
