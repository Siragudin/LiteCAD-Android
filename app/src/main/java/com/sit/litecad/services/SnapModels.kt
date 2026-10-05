package com.sit.litecad.services

import com.sit.litecad.math.Point2
import java.util.UUID

/**
 * Эквивалент перечисления SnapKind из C# версии
 */
enum class SnapKind {
    Endpoint,
    Midpoint,
    Intersection,
    AxisIntersection,
    OnEdge,
    Alignment
}

/**
 * Структура точки привязки. Полный аналог SnapPoint из C#
 */
data class SnapPoint(
    val Position: Point2,
    val Kind: SnapKind,
    val EdgeId: UUID? = null,
    val VertexId: UUID? = null
)

/**
 * Идентификатор привязки для исключения дубликатов. Полный аналог SnapIdentity
 */
data class SnapIdentity(
    val Kind: SnapKind,
    val Key: String
) {
    companion object {
        fun forVertex(vertexId: UUID): SnapIdentity =
            SnapIdentity(SnapKind.Endpoint, vertexId.toString())

        fun forMidpoint(edgeId: UUID): SnapIdentity =
            SnapIdentity(SnapKind.Midpoint, edgeId.toString())

        fun forAxisMidpoint(axisId: UUID): SnapIdentity =
            SnapIdentity(SnapKind.Midpoint, "axis:$axisId")

        fun forAxisEndpoint(axisId: UUID, isStart: Boolean): SnapIdentity =
            SnapIdentity(SnapKind.Endpoint, "axis-end:$axisId:${if (isStart) 's' else 'e'}")

        fun forOnAxis(axisId: UUID, parameterKey: Long): SnapIdentity =
            SnapIdentity(SnapKind.OnEdge, "axis-on:$axisId:$parameterKey")

        fun forAxisIntersection(axisIds: Collection<UUID>): SnapIdentity {
            val normalized = axisIds.map { it.toString() }.sorted()
            return SnapIdentity(SnapKind.AxisIntersection, normalized.joinToString("|"))
        }

        fun forIntersection(edgeIds: Collection<UUID>): SnapIdentity {
            val normalized = edgeIds.map { it.toString() }.sorted()
            return SnapIdentity(SnapKind.Intersection, normalized.joinToString("|"))
        }

        fun forOnEdge(edgeId: UUID, parameterKey: Long): SnapIdentity =
            SnapIdentity(SnapKind.OnEdge, "$edgeId:$parameterKey")

        fun forAlignment(alignmentKey: String): SnapIdentity =
            SnapIdentity(SnapKind.Alignment, alignmentKey)

        fun fromSnap(snap: SnapPoint): SnapIdentity = when (snap.Kind) {
            SnapKind.Endpoint -> if (snap.VertexId != null) forVertex(snap.VertexId) else SnapIdentity(snap.Kind, snap.Position.toString())
            SnapKind.Midpoint -> if (snap.EdgeId != null) forMidpoint(snap.EdgeId) else SnapIdentity(snap.Kind, snap.Position.toString())
            SnapKind.OnEdge -> if (snap.EdgeId != null) forOnEdge(snap.EdgeId, 0) else SnapIdentity(snap.Kind, snap.Position.toString())
            SnapKind.Alignment -> forAlignment("alignment")
            SnapKind.AxisIntersection -> forAxisIntersection(emptyList())
            else -> SnapIdentity(snap.Kind, snap.Position.toString())
        }
    }
}

/**
 * Результат единичного поиска лучшей точки. Полный аналог SnapResult
 */
data class SnapResult(
    val Snap: SnapPoint?,
    val HasSnap: Boolean
) {
    fun resolve(fallback: Point2): Point2 = Snap?.Position ?: fallback
}

/**
 * Итоговый результат пакетного запроса службы привязок. Полный аналог SnapQueryResult
 */
data class SnapQueryResult(
    val Best: SnapResult,
    val Visible: List<SnapPoint>
)
