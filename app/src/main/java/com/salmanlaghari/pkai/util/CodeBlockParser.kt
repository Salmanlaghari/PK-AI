package com.salmanlaghari.pkai.util

import java.util.UUID

sealed class MessageSegment {
    data class Text(val content: String) : MessageSegment()
    data class CodeBlock(
        val id: String = UUID.randomUUID().toString(),
        val rawLanguage: String,
        val hackerEarthLang: String?,
        val code: String
    ) : MessageSegment()
}

object CodeBlockParser {

    private val CODE_BLOCK_REGEX = Regex("```([a-zA-Z0-9_+#-]*)\\n?([\\s\\S]*?)```")

    fun parseSegments(content: String): List<MessageSegment> {
        if (content.isBlank()) return emptyList()

        val segments = mutableListOf<MessageSegment>()
        var lastIndex = 0

        for (match in CODE_BLOCK_REGEX.findAll(content)) {
            val startIndex = match.range.first
            if (startIndex > lastIndex) {
                val textChunk = content.substring(lastIndex, startIndex)
                if (textChunk.isNotBlank()) {
                    segments.add(MessageSegment.Text(textChunk.trim()))
                }
            }

            val rawLang = match.groupValues[1].trim().lowercase()
            val code = match.groupValues[2].trimEnd()
            val heLang = mapToHackerEarthLanguage(rawLang)

            segments.add(
                MessageSegment.CodeBlock(
                    rawLanguage = if (rawLang.isNotBlank()) rawLang else "code",
                    hackerEarthLang = heLang,
                    code = code
                )
            )

            lastIndex = match.range.last + 1
        }

        if (lastIndex < content.length) {
            val remainingText = content.substring(lastIndex)
            if (remainingText.isNotBlank()) {
                segments.add(MessageSegment.Text(remainingText.trim()))
            }
        }

        return if (segments.isEmpty()) {
            listOf(MessageSegment.Text(content))
        } else {
            segments
        }
    }

    fun mapToHackerEarthLanguage(rawLanguage: String): String? {
        return when (rawLanguage.lowercase().trim()) {
            "python", "python3", "py" -> "PYTHON3_8"
            "java" -> "JAVA17"
            "cpp", "c++", "cxx", "cc" -> "CPP17"
            "c" -> "C"
            "javascript", "js", "node", "nodejs" -> "JAVASCRIPT_NODE"
            "kotlin", "kt" -> "KOTLIN"
            "go", "golang" -> "GO"
            "rust", "rs" -> "RUST"
            "csharp", "c#", "cs" -> "CSHARP"
            "php" -> "PHP"
            "ruby", "rb" -> "RUBY"
            "swift" -> "SWIFT"
            "typescript", "ts" -> "TYPESCRIPT"
            "perl" -> "PERL"
            "scala" -> "SCALA"
            "haskell", "hs" -> "HASKELL"
            "clojure" -> "CLOJURE"
            "pascal" -> "PASCAL"
            "r" -> "R"
            else -> null
        }
    }
}
