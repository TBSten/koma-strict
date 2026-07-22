package me.tbsten.koma.strict.idea

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import me.tbsten.koma.strict.idea.frontend.PsiSourceAnchor
import me.tbsten.koma.strict.idea.frontend.StoreSpecModelBuilder
import me.tbsten.koma.strict.idea.model.SourceAnchor
import me.tbsten.koma.strict.idea.model.StoreDiagramModel
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.atomic.AtomicLong

/**
 * Drives the live state diagram behind the tool window (`ide.md` "ライブ更新が存在意義").
 *
 * It follows the editor via [FileEditorManagerListener.selectionChanged], debounces churn through a
 * 200 ms [MergingUpdateQueue], and analyzes the selected Kotlin file off the EDT with
 * `ReadAction.nonBlocking(...).coalesceBy(...).finishOnUiThread(...)`. The result is published into
 * [stores] — a Compose snapshot state the tool-window content reads, so recomposition happens
 * automatically when analysis finishes. A `(fileUrl, psiModStamp)` cache skips re-analysis when the
 * selection flips back to an already-analyzed, unchanged file.
 *
 * All wiring (message-bus connection + queue + pending read actions) is scoped to the supplied
 * [Disposable] (the tool window's), so it is released when the tool window closes.
 */
internal class KomaStrictToolWindowController(
    private val project: Project,
    private val parentDisposable: Disposable,
) {
    /** The stores of the currently selected file. Empty = show the setup guidance. */
    var stores: List<StoreDiagramModel> by mutableStateOf(emptyList())
        private set

    /**
     * True while the project is indexing (dumb mode). The content then shows an "indexing" screen
     * instead of a half-resolved diagram (annotations can't resolve until indexes are ready).
     */
    var indexing: Boolean by mutableStateOf(false)
        private set

    // updates flush on the EDT (SWING_THREAD alarm), so mutating [stores] from here is EDT-safe.
    private val queue = MergingUpdateQueue(
        "KomaStrictAnalysis",
        /* mergingTimeSpan = */ 200,
        /* isActive = */ true,
        MergingUpdateQueue.ANY_COMPONENT,
        parentDisposable,
    )

    // 直近に解析した (fileUrl, PSI modStamp)。同一なら再解析を省く。BG スレッドから読むので @Volatile。
    @Volatile
    private var lastKey: Key? = null

    // refresh のたびに +1。in-flight 解析はこの世代を capture し、apply 時に最新でなければ破棄する。
    // これで「全ファイルを閉じた (null 選択)」直後に前ファイルの in-flight 結果が遅れて上書きするのを防ぐ
    // (null 分岐は nonBlocking を submit しないため coalesceBy では止められない)。
    private val generation = AtomicLong(0)

    private data class Key(val url: String, val stamp: Long)

    private sealed interface Outcome
    private object Unchanged : Outcome
    // dumb mode (index 未完) で解析できなかった。degraded とは区別し「indexing」画面を出す。
    private object DumbMode : Outcome
    private data class Loaded(val key: Key?, val stores: List<StoreDiagramModel>, val degraded: Boolean) : Outcome

    /** Subscribes to editor selection and analyzes whatever file is open now. Call once on the EDT. */
    fun start() {
        val connection = project.messageBus.connect(parentDisposable)
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) = scheduleRefresh()
            },
        )
        // インデックス完了 (dumb -> smart) で再解析する。degraded 結果は cache しないので必ず再解析される。
        connection.subscribe(
            DumbService.DUMB_MODE,
            object : DumbService.DumbModeListener {
                override fun exitDumbMode() = scheduleRefresh()
            },
        )
        // 開いているファイルの編集追従 (ide.md「ライブ更新が存在意義」)。200ms デバウンス + modStamp
        // キャッシュがそのまま効くので、選択中ファイルの PSI 変更で scheduleRefresh するだけでよい。
        PsiManager.getInstance(project).addPsiTreeChangeListener(
            object : PsiTreeChangeAdapter() {
                override fun childrenChanged(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childAdded(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childRemoved(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childReplaced(event: PsiTreeChangeEvent) = onPsiChange(event)
                override fun childMoved(event: PsiTreeChangeEvent) = onPsiChange(event)
            },
            parentDisposable,
        )
        scheduleRefresh()
    }

    /** Forces a re-analysis of the current file (the reload button), bypassing the (url, modStamp) cache. */
    fun reload() {
        lastKey = null
        scheduleRefresh()
    }

    // 選択中ファイルの PSI が変わったときだけ再解析をスケジュールする。
    private fun onPsiChange(event: PsiTreeChangeEvent) {
        val changed = event.file?.virtualFile ?: return
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return
        if (changed == selected) scheduleRefresh()
    }

    // 同一 identity で merge されるので runnable は選択ファイルを capture しない。
    // フラッシュ時に常に「今」の選択を読み直すことで、連続切替でも最新ファイルを解析する。
    private fun scheduleRefresh() {
        queue.queue(Update.create(REFRESH_ID) { refresh() })
    }

    // MergingUpdateQueue のフラッシュ (EDT) から呼ばれる。
    private fun refresh() {
        val gen = generation.incrementAndGet()
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (file == null) {
            lastKey = null
            indexing = false
            stores = emptyList()
            return
        }
        // index 中は注釈が解決できず正しい図を出せない。doomed な解析を投げず indexing 画面を出す。
        // 完了時は DumbService の exitDumbMode -> scheduleRefresh で自動的に再解析される。
        if (DumbService.getInstance(project).isDumb) {
            lastKey = null
            indexing = true
            return
        }
        ReadAction.nonBlocking<Outcome> { compute(file) }
            .coalesceBy(this)
            .expireWith(parentDisposable)
            .finishOnUiThread(ModalityState.any()) { outcome -> apply(gen, outcome) }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    // BG スレッドの read action 内。ここで AA 解析まで済ませる (allowAnalysisOnEdt 不要)。
    private fun compute(file: VirtualFile): Outcome {
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile
            ?: return Loaded(key = null, stores = emptyList(), degraded = false)
        val key = Key(file.url, ktFile.modificationStamp)
        if (key == lastKey) return Unchanged
        // 解析開始後に dumb mode へ入ると build() が IndexNotReadyException を投げる。
        // degraded 化せず DumbMode として扱い、indexing 画面 + 完了後の再解析に回す。
        return try {
            val models = StoreSpecModelBuilder.findStoreSpecClasses(ktFile)
                .map { StoreSpecModelBuilder.build(it) }
            Loaded(key, models, degraded = models.any { it.degraded })
        } catch (e: IndexNotReadyException) {
            DumbMode
        }
    }

    private fun apply(gen: Long, outcome: Outcome) {
        // 後発の refresh が既に走っている (= この結果は stale) なら破棄する。
        if (gen != generation.get()) return
        when (outcome) {
            Unchanged -> Unit
            DumbMode -> {
                // index 未完。cache せず indexing 画面へ (exitDumbMode で再解析される)。
                lastKey = null
                indexing = true
            }
            is Loaded -> {
                // degraded 結果 (解析未完) は modStamp cache に焼き付けない。
                // 焼き付けると smart 復帰後も同一 modStamp で Unchanged になり degraded 表示に固着する。
                lastKey = if (outcome.degraded) null else outcome.key
                indexing = false
                stores = outcome.stores
            }
        }
    }

    /**
     * Jumps to a state declaration from a diagram node / transition-table row click (`ide.md` v1
     * click-to-declaration). Resolves the [PsiSourceAnchor]'s smart pointer and navigates via the
     * PSI element's `Navigatable` (the target `NavigationUtil` ultimately drives). Must run on the EDT.
     */
    fun navigate(anchor: SourceAnchor) {
        // PSI 解決 (SmartPsiElementPointer.element / canNavigate) は read action 内で行い、
        // 実際の navigate(true) だけを read action の外 (EDT) で呼ぶ (261 系の堅牢パターン)。
        ReadAction
            .computeBlocking<KtClassOrObject?, RuntimeException> {
                (anchor as? PsiSourceAnchor)?.declaration?.takeIf { it.canNavigate() }
            }?.navigate(true)
    }

    private companion object {
        const val REFRESH_ID = "koma-strict-refresh"
    }
}
