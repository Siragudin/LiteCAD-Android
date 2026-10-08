package com.sit.litecad.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/**
 * Модель выделения. Пока поддерживаем только рёбра и полигоны.
 * Остальные поля (Vertex / Dimension / Axis / Leader / Text) добавим, когда появятся
 * соответствующие сущности в Android-версии.
 *
 * Set-ы — Compose-state: изменение состава триггерит рекомпозицию холста.
 */
class Selection {

    val selectedEdgeIds: MutableSet<UUID> = mutableSetOf()
    val selectedPolygonIds: MutableSet<UUID> = mutableSetOf()

    /**
     * Счётчик изменений. Растёт на каждый мутационный вызов.
     * Нужен для Compose: Set сам по себе не триггерит рекомпозицию,
     * а вот этот счётчик — триггерит.
     */
    var revision by mutableStateOf(0)
        private set

    val isEmpty: Boolean
        get() = selectedEdgeIds.isEmpty() && selectedPolygonIds.isEmpty()

    fun clear() {
        val hadAny = !isEmpty
        selectedEdgeIds.clear()
        selectedPolygonIds.clear()
        if (hadAny) revision++
    }

    fun addEdge(id: UUID) {
        if (selectedEdgeIds.add(id)) revision++
    }

    fun removeEdge(id: UUID) {
        if (selectedEdgeIds.remove(id)) revision++
    }

    fun addPolygon(id: UUID) {
        if (selectedPolygonIds.add(id)) revision++
    }

    fun removePolygon(id: UUID) {
        if (selectedPolygonIds.remove(id)) revision++
    }

    /**
     * Удаляет из выделения Id, которых больше нет в документе.
     * Вызывать после undo/redo/удаления.
     */
    fun pruneMissing(
        edgesById: Set<UUID>,
        polygonsById: Set<UUID>
    ) {
        val removedEdges = selectedEdgeIds.retainAll(edgesById)
        val removedPolys = selectedPolygonIds.retainAll(polygonsById)
        if (removedEdges || removedPolys) revision++
    }
}