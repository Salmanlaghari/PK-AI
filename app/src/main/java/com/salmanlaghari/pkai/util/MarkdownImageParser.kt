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
        if (!content.contains("![")) {
            return ParsedContent(text = content, images = emptyList())
        }

        val images = mutableListOf<MarkdownImage>()
        val textBuilder = StringBuilder()
        var cursor = 0

        while (cursor < content.length) {
            val startIdx = content.indexOf("![", cursor)
            if (startIdx < 0) {
                textBuilder.append(content.substring(cursor))
                break
            }

            val altEndIdx = content.indexOf("](", startIdx + 2)
            if (altEndIdx < 0) {
                textBuilder.append(content.substring(cursor))
                break
            }

            val sourceEndIdx = content.indexOf(")", altEndIdx + 2)
            if (sourceEndIdx < 0) {
                textBuilder.append(content.substring(cursor))
                break
            }

            // Append preceding text
            if (startIdx > cursor) {
                textBuilder.append(content.substring(cursor, startIdx))
            }

            val alt = content.substring(startIdx + 2, altEndIdx).trim()
            val source = content.substring(altEndIdx + 2, sourceEndIdx).trim()

            if (isSupportedSource(source)) {
                images.add(MarkdownImage(alt = alt, source = source))
            } else {
                // If not a valid image source, preserve literal text
                textBuilder.append(content.substring(startIdx, sourceEndIdx + 1))
            }

            cursor = sourceEndIdx + 1
        }

        return ParsedContent(text = textBuilder.toString().trim(), images = images)
    }

    private fun isSupportedSource(source: String): Boolean {
        return source.startsWith("file://") ||
            source.startsWith("/") ||
            source.startsWith("http://") ||
            source.startsWith("https://") ||
            source.startsWith("content://") ||
            source.startsWith("data:image/")
    }
}
