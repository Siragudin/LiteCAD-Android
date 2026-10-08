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
     * Выбирает лексикографически минимальную циклическую ротацию последовательности.
     * Гарантирует один и тот же ключ независимо от того, с какой вершины начат обход.
     *
     * Используется алгоритм Боута — O(n) (раньше было O(n²) из-за построения n склеенных
     * строк). Токены ([UUID.toString]) имеют одинаковую длину и разделяются ',', поэтому
     * сравнение склеенных строк эквивалентно поэлементному сравнению токенов.
     */
    private fun canonicalizeCyclicSequence(sequence: List<UUID>): String {
        val n = sequence.size
        if (n == 0) return ""

        val tokens = Array(n) { sequence[it].toString() }

        // Поиск начала минимальной ротации (алгоритм Боута).
        var i = 0
        var j = 1
        var k = 0
        while (i < n && j < n && k < n) {
            val cmp = tokens[(i + k) % n].compareTo(tokens[(j + k) % n])
            when {
                cmp == 0 -> k++
                cmp > 0 -> {
                    i += k + 1
                    if (i <= j) i = j + 1
                    k = 0
                }
                else -> {
                    j += k + 1
                    if (j <= i) j = i + 1
                    k = 0
                }
            }
        }
        val start = minOf(i, j)

        val sb = StringBuilder(n * 37)
        for (t in 0 until n) {
            if (t > 0) sb.append(',')
            sb.append(tokens[(start + t) % n])
        }
        return sb.toString()
    }
}