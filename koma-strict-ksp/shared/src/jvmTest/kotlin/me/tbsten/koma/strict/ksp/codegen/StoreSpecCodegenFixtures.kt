package me.tbsten.koma.strict.ksp.codegen

import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.EventRef
import me.tbsten.koma.strict.ksp.model.GroupNode
import me.tbsten.koma.strict.ksp.model.LeafNode
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StateDeclarationKind
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.StateProp
import me.tbsten.koma.strict.ksp.model.StoreSpec
import me.tbsten.koma.strict.ksp.model.TransitionSpec
import me.tbsten.koma.strict.ksp.model.TypeRef

// codegen 単体テスト (GenerateStoreSpecFilesTest / GenerateStoreSpecBuildersTest) が共有する
// 手組み StoreSpec model。samples.md の LCE ケースと、自宣言つき中間 sealed の Flow ケース。

/** samples.md ケース 1 の LoadFailed event。 */
internal val lceLoadFailedEvent: EventRef =
    EventRef(
        type = TypeRef("example", "LceEvent.LoadFailed"),
        isObject = false,
        params = listOf(StateProp(name = "message", type = "String", isNullable = true)),
    )

/** samples.md ケース 1 (基本 LCE) 相当の手組み model。Loading = enter 宣言つき / Content = action のみ。 */
internal val lceCodegenSpec: StoreSpec =
    StoreSpec(
        root =
            RootNode(
                type = TypeRef("example", "LceState"),
                companionName = "Companion",
                children =
                    listOf(
                        LeafNode(
                            simpleName = "Loading",
                            declarationKind = StateDeclarationKind.INTERFACE,
                            companionName = "Companion",
                            enter =
                                EnterHandler(
                                    transition =
                                        TransitionSpec.of(
                                            targets = listOf(StatePath("Content")),
                                            declaredStay = false,
                                        ),
                                    emits = listOf(lceLoadFailedEvent),
                                ),
                        ),
                        LeafNode(
                            simpleName = "Content",
                            declarationKind = StateDeclarationKind.INTERFACE,
                            companionName = "Companion",
                            props = listOf(StateProp(name = "data", type = "String")),
                            actions =
                                listOf(
                                    ActionHandler(
                                        action = TypeRef("example", "LceAction.Reload"),
                                        transition =
                                            TransitionSpec.of(
                                                targets = listOf(StatePath("Loading")),
                                                declaredStay = false,
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ),
        actionsType = TypeRef("example", "LceAction"),
        eventsType = TypeRef("example", "LceEvent"),
        initial = listOf(StatePath("Loading")),
    )

/**
 * 自宣言 + 子を持つ中間 sealed のケース:
 * FlowState { Idle; @OnAction(Cancel) sealed Refresh { Running(宣言ゼロ); Failed(@OnAction Retry) } } 相当。
 */
internal val flowCodegenSpec: StoreSpec =
    StoreSpec(
        root =
            RootNode(
                type = TypeRef("example", "FlowState"),
                companionName = "Companion",
                children =
                    listOf(
                        LeafNode(
                            simpleName = "Idle",
                            declarationKind = StateDeclarationKind.INTERFACE,
                            companionName = "Companion",
                            actions =
                                listOf(
                                    ActionHandler(
                                        action = TypeRef("example", "FlowAction.Start"),
                                        transition =
                                            TransitionSpec.of(
                                                targets = listOf(StatePath("Refresh", "Running")),
                                                declaredStay = false,
                                            ),
                                    ),
                                ),
                        ),
                        GroupNode(
                            simpleName = "Refresh",
                            companionName = "Companion",
                            actions =
                                listOf(
                                    ActionHandler(
                                        action = TypeRef("example", "FlowAction.Cancel"),
                                        transition =
                                            TransitionSpec.of(
                                                targets = listOf(StatePath("Idle")),
                                                declaredStay = false,
                                            ),
                                    ),
                                ),
                            children =
                                listOf(
                                    LeafNode(
                                        simpleName = "Running",
                                        declarationKind = StateDeclarationKind.INTERFACE,
                                        companionName = "Companion",
                                    ),
                                    LeafNode(
                                        simpleName = "Failed",
                                        declarationKind = StateDeclarationKind.INTERFACE,
                                        companionName = "Companion",
                                        actions =
                                            listOf(
                                                ActionHandler(
                                                    action = TypeRef("example", "FlowAction.Retry"),
                                                    transition =
                                                        TransitionSpec.of(
                                                            targets = listOf(StatePath("Refresh", "Running")),
                                                            declaredStay = false,
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ),
        actionsType = TypeRef("example", "FlowAction"),
        eventsType = null,
    )

/** 自宣言の無い中間 sealed のケース (Refresh 自身は宣言なし・子 Failed のみ宣言)。 */
internal val groupOnlyFlowCodegenSpec: StoreSpec =
    StoreSpec(
        root =
            RootNode(
                type = TypeRef("example", "FlowState"),
                companionName = "Companion",
                children =
                    listOf(
                        GroupNode(
                            simpleName = "Refresh",
                            companionName = "Companion",
                            children =
                                listOf(
                                    LeafNode(
                                        simpleName = "Failed",
                                        declarationKind = StateDeclarationKind.INTERFACE,
                                        companionName = "Companion",
                                        actions =
                                            listOf(
                                                ActionHandler(
                                                    action = TypeRef("example", "FlowAction.Retry"),
                                                    transition =
                                                        TransitionSpec.of(
                                                            targets = listOf(StatePath("Refresh", "Failed")),
                                                            declaredStay = false,
                                                        ),
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ),
        actionsType = TypeRef("example", "FlowAction"),
        eventsType = null,
    )
