package net.vonforst.evmap.utils

import co.anbora.labs.spatia.geometry.Point
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

object SphericalMercatorProjection {
    // https://epsg.io/1024-method
    private val srid = 3857
    private val earthRadius = 6378137.0

    fun project(point: Point): Point {
        if (point.srid != 4326) throw IllegalArgumentException("expected WGS-84")
        val x = Math.toRadians(point.x)
        val y = ln(tan(PI / 4 + Math.toRadians(point.y) / 2))

        return Point(x * earthRadius, y * earthRadius, srid)
    }

    fun unproject(point: Point): Point {
        if (point.srid != srid) throw IllegalArgumentException("expected WGS-84")
        val x = point.x / earthRadius
        val y = PI / 2 - 2 * atan(exp(-point.y / earthRadius))
        return Point(Math.toDegrees(x), Math.toDegrees(y))
    }
}