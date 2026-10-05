package com.sit.litecad.services

import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.math.TopologyTolerance
import java.util.UUID
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.max

/**
 * Пространственная регулярная сетка (Broad-Phase Index) для ускорения поиска пересечений.
 * Полный аналог SnapSegmentSpatialGrid из C# версии.
 */
internal class SnapSegmentSpatialGrid private constructor(
    private val entries: List<Entry>
) {
    private val cells = HashMap<CellKey, MutableList<Int>>()
    private val longSegmentIndices = ArrayList<Int>()

    // Вспомогательный класс ключа для HashMap в Kotlin (Замена C#-вского кортежа (long, long))
    private data class CellKey(val X: Long, val Y: Long)

    private data class Entry(
        val Id: UUID,
        val Start: Point2,
        val End: Point2,
        val MinX: Double,
        val MinY: Double,
        val MaxX: Double,
        val MaxY: Double
    )

    val count: Int
        get() = entries.size

    companion object {
        private const val CELL_SIZE = 50.0
        private const val MAX_CELLS_PER_AXIS = 128

        fun fromSegments(
            segments: Iterable<Triple<UUID, Point2, Point2>>,
            tolerance: Double
        ): SnapSegmentSpatialGrid {
            val resolvedTolerance = TopologyTolerance.ForMutation // Используем наш ForMutation
            val entries = ArrayList<Entry>()

            for ((id, start, end) in segments) {
                val bounds = expandBounds(start, end, resolvedTolerance)
                entries.add(
                    Entry(
                        Id = id,
                        Start = start,
                        End = end,
                        MinX = bounds.minX,
                        MinY = bounds.minY,
                        MaxX = bounds.maxX,
                        MaxY = bounds.maxY
                    )
                )
            }

            val grid = SnapSegmentSpatialGrid(entries)
            grid.buildCells()
            return grid
        }

        private fun expandBounds(start: Point2, end: Point2, tolerance: Double): Bounds {
            val minX = min(start.X, end.X) - tolerance
            val minY = min(start.Y, end.Y) - tolerance
            val maxX = max(start.X, end.X) + tolerance
            val maxY = max(start.Y, end.Y) + tolerance
            return Bounds(minX, minY, maxX, maxY)
        }

        private class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)
    }

    fun getStart(index: Int): Point2 = entries[index].Start

    fun getEnd(index: Int): Point2 = entries[index].End

    fun getCandidateIndicesForSegment(start: Point2, end: Point2, tolerance: Double): HashSet<Int> {
        val resolvedTolerance = TopologyTolerance.ForMutation
        val bounds = expandBounds(start, end, resolvedTolerance)
        return collectCandidates(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, excludeIndex = -1)
    }

    fun getOrderedPairIndices(): List<Pair<Int, Int>> {
        val seen = HashSet<Pair<Int, Int>>()
        val pairs = ArrayList<Pair<Int, Int>>()

        for (bucket in cells.values) {
            for (i in 0 until bucket.size) {
                for (j in i + 1 until bucket.size) {
                    tryAddPair(bucket[i], bucket[j], seen, pairs)
                }
            }
        }

        for (i in 0 until longSegmentIndices.size) {
            val longIndex = longSegmentIndices[i]
            for (otherIndex in 0 until entries.size) {
                tryAddPair(longIndex, otherIndex, seen, pairs)
            }
        }

        // Сортировка по индексам точно как в C#
        pairs.sortWith { left, right ->
            val outerCompare = left.first.compareTo(right.first)
            if (outerCompare != 0) outerCompare else left.second.compareTo(right.second)
        }

        return pairs
    }

    private fun tryAddPair(
        indexA: Int,
        indexB: Int,
        seen: HashSet<Pair<Int, Int>>,
        pairs: ArrayList<Pair<Int, Int>>
    ) {
        if (indexA == indexB) return

        val entryA = entries[indexA]
        val entryB = entries[indexB]
        if (!boundsOverlap(entryA, entryB)) return

        val outerIndex: Int
        val innerIndex: Int

        // Сравнение UUID (аналог Guid.CompareTo на C#)
        val uuidCompare = entryA.Id.compareTo(entryB.Id)
        if (uuidCompare < 0) {
            outerIndex = indexA
            innerIndex = indexB
        } else if (uuidCompare > 0) {
            outerIndex = indexB
            innerIndex = indexA
        } else {
            return
        }

        val pair = Pair(outerIndex, innerIndex)
        if (!seen.add(pair)) return

        pairs.add(pair)
    }

    private fun collectCandidates(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
        excludeIndex: Int
    ): HashSet<Int> {
        val result = HashSet<Int>()
        val minCellX = floor(minX / CELL_SIZE).toLong()
        val maxCellX = floor(maxX / CELL_SIZE).toLong()
        val minCellY = floor(minY / CELL_SIZE).toLong()
        val maxCellY = floor(maxY / CELL_SIZE).toLong()

        for (cellX in minCellX..maxCellX) {
            for (cellY in minCellY..maxCellY) {
                val bucket = cells[CellKey(cellX, cellY)] ?: continue

                for (index in bucket) {
                    if (index == excludeIndex) continue

                    if (boundsOverlap(minX, minY, maxX, maxY, entries[index])) {
                        result.add(index)
                    }
                }
            }
        }

        for (index in longSegmentIndices) {
            if (index == excludeIndex) continue

            if (boundsOverlap(minX, minY, maxX, maxY, entries[index])) {
                result.add(index)
            }
        }

        return result
    }

    private fun buildCells() {
        for (index in entries.indices) {
            val entry = entries[index]
            val minCellX = floor(entry.MinX / CELL_SIZE).toLong()
            val maxCellX = floor(entry.MaxX / CELL_SIZE).toLong()
            val minCellY = floor(entry.MinY / CELL_SIZE).toLong()
            val maxCellY = floor(entry.MaxY / CELL_SIZE).toLong()

            val spanX = maxCellX - minCellX + 1
            val spanY = maxCellY - minCellY + 1
            if (spanX > MAX_CELLS_PER_AXIS || spanY > MAX_CELLS_PER_AXIS) {
                longSegmentIndices.add(index)
                continue
            }

            for (cellX in minCellX..maxCellX) {
                for (cellY in minCellY..maxCellY) {
                    val key = CellKey(cellX, cellY)
                    var bucket = cells[key]
                    if (bucket == null) {
                        bucket = ArrayList()
                        cells[key] = bucket
                    }
                    bucket.add(index)
                }
            }
        }
    }

    private fun boundsOverlap(a: Entry, b: Entry): Boolean =
        a.MinX <= b.MaxX && a.MaxX >= b.MinX && a.MinY <= b.MaxY && a.MaxY >= b.MinY

    private fun boundsOverlap(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
        entry: Entry
    ): Boolean =
        minX <= entry.MaxX && maxX >= entry.MinX && minY <= entry.MaxY && maxY >= entry.MinY
}
