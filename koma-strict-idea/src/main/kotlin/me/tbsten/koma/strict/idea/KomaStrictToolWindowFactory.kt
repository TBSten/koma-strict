package me.tbsten.koma.strict.idea

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.tbsten.koma.strict.idea.ui.KomaStrictToolWindowContent
import org.jetbrains.jewel.bridge.addComposeTab

/**
 * Registers the "Koma Strict" tool window and hosts its Compose (Jewel) content.
 *
 * The window is backed by [addComposeTab] (Jewel's ideLafBridge), which installs a Swing-hosted
 * `ComposePanel` themed to the current IDE Look-and-Feel. The tab renders the shared
 * [KomaStrictToolWindowContent] — the same composable the headless `renderComposeScene` preview
 * uses — so the visual dev loop and the real plugin stay in sync.
 *
 * A per-window [KomaStrictToolWindowController] drives the live model: it follows the editor
 * selection, analyzes the file off the EDT via the Analysis API, and publishes the result into a
 * Compose snapshot state that the content reads. Node / row clicks route back through the controller
 * to navigate to the state declaration. The controller is scoped to [ToolWindow.getDisposable].
 *
 * Implements [DumbAware] so the tool window stays available during indexing.
 */
internal class KomaStrictToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val controller = KomaStrictToolWindowController(project, toolWindow.disposable)
        toolWindow.addComposeTab("Koma Strict") {
            KomaStrictToolWindowContent(
                stores = controller.stores,
                onNavigate = controller::navigate,
                indexing = controller.indexing,
                onReload = controller::reload,
                onInsertFlowSpec = controller::insertFlowSpec,
                onGenerateTestFile = controller::generateTestFile,
                recording = controller.recording,
            )
        }
        controller.start()
    }
}
