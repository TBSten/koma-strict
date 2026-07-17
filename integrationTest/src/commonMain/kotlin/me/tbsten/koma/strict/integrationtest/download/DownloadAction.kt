package me.tbsten.koma.strict.integrationtest.download

import koma.core.Action

/** Actions of the download sample (see [DownloadState]). */
sealed interface DownloadAction : Action {
    data class Start(val url: String) : DownloadAction

    data object Pause : DownloadAction

    data object Resume : DownloadAction

    data object Cancel : DownloadAction
}
