package com.salmanlaghari.pkai.util

/**
 * Shared storage contract for song-result chat messages.
 *
 * Both producers ([com.salmanlaghari.pkai.ui.home.HomeViewModel],
 * [com.salmanlaghari.pkai.ui.superchat.SuperChatViewModel]) and the consumer
 * ([com.salmanlaghari.pkai.ui.superchat.SuperChatAdapter.SongCardHolder]) go
 * through this holder, so the attachment protocol is compiled rather than
 * commented:
 *
 * - `attachmentType == `[TYPE]
 * - `attachmentUri` holds the streamable audio URL (blank → browser fallback
 *   to [CardData.pageUrl])
 * - `attachmentName` holds the `|||`-packed payload built by [pack] and read
 *   by [unpack]
 *
 * Nothing here touches UI classes — ViewModels must not import the adapter
 * just for this constant (Kilo review, PR #101).
 */
object SongAttachment {

    /** [com.salmanlaghari.pkai.data.model.ChatMessage.attachmentType] for song cards. */
    const val TYPE = "song"

    /**
     * Packs song metadata into `attachmentName`. All four fields are
     * delimiter-sanitized so a remote-controlled value can never shift the
     * unpacked slots.
     */
    fun pack(song: SongSearchHelper.SongResult): String =
        SongSearchHelper.packSongAttachment(song)

    /** Unpacked song-card fields. */
    data class CardData(
        val title: String,
        val artist: String,
        val artworkUrl: String,
        val pageUrl: String,
        val source: String = "PagalWorld"
    )

    /**
     * Unpacks `attachmentName` into [CardData]. Never throws — missing slots
     * fall back to safe defaults.
     */
    fun unpack(attachmentName: String?): CardData {
        val parts = (attachmentName ?: "").split("|||")
        return CardData(
            title = parts.getOrElse(0) { "Unknown Song" }.ifBlank { "Unknown Song" },
            artist = parts.getOrElse(1) { "Unknown Artist" }.ifBlank { "Unknown Artist" },
            artworkUrl = parts.getOrElse(2) { "" },
            pageUrl = parts.getOrElse(3) { "" },
            source = parts.getOrElse(4) { "PagalWorld" }.ifBlank { "PagalWorld" }
        )
    }
}
