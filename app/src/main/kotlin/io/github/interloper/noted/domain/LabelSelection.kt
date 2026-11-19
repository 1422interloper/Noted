package io.github.interloper.noted.domain

import io.github.interloper.noted.data.Label

/**
 * Toggles [label] in a note's label selection while keeping the exclusivity rule: ticking a locked or folder label
 * clears everything else, and ticking an ordinary label clears any locked or folder label.
 */
fun toggleLabelSelection(selected: Set<Long>, label: Label, allLabels: List<Label>): Set<Long> {
    if (label.id in selected) return selected - label.id
    if (label.isExclusive) return setOf(label.id)
    val exclusiveIds = allLabels.filter { it.isExclusive }.mapTo(HashSet()) { it.id }
    return selected - exclusiveIds + label.id
}
