package com.sit.litecad.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DocumentSnapshot

class DocumentHistoryService(
    private val maxSteps: Int = 10
) {
    private val undoStack = ArrayDeque<DocumentSnapshot>()
    private val redoStack = ArrayDeque<DocumentSnapshot>()

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    private fun syncFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun record(document: CadDocument) {
        undoStack.addLast(DocumentSnapshot.capture(document))
        while (undoStack.size > maxSteps) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        syncFlags()
    }

    fun undo(document: CadDocument): Boolean {
        if (undoStack.isEmpty()) return false

        redoStack.addLast(DocumentSnapshot.capture(document))
        undoStack.removeLast().restore(document)
        syncFlags()
        return true
    }

    fun redo(document: CadDocument): Boolean {
        if (redoStack.isEmpty()) return false

        undoStack.addLast(DocumentSnapshot.capture(document))
        redoStack.removeLast().restore(document)
        syncFlags()
        return true
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        syncFlags()
    }
}