package io.github.interloper.noted.domain

data class TextStats(val words: Int, val characters: Int)

private val whitespace = Regex("\\s+")

/** Word and character counts for the note info sheet. Characters exclude line breaks. */
fun textStats(text: String): TextStats {
    val trimmed = text.trim()
    val words = if (trimmed.isEmpty()) 0 else trimmed.split(whitespace).size
    return TextStats(words = words, characters = text.count { it != '\n' && it != '\r' })
}
