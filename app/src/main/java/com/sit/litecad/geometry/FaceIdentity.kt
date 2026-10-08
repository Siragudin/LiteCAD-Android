package com.sit.litecad.geometry

import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DirectedEdgeReference
import com.sit.litecad.model.Loop
import com.sit.litecad.model.Polygon
import java.util.UUID

/**
 * Стабильная геометрическая идентичность грани.
 * Не привязана к Polygon.Id или Edge.Id — только к последовательности вершин.
 * Благодаря этому заливка «переживает» перестроение топологии.
 */
object FaceIdentity {

    /**
     * Ключ грани по внешнему контуру.
     */
    fun create(document: CadDocument, polygon: Polygon): String =
        createLoop(document, polygon.outerLoop)

    /**
     * Опциональный составной ключ региона: outer + holes.
     * Для идентичности Face (suppression/dedup) не используется.
     */
    fun createRegion(
        document: CadDocument,
        outerLoop: Loop,
        innerLoops: Iterable<Loop>
    ): String {
        val outer = createLoop(document, outerLoop)
        val holes = innerLoops
            .map { createLoop(document, it) }
            .filter { it.isNotEmpty() }
            .sorted()
        return if (holes.isEmpty()) outer else "$outer||${holes.joinToString("|")}"
    }

    /**
     * Ключ контура: канонизированная циклическая последовательность стартовых вершин.
     * Инвариантен к:
     *  - выбору начала обхода (offset),
     *  - направлению обхода (forward/reverse выбирается лексикографически меньшее).
     */
    fun createLoop(document: CadDocument, loop: Loop): String {
        val forward = getStartVertexSequence(document, loop)
        if (forward.isEmpty()) return ""

        val reverse = getStartVertexSequence(document, reverseLoop(loop))
        val forwardKey = canonicalizeCyclicSequence(forward)
        val reverseKey = canonicalizeCyclicSequence(reverse)

        return if (forwardKey <= reverseKey) forwardKey else reverseKey
    }

    private fun getStartVertexSequence(document: CadDocument, loop: Loop): List<UUID> {
        val result = ArrayList<UUID>(loop.edges.size)
        for (ref in loop.edges) {
            val edge = document.edgeById(ref.edgeId) ?: return emptyList()
            result.add(if (ref.forward) edge.startVertexId else edge.endVertexId)
        }
        return result
    }

    private fun reverseLoop(loop: Loop): Loop {
        val reversed = Loop()
        for (i in loop.edges.indices.reversed()) {
            val ref = loop.edges[i]
            reversed.edges.add(DirectedEdgeReference(ref.edgeId, !ref.forward))
        }
        return reversed
    }

    /**
     * Выбирает лексикографически минимальную ротацию последовательности.
     * Гарантирует один и тот же ключ независимо от того, с какой вершины начат обход.
     */
    private fun canonicalizeCyclicSequence(sequence: List<UUID>): String {
        if (sequence.isEmpty()) return ""
        var best: String? = null
        val n = sequence.size
        for (offset in 0 until n) {
            val parts = arrayOfNulls<String>(n)
            for (i in 0 until n) {
                parts[i] = sequence[(offset + i) % n].toString()
            }
            val candidate = parts.joinToString(",")
            if (best == null || candidate < best!!) best = candidate
        }
        return best ?: ""
    }
}