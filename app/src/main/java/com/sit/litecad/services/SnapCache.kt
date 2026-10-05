package com.sit.litecad.services

import com.sit.litecad.math.Point2

internal class DocumentSnapCache(
    val Fingerprint: Int,
    val StaticCandidates: List<Pair<SnapPoint, SnapIdentity>>,
    val AlignmentPoints: List<Point2>,
    val VerticalAlignmentPoints: List<Point2>
)

internal data class SnapCandidate(
    val Snap: SnapPoint,
    val Identity: SnapIdentity,
    val Distance: Double
)