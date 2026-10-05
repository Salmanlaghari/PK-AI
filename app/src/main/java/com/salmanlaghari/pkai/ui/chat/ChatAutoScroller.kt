package com.salmanlaghari.pkai.ui.chat

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Smart auto-scroll shared by every chat list (Super Chat + Home).
 *
 * Follows new messages only when the list was already near the bottom — or
 * the user just sent a message — so reading back through history never yanks
 * the scroll position.
 */
class ChatAutoScroller {

    private var lastSeenMessageCount = -1

    /**
     * Call BEFORE submitting the new list.
     *
     * @param oldItemCount the adapter's current item count (before submit).
     * @param newItemCount size of the list about to be submitted.
     * @param messageCount number of chat messages (excluding the typing indicator).
     * @param lastMessageIsUser whether the newest message is the user's own.
     * @return true when the list should smooth-scroll to the last item after
     * the submit commits.
     */
    fun shouldScrollToBottom(
        recyclerView: RecyclerView,
        oldItemCount: Int,
        newItemCount: Int,
        messageCount: Int,
        lastMessageIsUser: Boolean
    ): Boolean {
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val lastVisible = lm?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION
        val wasNearBottom = oldItemCount == 0 ||
            (lastVisible != RecyclerView.NO_POSITION && lastVisible >= oldItemCount - 2)
        val firstSubmission = lastSeenMessageCount == -1
        val userJustSent = lastMessageIsUser && messageCount != lastSeenMessageCount
        lastSeenMessageCount = messageCount
        return newItemCount > 0 && (firstSubmission || wasNearBottom || userJustSent)
    }

    fun reset() {
        lastSeenMessageCount = -1
    }
}
