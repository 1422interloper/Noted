package io.github.interloper.noted.domain

import io.github.interloper.noted.data.NoteWithLabels

sealed interface NoteFilter {
    data object All : NoteFilter
    data class ByLabel(val labelId: Long) : NoteFilter
    data object Bin : NoteFilter
}

/**
 * Decides which notes the list shows.
 *
 * - Notes under a locked label are hidden everywhere until [unlocked], and even then only appear under their own
 *   label, in search results and in the bin.
 * - Notes under a folder label are left out of "All" and only appear under their own label and in search results.
 * - A non-blank [query] searches titles and body text, ignoring case: the bin when [filter] is the bin, otherwise
 *   every note outside it regardless of the selected label.
 * - An individually locked note is always listed (the list shows only its title), but until [unlocked] a search
 *   matches it by title alone, so results never reveal what its text contains.
 *
 * Input order is preserved.
 */
fun visibleNotes(
    active: List<NoteWithLabels>,
    binned: List<NoteWithLabels>,
    filter: NoteFilter,
    query: String,
    unlocked: Boolean,
): List<NoteWithLabels> {
    val q = query.trim()
    val source = if (filter == NoteFilter.Bin) binned else active
    return source.filter { n ->
        if (n.hasLockedLabel && !unlocked) return@filter false
        if (q.isNotEmpty()) {
            n.note.title.contains(q, ignoreCase = true) ||
                ((unlocked || !n.note.locked) && n.note.plainText.contains(q, ignoreCase = true))
        } else {
            when (filter) {
                NoteFilter.All -> !n.hasLockedLabel && !n.hasFolderLabel
                is NoteFilter.ByLabel -> n.hasLabel(filter.labelId)
                NoteFilter.Bin -> true
            }
        }
    }
}
