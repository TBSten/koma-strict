package me.tbsten.koma.strict.integrationtest.download

import koma.core.Event

/**
 * Events of the download sample (see [DownloadState]).
 *
 * [Canceled] is declared in the shared `@OnAction` emit whitelist; [ReturnedToIdle] is
 * emitted only by the raw koma block registered through the factory's `configuration`
 * escape hatch (raw koma `event()` bypasses the per-handler whitelist by design).
 */
sealed interface DownloadEvent : Event {
    data object Canceled : DownloadEvent

    data object ReturnedToIdle : DownloadEvent
}
