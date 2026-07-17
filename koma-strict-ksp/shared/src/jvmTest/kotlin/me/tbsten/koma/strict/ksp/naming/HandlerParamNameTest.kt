package me.tbsten.koma.strict.ksp.naming

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.koma.strict.ksp.model.ActionHandler
import me.tbsten.koma.strict.ksp.model.EnterHandler
import me.tbsten.koma.strict.ksp.model.ExitHandler
import me.tbsten.koma.strict.ksp.model.RecoverHandler
import me.tbsten.koma.strict.ksp.model.TransitionSpec
import me.tbsten.koma.strict.ksp.model.TypeRef

/** handler / state param 名と予約 param 名 (default) の衝突判定の単体テスト。 */
internal class HandlerParamNameTest :
    FreeSpec({
        fun action(underPackageName: String): ActionHandler =
            ActionHandler(
                action = TypeRef("example", underPackageName),
                transition = TransitionSpec.stayOnly,
            )

        "handler param 名" - {
            "enter / exit は固定名になる" {
                handlerParamName(EnterHandler(transition = TransitionSpec.stayOnly)) shouldBe "enter"
                handlerParamName(ExitHandler()) shouldBe "exit"
            }

            "action はアクション simpleName の decapitalize になる" {
                handlerParamName(action("LceAction.Reload")) shouldBe "reload"
                handlerParamName(action("FeedAction.LoadMore")) shouldBe "loadMore"
                handlerParamName(action("MyAction.UpdateQuery")) shouldBe "updateQuery"
            }

            "recover は recover{ExceptionSimpleName} になる" {
                val recover =
                    RecoverHandler(
                        exception = TypeRef("example", "SessionExpiredException"),
                        transition = TransitionSpec.stayOnly,
                    )
                handlerParamName(recover) shouldBe "recoverSessionExpiredException"
            }
        }

        "builder member 名は named param 名と同一になる (action / recover)" {
            // 現状は handlerParamName の別名 (rename 案が採用されたらここが差分になる)
            builderHandlerMemberName(action("LceAction.Reload")) shouldBe "reload"
            builderHandlerMemberName(
                RecoverHandler(
                    exception = TypeRef("example", "SessionExpiredException"),
                    transition = TransitionSpec.stayOnly,
                ),
            ) shouldBe "recoverSessionExpiredException"
        }

        "state param 名は state 名の decapitalize になる" {
            stateParamName("Loading") shouldBe "loading"
            stateParamName("LoadingMore") shouldBe "loadingMore"
        }

        "予約 param 名との衝突判定" - {
            "decapitalize が default と一致する state は衝突する" {
                stateParamNameConflictsWithDefaultBlock("Default") shouldBe true
            }

            "通常の state 名は衝突しない" {
                stateParamNameConflictsWithDefaultBlock("Loading") shouldBe false
            }

            "@DefaultName で default 名を変えると衝突を回避できる" {
                stateParamNameConflictsWithDefaultBlock("Default", defaultName = "common") shouldBe false
                // 逆に @DefaultName と一致する state 名は衝突になる
                stateParamNameConflictsWithDefaultBlock("Common", defaultName = "common") shouldBe true
            }
        }
    })
