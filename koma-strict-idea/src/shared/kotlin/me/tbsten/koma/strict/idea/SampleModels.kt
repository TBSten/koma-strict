package me.tbsten.koma.strict.idea

import me.tbsten.koma.strict.idea.model.ActionTrigger
import me.tbsten.koma.strict.idea.model.EnterTrigger
import me.tbsten.koma.strict.idea.model.ExitInfo
import me.tbsten.koma.strict.idea.model.GroupState
import me.tbsten.koma.strict.idea.model.LeafState
import me.tbsten.koma.strict.idea.model.Reachability
import me.tbsten.koma.strict.idea.model.RecoverTrigger
import me.tbsten.koma.strict.idea.model.RootState
import me.tbsten.koma.strict.idea.model.StateId
import me.tbsten.koma.strict.idea.model.StoreDiagramModel

/**
 * Hand-built slim models mirroring doc/internal/samples.md. Lives in `src/shared` so it is the single
 * source of truth for both the pure (non-platform) lowering / layout / reachability tests and the
 * headless `renderComposeScene` preview, which renders these exact models (no PSI / IDE fixture).
 */
object SampleModels {

    private fun id(vararg s: String) = StateId(*s)

    private fun modelOf(root: RootState, initial: List<StateId>): StoreDiagramModel =
        StoreDiagramModel(
            root = root,
            initial = initial,
            reachableLeafIds = Reachability.compute(root, initial),
        )

    /** Case 1: basic LCE — Loading -> (Content | Error), Content/Error -> Loading. */
    fun lce(): StoreDiagramModel {
        val root = RootState(
            simpleName = "LceState",
            children = listOf(
                LeafState(
                    simpleName = "Loading",
                    id = id("Loading"),
                    enter = EnterTrigger(targets = listOf(id("Content"), id("Error")), emits = listOf("LoadFailed")),
                ),
                LeafState(
                    simpleName = "Content",
                    id = id("Content"),
                    actions = listOf(ActionTrigger("Reload", targets = listOf(id("Loading")))),
                ),
                LeafState(
                    simpleName = "Error",
                    id = id("Error"),
                    actions = listOf(ActionTrigger("Retry", targets = listOf(id("Loading")))),
                ),
            ),
        )
        return modelOf(root, initial = listOf(id("Loading")))
    }

    /**
     * A store with an unreachable leaf (`Broken`) — never `initial` and no transition targets it —
     * to eyeball the warning color in both the figure and the transition table (`ide.md` reachability).
     */
    fun unreachable(): StoreDiagramModel {
        val root = RootState(
            simpleName = "GateState",
            children = listOf(
                LeafState("Open", id("Open"), actions = listOf(ActionTrigger("Close", targets = listOf(id("Closed"))))),
                LeafState("Closed", id("Closed"), actions = listOf(ActionTrigger("Open", targets = listOf(id("Open"))))),
                // 到達不能だが自身は遷移を持つ leaf: 図でも表でも警告色になることを確認する。
                LeafState("Broken", id("Broken"), actions = listOf(ActionTrigger("Reset", targets = listOf(id("Open"))))),
            ),
        )
        return modelOf(root, initial = listOf(id("Open")))
    }

    /**
     * A linear chain of [count] leaves (`S0 -> S1 -> ... -> S{count-1}`), each with a `Next` action to
     * the following state. Used to exceed the canvas extent cap on purpose (`ide-review.md` P1-08): at
     * the UI layer gap a long enough LR chain lays out wider than the cap, so the diagram must auto-fit
     * instead of silently clipping its tail. Every leaf is reachable (chained from the initial `S0`), so
     * the fixture doesn't drag in unrelated unreachable-warning coloring.
     */
    fun longChain(count: Int): StoreDiagramModel {
        val children = (0 until count).map { i ->
            val name = "S$i"
            if (i < count - 1) {
                LeafState(name, id(name), actions = listOf(ActionTrigger("Next", targets = listOf(id("S${i + 1}")))))
            } else {
                LeafState(name, id(name))
            }
        }
        val root = RootState(simpleName = "ChainState", children = children)
        return modelOf(root, initial = listOf(id("S0")))
    }

    /** Case 3: tabs — root shared SelectTab (any-state) + a self-transition on Search. */
    fun tabs(): StoreDiagramModel {
        val root = RootState(
            simpleName = "TabsState",
            children = listOf(
                LeafState("Home", id("Home")),
                LeafState(
                    "Search",
                    id("Search"),
                    actions = listOf(ActionTrigger("UpdateQuery", targets = listOf(id("Search")))),
                ),
                LeafState("Profile", id("Profile")),
            ),
            actions = listOf(
                ActionTrigger(
                    "SelectTab",
                    targets = listOf(id("Home"), id("Search"), id("Profile")),
                    stay = true,
                ),
            ),
        )
        return modelOf(root, initial = listOf(id("Home")))
    }

    /** Case 2: feed — intermediate sealed `Stable` group with a conditional (Stay) transition. */
    fun feed(): StoreDiagramModel {
        val stable = GroupState(
            simpleName = "Stable",
            id = id("Stable"),
            children = listOf(
                LeafState(
                    "Idle",
                    id("Stable", "Idle"),
                    actions = listOf(
                        ActionTrigger("Refresh", targets = listOf(id("Stable", "Refreshing"))),
                        ActionTrigger("LoadMore", targets = listOf(id("Stable", "LoadingMore")), stay = true),
                    ),
                ),
                LeafState(
                    "Refreshing",
                    id("Stable", "Refreshing"),
                    enter = EnterTrigger(targets = listOf(id("Stable", "Idle")), emits = listOf("RefreshFailed")),
                ),
                LeafState(
                    "LoadingMore",
                    id("Stable", "LoadingMore"),
                    enter = EnterTrigger(targets = listOf(id("Stable", "Idle")), emits = listOf("LoadMoreFailed")),
                ),
            ),
        )
        val root = RootState(
            simpleName = "FeedState",
            children = listOf(
                LeafState(
                    "Loading",
                    id("Loading"),
                    enter = EnterTrigger(
                        targets = listOf(id("Stable", "Idle"), id("Error")),
                        emits = listOf("LoadFailed"),
                    ),
                ),
                stable,
                LeafState(
                    "Error",
                    id("Error"),
                    actions = listOf(ActionTrigger("Retry", targets = listOf(id("Loading")))),
                ),
            ),
        )
        return modelOf(root, initial = listOf(id("Loading")))
    }

    /**
     * Regression fixture for the "collapsed evictees" bug: [feed] plus a sibling `Test` leaf that
     * `Loading` also branches to. `Test`, `Error`, and the `Stable` group's entry all land in the same
     * BFS layer/column, so the composite push-out drops `Error` below the box — right where `Test`
     * already sits. The layout must un-stack them (see `separateOverlaps`) instead of rendering the two
     * boxes on top of each other. Also guards the nested-box top-edge clipping (see `normalizeAll`).
     */
    fun feedBranch(): StoreDiagramModel {
        val stable = GroupState(
            simpleName = "Stable",
            id = id("Stable"),
            children = listOf(
                LeafState(
                    "Idle",
                    id("Stable", "Idle"),
                    actions = listOf(
                        ActionTrigger("Refresh", targets = listOf(id("Stable", "Refreshing"))),
                        ActionTrigger("LoadMore", targets = listOf(id("Stable", "LoadingMore")), stay = true),
                    ),
                ),
                LeafState(
                    "Refreshing",
                    id("Stable", "Refreshing"),
                    enter = EnterTrigger(targets = listOf(id("Stable", "Idle")), emits = listOf("RefreshFailed")),
                ),
                LeafState(
                    "LoadingMore",
                    id("Stable", "LoadingMore"),
                    enter = EnterTrigger(targets = listOf(id("Stable", "Idle")), emits = listOf("LoadMoreFailed")),
                ),
            ),
        )
        val root = RootState(
            simpleName = "FeedState",
            children = listOf(
                LeafState(
                    "Loading",
                    id("Loading"),
                    // Idle・Error に加え Test へも分岐 = 同一 layer に root leaf が 3 つ並ぶ。
                    enter = EnterTrigger(
                        targets = listOf(id("Stable", "Idle"), id("Error"), id("Test")),
                        emits = listOf("LoadFailed"),
                    ),
                ),
                stable,
                LeafState(
                    "Error",
                    id("Error"),
                    actions = listOf(ActionTrigger("Retry", targets = listOf(id("Loading")))),
                ),
                // Loading から到達する追加の root leaf。Loading へ戻る対向線 = 長い斜めの往復帯になる。
                LeafState(
                    "Test",
                    id("Test"),
                    actions = listOf(ActionTrigger("Restart", targets = listOf(id("Loading")))),
                ),
            ),
        )
        return modelOf(root, initial = listOf(id("Loading")))
    }

    /**
     * Long state names to eyeball node-label wrapping + font autosize (枠内改行 → それでも入らねば縮小).
     * Names deliberately exceed the fixed node width so the renderer must wrap at camelCase boundaries
     * and shrink the font when a name is too long to fit even wrapped.
     */
    fun longNames(): StoreDiagramModel {
        val root = RootState(
            simpleName = "OnboardingState",
            children = listOf(
                LeafState(
                    "CollectingUserProfileInformation",
                    id("CollectingUserProfileInformation"),
                    actions = listOf(
                        ActionTrigger("Continue", targets = listOf(id("AuthenticatingWithBiometricsFallback"))),
                    ),
                ),
                LeafState(
                    "AuthenticatingWithBiometricsFallback",
                    id("AuthenticatingWithBiometricsFallback"),
                    actions = listOf(
                        ActionTrigger("Back", targets = listOf(id("CollectingUserProfileInformation"))),
                    ),
                ),
                LeafState("Done", id("Done")),
            ),
        )
        return modelOf(root, initial = listOf(id("CollectingUserProfileInformation")))
    }

    /**
     * Case 4: wizard — a linear step flow with `Back`. A validation failure is a stay + emit (`Next`
     * keeps the state and only notifies through an event); `InputName` is a self-transition (rebuilds
     * the same state). `Done` is a terminal leaf with no triggers. Mirrors doc/internal/samples.md §4;
     * exercises self-transitions, conditional stay + emit, and a trigger-less terminal leaf.
     */
    fun wizard(): StoreDiagramModel {
        val root = RootState(
            simpleName = "WizardState",
            children = listOf(
                LeafState(
                    "Step1",
                    id("Step1"),
                    actions = listOf(
                        // 自己遷移 (state を作り直す。stay とは別物)。
                        ActionTrigger("InputName", targets = listOf(id("Step1"))),
                        // 検証 NG は stay + emit、OK なら Step2。
                        ActionTrigger("Next", targets = listOf(id("Step2")), stay = true, emits = listOf("ValidationFailed")),
                    ),
                ),
                LeafState(
                    "Step2",
                    id("Step2"),
                    actions = listOf(
                        ActionTrigger("Next", targets = listOf(id("Step3")), stay = true, emits = listOf("ValidationFailed")),
                        ActionTrigger("Back", targets = listOf(id("Step1"))),
                    ),
                ),
                LeafState(
                    "Step3",
                    id("Step3"),
                    actions = listOf(
                        ActionTrigger("Submit", targets = listOf(id("Submitting"))),
                        ActionTrigger("Back", targets = listOf(id("Step2"))),
                    ),
                ),
                LeafState(
                    "Submitting",
                    id("Submitting"),
                    enter = EnterTrigger(targets = listOf(id("Done"), id("Step3")), emits = listOf("SubmitFailed")),
                ),
                // 宣言ゼロの終端 leaf (トリガ無し)。
                LeafState("Done", id("Done")),
            ),
        )
        return modelOf(root, initial = listOf(id("Step1")))
    }

    /**
     * Regression fixture for P1-05 (multiple self-loops on one node). `Relay` declares three different
     * self-loops at once — a self re-entering `@OnEnter`, a self `@OnAction` (`Poke`), and a self
     * `@OnRecover` — plus a normal `Finish` edge to `Done`. The renderer must fan the three self-loops
     * onto separate faces (top / bottom / right) so they don't collapse into one identical arc with a
     * single visible label. Exercises mixed ENTER / ACTION / RECOVER self-loops on a single node.
     */
    fun selfLoops(): StoreDiagramModel {
        val root = RootState(
            simpleName = "RelayState",
            children = listOf(
                LeafState(
                    "Relay",
                    id("Relay"),
                    // 自分自身を再 enter する onEnter (ENTER self-loop)。
                    enter = EnterTrigger(targets = listOf(id("Relay")), emits = listOf("Reentered")),
                    actions = listOf(
                        // 自己遷移アクション (ACTION self-loop) と、通常の Done への遷移。
                        ActionTrigger("Poke", targets = listOf(id("Relay"))),
                        ActionTrigger("Finish", targets = listOf(id("Done"))),
                    ),
                    // 自ノードへ戻る recover (RECOVER self-loop、破線)。
                    recovers = listOf(
                        RecoverTrigger("TimeoutException", targets = listOf(id("Relay")), emits = listOf("Recovered")),
                    ),
                ),
                LeafState("Done", id("Done")),
            ),
        )
        return modelOf(root, initial = listOf(id("Relay")))
    }

    /**
     * Regression fixture for P1-04 (typed [me.tbsten.koma.strict.idea.ir.NodeId]). A root leaf is
     * literally named `any`, and the root also declares a shared action — so the diagram holds a
     * concrete `State(any)` leaf *and* the root any-state pseudo node at the same time. A group
     * (`Section`) nests a second leaf named `any` and declares its own shared action, adding a scoped
     * any-state. Under the old String id every one of these collapsed onto the key `"any"` /
     * `"any:Section"` and the collision crashed the layout's layering loop; with typed ids each stays
     * a distinct key. The model must lower + lay out without throwing and keep both the `any` leaves
     * and the any-state pseudo nodes.
     */
    fun anyNamed(): StoreDiagramModel {
        val section = GroupState(
            simpleName = "Section",
            id = id("Section"),
            children = listOf(
                // group 内にも "any" という名の leaf (path = Section.any)。
                LeafState(
                    "any",
                    id("Section", "any"),
                    actions = listOf(ActionTrigger("Leave", targets = listOf(id("Home")))),
                ),
            ),
            // group 共有アクション: any:Section 擬似ノードを生む (scoped any)。
            actions = listOf(ActionTrigger("Reset", targets = listOf(id("Home")))),
        )
        val root = RootState(
            simpleName = "AnyState",
            children = listOf(
                LeafState(
                    "Home",
                    id("Home"),
                    actions = listOf(ActionTrigger("Open", targets = listOf(id("any")))),
                ),
                // root 直下に "any" という名の leaf (path = any)。root any-state 擬似ノードと同名で
                // 衝突していた張本人。自己遷移 + Section.any への遷移を持つ。
                LeafState(
                    "any",
                    id("any"),
                    actions = listOf(
                        ActionTrigger("Refresh", targets = listOf(id("any"))),
                        ActionTrigger("Enter", targets = listOf(id("Section", "any"))),
                    ),
                ),
                section,
            ),
            // root 共有アクション: root any-state 擬似ノード (display "any") を生む。
            actions = listOf(ActionTrigger("Sync", targets = listOf(id("Home")), stay = true)),
        )
        return modelOf(root, initial = listOf(id("Home")))
    }

    /**
     * Case 5: auth — a root-shared `@OnRecover<SessionExpiredException>` (any-state -> LoggedOut,
     * drawn as a dashed recover edge) plus an `@OnExit` on `Authenticating` (emit-only, drawn as a
     * badge). Mirrors doc/internal/samples.md §5; the canonical recover / exit rendering fixture.
     */
    fun auth(): StoreDiagramModel {
        val root = RootState(
            simpleName = "AuthState",
            children = listOf(
                LeafState(
                    "CheckingSession",
                    id("CheckingSession"),
                    enter = EnterTrigger(targets = listOf(id("LoggedIn"), id("LoggedOut"))),
                ),
                LeafState(
                    "LoggedOut",
                    id("LoggedOut"),
                    actions = listOf(ActionTrigger("Login", targets = listOf(id("Authenticating")))),
                ),
                LeafState(
                    "Authenticating",
                    id("Authenticating"),
                    enter = EnterTrigger(
                        targets = listOf(id("LoggedIn"), id("LoggedOut")),
                        emits = listOf("LoginFailed"),
                    ),
                    // 遷移能力なし・emit のみ = ノード脇の exit バッジになる。
                    exit = ExitInfo(emits = listOf("AuthAttemptFinished")),
                ),
                LeafState(
                    "LoggedIn",
                    id("LoggedIn"),
                    actions = listOf(ActionTrigger("Logout", targets = listOf(id("LoggedOut")))),
                ),
            ),
            // root 共有 recover: どの state でも SessionExpiredException が飛べば LoggedOut へ + 通知。
            recovers = listOf(
                RecoverTrigger(
                    exceptionName = "SessionExpiredException",
                    targets = listOf(id("LoggedOut")),
                    emits = listOf("SessionExpired"),
                ),
            ),
        )
        return modelOf(root, initial = listOf(id("CheckingSession")))
    }

    /**
     * Case 6: settings — two-level nested sealed (`Loaded > General > {Viewing, Editing}` + a
     * `Loaded.Account` sibling) with a group-shared `Close` action on `Loaded` (an `any Loaded`
     * pseudo node nested inside the composite). Exercises nested composite boxes, the group-scoped
     * any-state form, and the non-member push-out that keeps each box wrapping only its own members.
     */
    fun settings(): StoreDiagramModel {
        val general = GroupState(
            simpleName = "General",
            id = id("Loaded", "General"),
            children = listOf(
                LeafState(
                    "Viewing",
                    id("Loaded", "General", "Viewing"),
                    actions = listOf(
                        ActionTrigger("Edit", targets = listOf(id("Loaded", "General", "Editing"))),
                        ActionTrigger("OpenAccount", targets = listOf(id("Loaded", "Account"))),
                    ),
                ),
                LeafState(
                    "Editing",
                    id("Loaded", "General", "Editing"),
                    actions = listOf(
                        ActionTrigger("Save", targets = listOf(id("Loaded", "General", "Viewing")), stay = true),
                    ),
                ),
            ),
        )
        val loaded = GroupState(
            simpleName = "Loaded",
            id = id("Loaded"),
            children = listOf(
                general,
                LeafState(
                    "Account",
                    id("Loaded", "Account"),
                    actions = listOf(ActionTrigger("Back", targets = listOf(id("Loaded", "General", "Viewing")))),
                ),
            ),
            // group 共有アクション: any Loaded から Loading へ (composite 内の擬似ノード)。
            actions = listOf(ActionTrigger("Close", targets = listOf(id("Loading")))),
        )
        val root = RootState(
            simpleName = "SettingsState",
            children = listOf(
                LeafState(
                    "Loading",
                    id("Loading"),
                    enter = EnterTrigger(targets = listOf(id("Loaded", "General", "Viewing"))),
                ),
                loaded,
            ),
        )
        return modelOf(root, initial = listOf(id("Loading")))
    }

    /**
     * Case 7: session — a leaf (`SignedOut`) whose `SignIn` transition targets an intermediate sealed
     * *group* (`SignedIn`) rather than a concrete leaf. koma's `nextState` is leaf-only, but the tool
     * window analyzes arbitrary code, so the group target is kept and drawn as an edge landing on the
     * `SignedIn` composite box border (UML composite-state entry) instead of being dropped. Every leaf
     * stays reachable, so the box members render without the unreachable warning color — the fixture
     * isolates exactly the group-edge rendering.
     */
    fun session(): StoreDiagramModel {
        val signedIn = GroupState(
            simpleName = "SignedIn",
            id = id("SignedIn"),
            children = listOf(
                LeafState(
                    "Home",
                    id("SignedIn", "Home"),
                    actions = listOf(ActionTrigger("OpenSettings", targets = listOf(id("SignedIn", "Settings")))),
                ),
                LeafState(
                    "Settings",
                    id("SignedIn", "Settings"),
                    actions = listOf(
                        ActionTrigger("Back", targets = listOf(id("SignedIn", "Home"))),
                        ActionTrigger("SignOut", targets = listOf(id("SignedOut"))),
                    ),
                ),
            ),
        )
        val root = RootState(
            simpleName = "AppState",
            children = listOf(
                signedIn,
                LeafState(
                    "SignedOut",
                    id("SignedOut"),
                    // group を指す遷移: leaf ではなく中間 sealed (SignedIn) を target にする。
                    // 図では SignedIn の composite box 境界に矢印が刺さる。
                    actions = listOf(ActionTrigger("SignIn", targets = listOf(id("SignedIn")))),
                ),
            ),
        )
        return modelOf(root, initial = listOf(id("SignedIn", "Home")))
    }
}
