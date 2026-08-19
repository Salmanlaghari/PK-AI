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
         * Pollinations AI — a genuinely key-less hosted LLM.
         *
         * Anonymous requests to `text.pollinations.ai` require no token and are explicitly
         * excluded from Pollinations' authenticated-user deprecation, so this gives the Free
         * tab a real large-language-model conversation instead of canned public-API replies.
         */
        val POLLINATIONS = FreeAiModel(
            id = "pollinations",
            displayName = "Pollinations AI",
            tagline = "Real LLM chat, no API key",
            logoEmoji = "🌸"
        )

        val ALL: List<FreeAiModel> = listOf(PUBLIC_CHATBOT, POLLINATIONS)

        /** Pollinations is the default so the Free tab feels like a real assistant. */
        val DEFAULT: FreeAiModel = POLLINATIONS

        fun fromId(id: String): FreeAiModel = ALL.firstOrNull { it.id == id } ?: DEFAULT

        /** True when a stored `modelUsed` value belongs to the Free AI tier. */
        fun isFreeLabel(modelUsed: String?): Boolean =
            modelUsed != null && modelUsed.startsWith("Free")
    }
}
