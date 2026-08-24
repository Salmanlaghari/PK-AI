package com.salmanlaghari.pkai.data.model

/**
 * The catalogue of **key-less** models offered in the Home screen's "Free AI" tab.
 *
 * These are deliberately distinct from the seven BYOK providers in [LlmProvider]: they need
 * no API key, no sign-up and no billing details, which is what makes the Free AI tier usable
 * straight after install.
 *
 * Every entry is labelled in chat history as `"$LABEL_PREFIX$displayName"` so the Free tab can
 * filter its own conversation without colliding with the premium providers.
 */
data class FreeAiModel(
    val id: String,
    val displayName: String,
    val tagline: String,
    val logoEmoji: String
) {
    /** The value stored in `ChatMessage.modelUsed` for this model. */
    val chatLabel: String get() = "$LABEL_PREFIX$displayName"

    companion object {
        /**
         * Prefix marking a message as belonging to the Free AI tier.
         *
         * NOTE: the legacy label `"Free Public AI"` also starts with "Free", so history saved by
         * older builds keeps showing up in the Free tab.
         */
        const val LABEL_PREFIX = "Free · "

        /** Public fact/advice chatbot — the original key-less tier. */
        val PUBLIC_CHATBOT = FreeAiModel(
            id = "public_chatbot",
            displayName = "PK AI Public Chatbot",
            tagline = "Facts & advice, zero setup",
            logoEmoji = "🌍"
        )

        /**
         * A real LLM that needs no API key.
         *
         * Backed by a key-less fallback chain (Pollinations' anonymous text endpoint, then
         * LLM7.io's anonymous chat endpoint). Both accept requests with no token, but each
         * one rate-limits or bills certain source IPs, so chaining them is what makes the
         * free tier dependable from any network. The name is deliberately provider-neutral
         * because the upstream that answers can vary.
         */
        val FREE_LLM = FreeAiModel(
            id = "free_llm",
            displayName = "PK AI Free LLM",
            tagline = "Real LLM chat · no API key",
            logoEmoji = "⚡"
        )

        /**
         * Ox Alpha — a free stealth reasoning model with 1M-token context.
         * Backed by oxalpha.com/api/chat, no API key or login required.
         */
        val OX_ALPHA = FreeAiModel(
            id = "ox_alpha",
            displayName = "Ox Alpha",
            tagline = "Stealth reasoning · 1M context · free",
            logoEmoji = "🧠"
        )

        /** Ox Alpha is first — it's the most capable free model and always available. */
        val ALL: List<FreeAiModel> = listOf(OX_ALPHA, FREE_LLM, PUBLIC_CHATBOT)

        /** Ox Alpha is the default so the Free tab uses the best free model first. */
        val DEFAULT: FreeAiModel = OX_ALPHA

        fun fromId(id: String): FreeAiModel = ALL.firstOrNull { it.id == id } ?: DEFAULT

        /** True when a stored `modelUsed` value belongs to the Free AI tier. */
        fun isFreeLabel(modelUsed: String?): Boolean =
            modelUsed != null && modelUsed.startsWith("Free")
    }
}
