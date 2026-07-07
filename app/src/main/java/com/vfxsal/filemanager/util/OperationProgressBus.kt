package com.vfxsal.filemanager.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OperationProgress(val label: String, val done: Int, val total: Int)

/**
 * App-wide channel for long-running batch file operations (bulk deletes, emptying the
 * recycle bin). Any view model reports progress here and a single overlay dialog rendered
 * at the app root shows it - so every section gets the same smooth "Deleting 3 of 12"
 * experience without each screen owning its own progress UI.
 *
 * Single-item operations shouldn't use this (a dialog that flashes for 50ms reads as a
 * glitch); [start] therefore ignores batches smaller than two items, and [update]/[finish]
 * become no-ops in that case.
 */
object OperationProgressBus {

    private const val MIN_ITEMS_TO_SHOW = 2

    private val _state = MutableStateFlow<OperationProgress?>(null)
    val state: StateFlow<OperationProgress?> = _state.asStateFlow()

    fun start(label: String, total: Int) {
        if (total >= MIN_ITEMS_TO_SHOW) {
            _state.value = OperationProgress(label = label, done = 0, total = total)
        }
    }

    fun update(done: Int) {
        _state.value = _state.value?.let { it.copy(done = done.coerceIn(0, it.total)) }
    }

    fun finish() {
        _state.value = null
    }
}
