package io.github.interloper.noted.domain

import io.github.interloper.noted.data.Label
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelSelectionTest {

    private val a = Label(id = 1, name = "a")
    private val b = Label(id = 2, name = "b")
    private val locked = Label(id = 3, name = "locked", locked = true)
    private val folder = Label(id = 4, name = "folder", folder = true)
    private val all = listOf(a, b, locked, folder)

    @Test
    fun ordinaryLabels_combineAndUntick() {
        assertEquals(setOf(1L, 2L), toggleLabelSelection(setOf(1L), b, all))
        assertEquals(setOf(2L), toggleLabelSelection(setOf(1L, 2L), a, all))
    }

    @Test
    fun tickingExclusiveLabel_clearsEverythingElse() {
        assertEquals(setOf(3L), toggleLabelSelection(setOf(1L, 2L), locked, all))
        assertEquals(setOf(4L), toggleLabelSelection(setOf(3L), folder, all))
    }

    @Test
    fun tickingOrdinaryLabel_clearsExclusiveOne() {
        assertEquals(setOf(1L), toggleLabelSelection(setOf(3L), a, all))
        assertEquals(setOf(2L), toggleLabelSelection(setOf(4L), b, all))
    }

    @Test
    fun untickingExclusiveLabel_leavesNothing() {
        assertEquals(emptySet<Long>(), toggleLabelSelection(setOf(3L), locked, all))
    }
}
