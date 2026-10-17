package com.sit.litecad.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Модель выделения: рёбра, оси, полигоны и надписи ([CadText]).
 * Остальные поля (Vertex / Dimension / Leader) добавим, когда появится их выделение.
 *
 * Set-ы — Compose-state: изменение состава триггерит рекомпозицию холста.
 */
class Selection {

    val selectedEdgeIds: MutableSet<UUID> = mutableSetOf()
    val selectedAxisIds: MutableSet<UUID> = mutableSetOf()
    val selectedPolygonIds: MutableSet<UUID> = mutableSetOf()

    /** Выделенные надписи ([CadText]). Не участвуют в топологии граней. */
    val selectedTextIds: MutableSet<UUID> = mutableSetOf()

    /**
     * Счётчик изменений. Растёт на каждый мутационный вызов.
     * Нужен для Compose: Set сам по себе не триггерит рекомпозицию,
     * а вот этот счётчик — триггерит.
     */
    var revision by mutableStateOf(0)
        private set

    val isEmpty: Boolean
        get() = selectedEdgeIds.isEmpty() &&
                selectedAxisIds.isEmpty() &&
                selectedPolygonIds.isEmpty() &&
                selectedTextIds.isEmpty()

    fun clear() {
        val hadAny = !isEmpty
        selectedEdgeIds.clear()
        selectedAxisIds.clear()
        selectedPolygonIds.clear()
        selectedTextIds.clear()
        if (hadAny) revision++
    }

    fun addEdge(id: UUID) {
        if (selectedEdgeIds.add(id)) revision++
    }

    fun removeEdge(id: UUID) {
        if (selectedEdgeIds.remove(id)) revision++
    }

    fun addAxis(id: UUID) {
        if (selectedAxisIds.add(id)) revision++
    }

    fun removeAxis(id: UUID) {
        if (selectedAxisIds.remove(id)) revision++
    }

    fun addPolygon(id: UUID) {
        if (selectedPolygonIds.add(id)) revision++
    }

    fun removePolygon(id: UUID) {
        if (selectedPolygonIds.remove(id)) revision++
    }

    fun addText(id: UUID) {
        if (selectedTextIds.add(id)) revision++
    }

    fun removeText(id: UUID) {
        if (selectedTextIds.remove(id)) revision++
    }

    /**
     * Удаляет из выделения Id, которых больше нет в документе.
     * Вызывать после undo/redo/удаления.
     */
    fun pruneMissing(
        edgesById: Set<UUID>,
        axesById: Set<UUID>,
        polygonsById: Set<UUID>,
        textsById: Set<UUID>
    ) {
        val removedEdges = selectedEdgeIds.retainAll(edgesById)
        val removedAxes = selectedAxisIds.retainAll(axesById)
        val removedPolys = selectedPolygonIds.retainAll(polygonsById)
        val removedTexts = selectedTextIds.retainAll(textsById)
        if (removedEdges || removedAxes || removedPolys || removedTexts) revision++
    }
}