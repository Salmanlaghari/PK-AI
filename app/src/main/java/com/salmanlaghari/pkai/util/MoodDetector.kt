package com.salmanlaghari.pkai.util

/**
 * Detects the emotional mood of a Super Chat message from its text.
 *
 * Supports English, Roman Urdu and Urdu script keywords. Detection is intentionally
 * simple keyword matching — fast, offline, and deterministic — and always falls back
 * to [Mood.NEUTRAL] when nothing matches.
 */
enum class Mood(val emoji: String, val label: String) {
    GREETING("👋", "Greeting"),
    HAPPY("😄", "Happy"),
    GRATEFUL("🥰", "Grateful"),
    SAD("😢", "Sad"),
    LOVE("😍", "Love"),
    FAREWELL("👋", "Farewell"),
    EXCITED("🤩", "Excited"),
    AGREE("👍", "Agree"),
    DISAGREE("🙅", "Disagree"),
    ANGRY("😠", "Angry"),
    THINKING("🤔", "Thinking"),
    NEUTRAL("😊", "Neutral")
}

object MoodDetector {

    /** Keyword table. First match wins, so order more specific phrases first. */
    private val keywords: List<Pair<Mood, List<String>>> = listOf(
        Mood.GRATEFUL to listOf(
            "thanks", "thank you", "thankyou", "shukriya", "shukria", "mehrban", "mehrbani",
            "thx", "tysm", "jazakallah", "شکریہ"
        ),
        Mood.LOVE to listOf(
            "i love you", "love you", "luv u", "pyar", "pyaar", "mohabbat", "pasand hai",
            "love it", "محبت", "پیار"
        ),
        Mood.FAREWELL to listOf(
            "bye", "goodbye", "good bye", "allah hafiz", "khuda hafiz", "see you", "alvida",
            "خدا حافظ", "اللہ حفاظت"
        ),
        Mood.GREETING to listOf(
            "hello", "hi ", "hi!", "hi?", "hey", "salam", "assalam", "asalam", "salaam",
            "aoa", "good morning", "good evening", "good night", "سلام", "ہیلو"
        ),
        Mood.HAPPY to listOf(
            "i'm fine", "im fine", "i am fine", "fine", "good", "great", "awesome", "acha",
            "theek", "thik", "badiya", "mast", "khush", "happy", "زندہ", "اچھا"
        ),
        Mood.SAD to listOf(
            "sad", "upset", "rona", "ro raha", "udaas", "udass", "dukhi", "pareshan",
            "presan", "gham", "depressed", "not good", "دکھی", "اداس", "پریشان"
        ),
        Mood.EXCITED to listOf(
            "wow", "wah", "kamal", "amazing", "zabardast", "kya baat", "shandar", "excited",
            "can't wait", "cant wait", "واو", "کمال", "زبردست"
        ),
        Mood.AGREE to listOf(
            "yes", "yeah", "yup", "ok", "okay", "theek hai", "thik hai", "haan", "han",
            "bilkul", "sure", "right", "سہی", "ہاں", "ٹھیک ہے"
        ),
        Mood.DISAGREE to listOf(
            "no ", "no!", "nope", "nahi", "nahin", "ghalat", "galat", "wrong", "disagree",
            "نہیں", "غلط"
        ),
        Mood.ANGRY to listOf(
            "angry", "gussa", "gusa", "naraz", "khafa", "hate", "nafrat", "غصہ", "ناراض"
        ),
        Mood.THINKING to listOf(
            "thinking", "soch", "soch raha", "maybe", "shayad", "pata nahi", "pta nahi",
            "hmm", "let me think", "سوچ", "شاید"
        )
    )

    /**
     * Detects the mood of [text]. Returns [Mood.NEUTRAL] for blank input or no match.
     */
    fun detect(text: String): Mood {
        val lower = " ${text.trim().lowercase()} "
        if (lower.isBlank()) return Mood.NEUTRAL
        for ((mood, words) in keywords) {
            for (word in words) {
                if (lower.contains(word)) return mood
            }
        }
        return Mood.NEUTRAL
    }
}
