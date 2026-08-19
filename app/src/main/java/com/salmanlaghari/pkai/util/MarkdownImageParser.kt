package com.salmanlaghari.pkai.util

/**
 * Splits an AI response into the plain-text portion and any markdown image embeds it contains.
 *
 * Matches the standard `![alt](source)` syntax where `source` is either a base64 data URI
 * (`data:image/…;base64,…`) or a regular `http(s)://` / `content://` link. The matched
 * markdown is stripped from the returned text so the bubble shows clean prose instead of the
 * raw image source.
 */
object MarkdownImageParser {

    private val IMAGE_PATTERN = Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)")

    data class MarkdownImage(val alt: String, val source: String)

    data class ParsedContent(val text: String, val images: List<MarkdownImage>)

    fun parse(content: String): ParsedContent {
        val images = IMAGE_PATTERN.findAll(content).mapNotNull { match ->
            val source = match.groupValues[2].trim()
            if (source.isBlank() || !isSupportedSource(source)) return@mapNotNull null
            MarkdownImage(alt = match.groupValues[1].trim(), source = source)
        }.toList()

        // Remove every matched image markdown span from the visible text.
        val text = if (images.isEmpty()) {
            content
        } else {
            IMAGE_PATTERN.replace(content) { "" }.trim()
        }

        return ParsedContent(text = text, images = images)
    }

    private fun isSupportedSource(source: String): Boolean {
        return source.startsWith("data:image/") ||
            source.startsWith("http://") ||
            source.startsWith("https://") ||
            source.startsWith("content://")
    }
}
