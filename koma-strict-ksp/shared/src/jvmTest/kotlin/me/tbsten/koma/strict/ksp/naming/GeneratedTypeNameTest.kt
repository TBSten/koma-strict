package me.tbsten.koma.strict.ksp.naming

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.RootNode
import me.tbsten.koma.strict.ksp.model.StatePath
import me.tbsten.koma.strict.ksp.model.TransitionSpec
import me.tbsten.koma.strict.ksp.model.TypeRef

/**
 * 生成型名の単体テスト。期待値は doc/internal/samples.md の想定生成コード
 * (LCE / Feed / Tabs / Auth ケース) から取っている。
 */
internal class GeneratedTypeNameTest :
    FreeSpec({
        fun root(underPackageName: String): RootNode =
            RootNode(
                type = TypeRef("example", underPackageName),
                companionName = "Companion",
                children = emptyList(),
            )

        fun action(underPackageName: String): ActionHandler =
            ActionHandler(
                action = TypeRef("example", underPackageName),
                transition = TransitionSpec.stayOnly,
            )

        "generatedTypePrefix" - {
            "leaf は path から root を除いた連結になる" {
                generatedTypePrefix(root("LceState"), StatePath("Loading")) shouldBe "Loading"
                generatedTypePrefix(root("FeedState"), StatePath("Stable", "Idle")) shouldBe "StableIdle"
            }

            "root (空 path) は root 名になる" {
                generatedTypePrefix(root("MyState"), StatePath.root) shouldBe "MyState"
            }

            "ネストした root は underPackageName 連結で同 simpleName 衝突を回避する" {
                val foo = generatedTypePrefix(root("FooScreen.State"), StatePath.root)
                val bar = generatedTypePrefix(root("BarScreen.State"), StatePath.root)
                foo shouldBe "FooScreenState"
                bar shouldBe "BarScreenState"
                foo shouldNotBe bar
            }
        }

        "per-handler 型名 (samples.md 準拠)" - {
            val enter = EnterHandler(transition = TransitionSpec.stayOnly)

            "enter handler の Scope / Reaction / Transitions" {
                handlerScopeTypeName("Loading", enter) shouldBe "LoadingEnterScope"
                handlerReactionTypeName("Loading", enter) shouldBe "LoadingEnterReaction"
                handlerTransitionsTypeName("Loading", enter) shouldBe "LoadingEnterTransitions"
            }

            "action handler はアクション simpleName が trigger になる" {
                val reload = action("LceAction.Reload")
                handlerScopeTypeName("Content", reload) shouldBe "ContentReloadScope"
                handlerReactionTypeName("Content", reload) shouldBe "ContentReloadReaction"
                handlerTransitionsTypeName("Content", reload) shouldBe "ContentReloadTransitions"
            }

            "入れ子 leaf の action handler は path 連結 prefix と組み合わさる" {
                val loadMore = action("FeedAction.LoadMore")
                handlerReactionTypeName("StableIdle", loadMore) shouldBe "StableIdleLoadMoreReaction"
            }

            "root 直付き共有 action は {Root}{Trigger} 形になる" {
                val selectTab = action("TabsAction.SelectTab")
                val prefix = generatedTypePrefix(root("TabsState"), StatePath.root)
                handlerScopeTypeName(prefix, selectTab) shouldBe "TabsStateSelectTabScope"
                handlerTransitionsTypeName(prefix, selectTab) shouldBe "TabsStateSelectTabTransitions"
            }

            "recover handler は Recover{ExceptionSimpleName} が trigger になる" {
                val recover =
                    RecoverHandler(
                        exception = TypeRef("example", "SessionExpiredException"),
                        transition = TransitionSpec.stayOnly,
                    )
                val prefix = generatedTypePrefix(root("AuthState"), StatePath.root)
                handlerScopeTypeName(prefix, recover) shouldBe "AuthStateRecoverSessionExpiredExceptionScope"
                handlerTransitionsTypeName(prefix, recover) shouldBe
                    "AuthStateRecoverSessionExpiredExceptionTransitions"
            }

            "exit handler は Scope のみ (Reaction / Transitions は生成対象外)" {
                handlerScopeTypeName("Authenticating", ExitHandler()) shouldBe "AuthenticatingExitScope"
            }
        }

        "per-node 型名 (samples.md 準拠)" - {
            "Impl / Handlers" {
                implTypeName("Loading") shouldBe "LoadingImpl"
                handlersTypeName("Loading") shouldBe "LoadingHandlers"
            }

            "中間 node の GroupHandlers" {
                groupHandlersTypeName("Stable") shouldBe "StableGroupHandlers"
            }

            "default ブロックの Handlers" {
                defaultHandlersTypeName("TabsState", "default") shouldBe "TabsStateDefaultHandlers"
            }

            "@DefaultName 由来の default 名も capitalize して連結する" {
                defaultHandlersTypeName("StableRefresh", "refreshCommon") shouldBe
                    "StableRefreshRefreshCommonHandlers"
            }

            "bundle scope 名は束の型名 + Scope (leaf / group / default / 合成のすべてで同型)" {
                bundleScopeTypeName(handlersTypeName("Loading")) shouldBe "LoadingHandlersScope"
                bundleScopeTypeName(groupHandlersTypeName("Stable")) shouldBe "StableGroupHandlersScope"
                bundleScopeTypeName(defaultHandlersTypeName("TabsState", "default")) shouldBe
                    "TabsStateDefaultHandlersScope"
            }

            "builder 形式の builder 型名 (leaf / default / group)" {
                actionsBuilderTypeName("Content") shouldBe "ContentActionsBuilder"
                defaultActionsBuilderTypeName("TabsState", "default") shouldBe "TabsStateDefaultActionsBuilder"
                defaultActionsBuilderTypeName("Refresh", "refreshCommon") shouldBe "RefreshRefreshCommonActionsBuilder"
                groupBuilderTypeName("Stable") shouldBe "StableGroupBuilder"
            }

            "states() escape scope の型名 (root / group)" {
                statesConfigureScopeTypeName(generatedTypePrefix(root("FeedState"), StatePath.root)) shouldBe
                    "FeedStateStatesConfigureScope"
                statesConfigureScopeTypeName("Stable") shouldBe "StableStatesConfigureScope"
            }
        }

        "stateTypeReference は宣言 package 内から見たソース参照を返す" {
            stateTypeReference(root("LceState"), StatePath("Loading")) shouldBe "LceState.Loading"
            stateTypeReference(root("FeedState"), StatePath("Stable", "Idle")) shouldBe "FeedState.Stable.Idle"
            stateTypeReference(root("LceState"), StatePath.root) shouldBe "LceState"
        }
    })
