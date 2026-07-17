package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.InternalKomaStrictApi
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StoreSpec
import me.tbsten.koma.strict.ksp.model.nodesWithPath
import me.tbsten.koma.strict.ksp.naming.generatedFileName
import me.tbsten.koma.strict.ksp.naming.storeSpecFileName

/**
 * Pure function (KSP-independent) that assembles generated file contents (strings) from a
 * validated [StoreSpec] model.
 *
 * - 1 node = 1 file (`<qualified state>.generated`). Nodes that generate nothing produce no file
 * - The root `states()` extension and the per-store factory function go to
 *   `<Root>.storeSpec.generated` (the only whole-spec-dependent file)
 * - The canonical shape of the expected output is doc/internal/samples.md (the LCE case covers everything)
 * - All visibility is explicit (compiles even when the consuming module uses `explicitApi()`):
 *   supporting types inherit visibility from the state declarations ([StoreSpec.visibility])
 *   with internal constructors / internals read only by the facade are internal /
 *   Impl is private (internal only for states without a companion)
 */
@InternalKomaStrictApi
public fun generateStoreSpecFiles(spec: StoreSpec): List<GeneratedFile> {
    val env = CodegenEnv(spec)
    return buildList {
        spec.root.nodesWithPath().forEach { (path, node) ->
            val content = buildString { appendNodeFile(env, path, node) }
            if (content.isNotEmpty()) {
                add(GeneratedFile(fileName = generatedFileName(spec.root, path), content = content))
            }
        }
        // root states() の trailing escape scope も whole-spec 依存 (member = root の子) なので同ファイル
        val rootEscapeMembers = statesConfigureMembers(env, StatePath.root, spec.root)
        add(
            GeneratedFile(
                fileName = storeSpecFileName(spec.root),
                content =
                    buildList {
                        add(buildString { appendRootStates(env) })
                        add(buildString { appendStoreFactory(env) })
                        if (rootEscapeMembers.isNotEmpty()) {
                            add(
                                statesConfigureScopeSection(
                                    env,
                                    scopeType = rootStatesConfigureScopeType(env),
                                    owner = env.rootRef,
                                    members = rootEscapeMembers,
                                ),
                            )
                        }
                    }.joinToString("\n"),
            ),
        )
    }
}
