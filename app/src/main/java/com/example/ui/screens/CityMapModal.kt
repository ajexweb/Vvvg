package com.example.ui.screens

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.BuildingStyle
import com.example.game.engine.CityWorldData
import com.example.game.engine.PoiCategory
import com.example.game.engine.RoadClass
import com.example.game.engine.WorldBiome
import com.example.game.engine.WorldMapData
import com.example.game.model.CityZones
import com.example.game.model.GameTelemetry
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.TacticalAccent
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalSilver
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val MapGlassPanel = Color(0xFF0A1019).copy(alpha = 0.88f)
private val MapBorderColor = Color.White.copy(alpha = 0.18f)
private val OceanDeepBackdrop = Color(0xFF081B30)
private val HighwayCasingColor = Color(0xFF1A222D)
private val HighwayFillColor = Color(0xFFD5A96A)
private val GhatRoadFillColor = Color(0xFFC98B52)
private val SeaBridgeCasingColor = Color(0xFFE2F1F8)
private val SeaBridgeFillColor = Color(0xFF475569)
private val DirtTrailColor = Color(0xFF9E8462)
private val ShopMarkerTeal = Color(0xFF2DD4BF)
private val VehicleRedDot = Color(0xFFF87171)

// Calibrated for the massive 50 km x 65 km GTA 6-Style Multi-Island Open World:
//   Low Zoom (0.25x - 1.30x): Full 50 km x 65 km Mainland + all 6 Big Islands with crisp region/island labels
//   Medium Zoom (0.80x - 6.00x): Regional Towns, Dhabas, Hotels, Garages, Petrol & Landmarks with AABB spacing
//   High Zoom (6.00x - 32.0x): Saini Metro downtown streets, buildings, enterables, shops & parked vehicles!
private const val MAP_BASE_WORLD_SPAN = 18000f
private const val MIN_MAP_ZOOM = 0.25f
private const val MAX_MAP_ZOOM = 32.0f

private fun formatMapDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format("%.2f km", meters / 1000f)
    } else {
        "${meters}m"
    }
}

private fun poiColor(category: PoiCategory): Color = when (category) {
    PoiCategory.LANDMARK -> Color(0xFFFBBF24)
    PoiCategory.TOWN -> TacticalCyan
    PoiCategory.MILITARY -> Color(0xFFF87171)
    PoiCategory.GARAGE -> EmeraldPulse
    PoiCategory.PETROL_PUMP -> Color(0xFFFB923C)
    PoiCategory.DHABA -> ShopMarkerTeal
    PoiCategory.HELIPAD_DOCK -> Color(0xFFE2E8F0)
}

private fun DrawScope.drawCategoryMarker(category: PoiCategory, cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(color = color.copy(alpha = 0.24f), radius = r * 1.9f, center = Offset(cx, cy))
    when (category) {
        PoiCategory.LANDMARK -> {
            // Crisp diamond icon for Landmarks
            val d = Path().apply {
                moveTo(cx, cy - r * 1.25f)
                lineTo(cx + r * 1.15f, cy)
                lineTo(cx, cy + r * 1.25f)
                lineTo(cx - r * 1.15f, cy)
                close()
            }
            drawPath(d, color = color)
            drawPath(d, color = Color.Black.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
        }
        PoiCategory.MILITARY -> {
            // Tactical triangle icon for Military Zones
            val t = Path().apply {
                moveTo(cx, cy - r * 1.25f)
                lineTo(cx + r * 1.2f, cy + r * 0.95f)
                lineTo(cx - r * 1.2f, cy + r * 0.95f)
                close()
            }
            drawPath(t, color = color)
            drawPath(t, color = Color.Black.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
        }
        PoiCategory.GARAGE, PoiCategory.PETROL_PUMP -> {
            // Rounded square badge for Garages & Petrol Pumps
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.7f),
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 1.2f)
            )
        }
        else -> {
            drawCircle(color = color, radius = r, center = Offset(cx, cy))
            drawCircle(color = Color.Black.copy(alpha = 0.65f), radius = r, center = Offset(cx, cy), style = Stroke(width = 1.2f))
        }
    }
}

@Composable
fun CityMapModal(
    telemetry: GameTelemetry,
    streamingRadiusMeters: Float,
    waypointX: Float?,
    waypointZ: Float?,
    onSetWaypoint: (Float, Float) -> Unit,
    onClearWaypoint: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val satelliteImage = remember { WorldMapData.getOrCreateSatelliteBitmap().asImageBitmap() }
    val enterables = remember { CityWorldData.createEnterableBuildings() }
    val shops = remember { CityWorldData.createRoadsideShops() }

    // Default 0.27x overview showcases the entire 50 km x 65 km Bharat Mainland + all 6 Big Islands!
    var zoom by remember { mutableFloatStateOf(0.27f) }
    var centerWorldX by remember { mutableFloatStateOf(0f) }
    var centerWorldZ by remember { mutableFloatStateOf(2000f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    // Curved Road-Network GPS Pathfinding (recomputed when pin moves or player shifts >15m)
    val playerBucketX = (telemetry.playerX / 15f).toInt()
    val playerBucketZ = (telemetry.playerZ / 15f).toInt()
    val gpsRoutePoints = remember(waypointX, waypointZ, playerBucketX, playerBucketZ) {
        if (waypointX != null && waypointZ != null) {
            WorldMapData.computeRoadRoute(telemetry.playerX, telemetry.playerZ, waypointX, waypointZ)
        } else emptyList()
    }
    val waypointDistMeters = remember(gpsRoutePoints, waypointX, waypointZ, telemetry.playerX, telemetry.playerZ) {
        if (waypointX != null && waypointZ != null) {
            if (gpsRoutePoints.size >= 2) {
                WorldMapData.computePolylineLengthMeters(gpsRoutePoints).roundToInt()
            } else {
                val dx = waypointX - telemetry.playerX
                val dz = waypointZ - telemetry.playerZ
                sqrt(dx * dx + dz * dz).roundToInt()
            }
        } else null
    }

    val regionTitlePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(7f, 0f, 2f, android.graphics.Color.BLACK)
        }
    }
    val poiLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFF1F5F9).toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(5f, 0f, 1.5f, android.graphics.Color.BLACK)
        }
    }
    val subLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFF8FAFC).toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }
    val shopLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFF5EEAD4).toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(3.5f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }
    val distBadgePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OceanDeepBackdrop)
            .testTag("modal_city_map")
    ) {
        // 1. Fullscreen Interactive 50 km x 65 km GTA-6 Style Multi-Island Satellite Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val baseScale = min(canvasSize.width, canvasSize.height).coerceAtLeast(1) / MAP_BASE_WORLD_SPAN
                        val newZoom = (zoom * gestureZoom).coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
                        val worldPerPx = 1f / (baseScale * newZoom)
                        centerWorldX = (centerWorldX - pan.x * worldPerPx).coerceIn(-24500f, 24500f)
                        centerWorldZ = (centerWorldZ - pan.y * worldPerPx).coerceIn(-32000f, 32000f)
                        zoom = newZoom
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val baseScale = min(canvasSize.width, canvasSize.height).coerceAtLeast(1) / MAP_BASE_WORLD_SPAN
                        val scale = baseScale * zoom
                        val wx = (centerWorldX + (tapOffset.x - canvasSize.width * 0.5f) / scale).coerceIn(-24500f, 24500f)
                        val wz = (centerWorldZ + (tapOffset.y - canvasSize.height * 0.5f) / scale).coerceIn(-32000f, 32000f)
                        onSetWaypoint(wx, wz)
                    }
                }
        ) {
            val baseScale = min(size.width, size.height) / MAP_BASE_WORLD_SPAN
            val scale = baseScale * zoom
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f

            fun sx(wx: Float): Float = cx + (wx - centerWorldX) * scale
            fun sy(wz: Float): Float = cy + (wz - centerWorldZ) * scale

            // 1a. Cached 512x666 Hillshaded Multi-Biome Non-Square 50km x 65km Archipelago Satellite Map
            val mapLeft = sx(-WorldMapData.WORLD_HALF_X).roundToInt()
            val mapTop = sy(-WorldMapData.WORLD_HALF_Z).roundToInt()
            val mapW = (WorldMapData.WORLD_WIDTH * scale).roundToInt().coerceAtLeast(1)
            val mapH = (WorldMapData.WORLD_HEIGHT * scale).roundToInt().coerceAtLeast(1)
            drawImage(
                image = satelliteImage,
                dstOffset = IntOffset(mapLeft, mapTop),
                dstSize = IntSize(mapW, mapH)
            )

            // 1b. Subtle Tactical Map Coordinate Grid
            val gridStep = when {
                zoom >= 10.0f -> 200f
                zoom >= 3.0f -> 1000f
                zoom >= 0.9f -> 4000f
                else -> 10000f
            }
            val gridColor = Color.White.copy(alpha = 0.055f)
            var gxW = -25000f
            while (gxW <= 25000f) {
                val gx = sx(gxW)
                if (gx in 0f..size.width) {
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                }
                gxW += gridStep
            }
            var gzW = -32500f
            while (gzW <= 32500f) {
                val gy = sy(gzW)
                if (gy in 0f..size.height) {
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                }
                gzW += gridStep
            }

            // 1c. Saini Metro Urban Overlay (when zoomed into downtown Saini Metro: >= 5.0x)
            if (zoom >= 5.0f) {
                // Emerald City Park
                drawRect(
                    color = Color(0xFF1F5D30),
                    topLeft = Offset(sx(-96f), sy(56f)),
                    size = Size(40f * scale, 40f * scale)
                )
                // Saini Army Base & Helipads
                drawRect(
                    color = Color(0xFF3A3F38),
                    topLeft = Offset(sx(CityWorldData.ARMY_BASE_CENTER_X - 62f), sy(CityWorldData.ARMY_BASE_CENTER_Z - 48f)),
                    size = Size(124f * scale, 96f * scale)
                )
                if (zoom >= 8.0f) {
                    for (hp in CityWorldData.helipads) {
                        val hcx = sx(hp.x); val hcy = sy(hp.z)
                        val hr = (hp.size * 0.45f * scale).coerceAtLeast(2.5f)
                        drawCircle(Color(0xFF555E54), radius = hr, center = Offset(hcx, hcy))
                        drawCircle(TacticalSilver.copy(alpha = 0.72f), radius = hr * 0.72f, center = Offset(hcx, hcy), style = Stroke(width = 1.2f))
                    }
                }
                // Saini International Airport Runway
                val rx = CityWorldData.AIRPORT_RUNWAY_X
                val rz = CityWorldData.AIRPORT_RUNWAY_Z
                val rw = CityWorldData.AIRPORT_RUNWAY_WIDTH
                val rl = CityWorldData.AIRPORT_RUNWAY_LENGTH
                drawRect(
                    color = Color(0xFF2E3540),
                    topLeft = Offset(sx(rx - 4f - (rw + 22f) * 0.5f), sy(rz - (rl + 10f) * 0.5f)),
                    size = Size((rw + 22f) * scale, (rl + 10f) * scale)
                )
                drawRect(
                    color = Color(0xFF1B2028),
                    topLeft = Offset(sx(rx - rw * 0.5f), sy(rz - rl * 0.5f)),
                    size = Size(rw * scale, rl * scale)
                )

                // Downtown Road Grid
                val mainRoadColor = Color(0xFF4F5B6D)
                for (r in CityWorldData.roadSegments) {
                    val rwSeg = if (r.isVertical) r.width else r.length
                    val rdSeg = if (r.isVertical) r.length else r.width
                    val left = sx(r.x - rwSeg * 0.5f)
                    val top = sy(r.z - rdSeg * 0.5f)
                    val wPx = rwSeg * scale
                    val hPx = rdSeg * scale
                    if (left + wPx < 0f || left > size.width || top + hPx < 0f || top > size.height) continue
                    drawRect(color = mainRoadColor, topLeft = Offset(left, top), size = Size(wPx, hPx))
                }
                for (ix in CityWorldData.intersections) {
                    val left = sx(ix.x - ix.sizeX * 0.5f)
                    val top = sy(ix.z - ix.sizeZ * 0.5f)
                    val wPx = ix.sizeX * scale
                    val hPx = ix.sizeZ * scale
                    if (left + wPx < 0f || left > size.width || top + hPx < 0f || top > size.height) continue
                    drawRect(color = Color(0xFF5A677A), topLeft = Offset(left, top), size = Size(wPx, hPx))
                }
            }

            if (zoom >= 10.5f) {
                val alleyColor = Color(0xFF3E4857)
                for (al in CityWorldData.colonyAlleys) {
                    val aw = if (al.isVertical) al.width else al.length
                    val ad = if (al.isVertical) al.length else al.width
                    val left = sx(al.x - aw * 0.5f)
                    val top = sy(al.z - ad * 0.5f)
                    val wPx = aw * scale
                    val hPx = ad * scale
                    if (left + wPx < 0f || left > size.width || top + hPx < 0f || top > size.height) continue
                    drawRect(color = alleyColor, topLeft = Offset(left, top), size = Size(wPx, hPx))
                }
            }

            if (zoom >= 7.5f) {
                for (b in CityWorldData.buildings) {
                    val left = sx(b.x - b.width * 0.5f)
                    val top = sy(b.z - b.depth * 0.5f)
                    val bw = b.width * scale
                    val bh = b.depth * scale
                    if (left + bw < -8f || left > size.width + 8f || top + bh < -8f || top > size.height + 8f) continue
                    val bCol = when (b.style) {
                        BuildingStyle.GLASS_SKYSCRAPER -> Color(0xFF637E99)
                        BuildingStyle.APARTMENT_WINDOWS -> Color(0xFF7D8590)
                        BuildingStyle.RED_BRICK_HOUSE -> Color(0xFF8F6659)
                        BuildingStyle.FACTORY_INDUSTRIAL -> Color(0xFF666F78)
                    }
                    drawRect(color = bCol, topLeft = Offset(left, top), size = Size(bw, bh))
                }
                for (eb in enterables) {
                    val left = sx(eb.centerX - eb.halfW)
                    val top = sy(eb.centerZ - eb.halfD)
                    val bw = eb.width * scale
                    val bh = eb.depth * scale
                    if (left + bw < -8f || left > size.width + 8f || top + bh < -8f || top > size.height + 8f) continue
                    drawRect(color = TacticalCyan.copy(alpha = 0.28f), topLeft = Offset(left, top), size = Size(bw, bh))
                    drawRect(color = TacticalCyan, topLeft = Offset(left, top), size = Size(bw, bh), style = Stroke(width = 1.4f))
                }
            }

            if (zoom >= 11.5f) {
                for (shop in shops) {
                    val left = sx(shop.x - shop.width * 0.5f)
                    val top = sy(shop.z - shop.depth * 0.5f)
                    val bw = shop.width * scale
                    val bh = shop.depth * scale
                    if (left + bw < -6f || left > size.width + 6f || top + bh < -6f || top > size.height + 6f) continue
                    drawRect(color = ShopMarkerTeal.copy(alpha = 0.36f), topLeft = Offset(left, top), size = Size(bw, bh))
                    drawRect(color = ShopMarkerTeal, topLeft = Offset(left, top), size = Size(bw, bh), style = Stroke(width = 1.2f))
                }
            }

            // 1d. Curved GTA-6 Style Highway, Mountain Ghat, Overseas Sea Bridge & Dirt Trail Network!
            for (spline in WorldMapData.roadSplines) {
                if (spline.roadClass == RoadClass.DIRT_TRAIL && zoom < 0.58f) continue
                val pts = spline.points
                if (pts.size < 2) continue

                val path = Path().apply {
                    moveTo(sx(pts[0].first), sy(pts[0].second))
                    for (i in 1 until pts.size) {
                        lineTo(sx(pts[i].first), sy(pts[i].second))
                    }
                    if (spline.closedLoop) close()
                }

                when (spline.roadClass) {
                    RoadClass.SEA_BRIDGE -> {
                        val wOut = (4.8f + zoom * 3.8f).coerceIn(4.8f, 16f)
                        val wIn = (wOut * 0.62f).coerceAtLeast(2.4f)
                        drawPath(path, color = SeaBridgeCasingColor, style = Stroke(width = wOut, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(path, color = SeaBridgeFillColor, style = Stroke(width = wIn, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    RoadClass.HIGHWAY, RoadClass.RING_EXPRESSWAY -> {
                        val wOut = (4.2f + zoom * 3.2f).coerceIn(4.0f, 14f)
                        val wIn = (wOut * 0.56f).coerceAtLeast(2.0f)
                        drawPath(path, color = HighwayCasingColor, style = Stroke(width = wOut, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(path, color = HighwayFillColor, style = Stroke(width = wIn, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    RoadClass.GHAT_MOUNTAIN -> {
                        val wOut = (3.6f + zoom * 2.7f).coerceIn(3.5f, 11.5f)
                        val wIn = (wOut * 0.56f).coerceAtLeast(1.8f)
                        drawPath(path, color = HighwayCasingColor, style = Stroke(width = wOut, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawPath(path, color = GhatRoadFillColor, style = Stroke(width = wIn, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    RoadClass.DIRT_TRAIL -> {
                        val wTrail = (1.6f + zoom * 1.4f).coerceIn(1.6f, 5.5f)
                        drawPath(
                            path = path,
                            color = DirtTrailColor.copy(alpha = 0.88f),
                            style = Stroke(
                                width = wTrail,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))
                            )
                        )
                    }
                }
            }

            // 1e. High Zoom (11.0x+): Parked Sports Cars & Royal Enfield Bullet 350 Spawns
            if (zoom >= 11.0f) {
                for (pc in CityWorldData.parkedCars) {
                    val cxP = sx(pc.x); val cyP = sy(pc.z)
                    if (cxP in -8f..(size.width + 8f) && cyP in -8f..(size.height + 8f)) {
                        drawCircle(color = VehicleRedDot, radius = 3.5f, center = Offset(cxP, cyP))
                    }
                }
                for (bk in CityWorldData.bulletBikes) {
                    val cxB = sx(bk.x); val cyB = sy(bk.z)
                    if (cxB in -8f..(size.width + 8f) && cyB in -8f..(size.height + 8f)) {
                        drawCircle(color = TacticalSilver, radius = 3.0f, center = Offset(cxB, cyB))
                    }
                }
            }

            // Player's Saved Parked Vehicle Pin (ALWAYS visible at any zoom so you never lose your car!)
            val savedCarX = telemetry.parkedCarX
            val savedCarZ = telemetry.parkedCarZ
            if (savedCarX != null && savedCarZ != null) {
                val pvx = sx(savedCarX)
                val pvz = sy(savedCarZ)
                drawCircle(color = EmeraldPulse.copy(alpha = 0.28f), radius = 12f, center = Offset(pvx, pvz))
                drawRoundRect(
                    color = EmeraldPulse,
                    topLeft = Offset(pvx - 6f, pvz - 6f),
                    size = Size(12f, 12f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(pvx - 6f, pvz - 6f),
                    size = Size(12f, 12f),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = 1.5f)
                )
            }

            // 1f. Player 360-degree Loaded Streaming Zone Ring
            val px = sx(telemetry.playerX)
            val pz = sy(telemetry.playerZ)
            val zoneRadPx = streamingRadiusMeters * scale
            if (zoneRadPx >= 6f) {
                drawCircle(color = Color.White.copy(alpha = 0.05f), radius = zoneRadPx, center = Offset(px, pz))
                drawCircle(color = Color.White.copy(alpha = 0.36f), radius = zoneRadPx, center = Offset(px, pz), style = Stroke(width = 1.1f))
            }

            // 1g. ZOOM-AWARE PROGRESSIVE DECLUTTERING WITH STRICT AABB LABEL COLLISION AVOIDANCE!
            val placedLabelBoxes = ArrayList<RectF>(72)

            fun tryDrawNonOverlappingLabel(
                text: String,
                centerX: Float,
                baselineY: Float,
                paint: Paint,
                padX: Float = 10f,
                padY: Float = 6f
            ): Boolean {
                val lines = text.split("\n")
                var maxW = 0f
                for (line in lines) {
                    val w = paint.measureText(line)
                    if (w > maxW) maxW = w
                }
                val lineH = paint.textSize * 1.16f
                val totalH = lineH * lines.size
                val rect = RectF(
                    centerX - maxW * 0.5f - padX,
                    baselineY - paint.textSize - padY,
                    centerX + maxW * 0.5f + padX,
                    baselineY - paint.textSize + totalH + padY
                )
                if (rect.right < 0f || rect.left > size.width || rect.bottom < 0f || rect.top > size.height) {
                    return false
                }
                for (i in placedLabelBoxes.indices) {
                    if (RectF.intersects(placedLabelBoxes[i], rect)) {
                        return false
                    }
                }
                placedLabelBoxes.add(rect)
                var curY = baselineY
                for (line in lines) {
                    drawContext.canvas.nativeCanvas.drawText(line, centerX, curY, paint)
                    curY += lineH
                }
                return true
            }

            // TIER 1: Low-to-Mid Zoom (0.25x - 1.55x) -> Mainland Regions & All 6 Massive Islands!
            if (zoom < 1.55f) {
                val titleAlpha = ((1.55f - zoom) / 0.35f).coerceIn(0f, 1f)
                regionTitlePaint.alpha = (titleAlpha * 248f).toInt()
                regionTitlePaint.textSize = (21f + (zoom - 0.25f) * 10f).coerceIn(20f, 32f)
                for (lbl in WorldMapData.islandMapLabels) {
                    tryDrawNonOverlappingLabel(
                        text = lbl.title,
                        centerX = sx(lbl.x),
                        baselineY = sy(lbl.z),
                        paint = regionTitlePaint,
                        padX = 12f,
                        padY = 8f
                    )
                }
            }

            // TIER 2: Medium-to-High Zoom (0.72x+) -> 50+ Regional Landmarks, Towns, Garages, Petrol Pumps & Dhabas!
            if (zoom >= 0.72f) {
                poiLabelPaint.textSize = (17f + (zoom - 0.72f) * 2.2f).coerceIn(17f, 24f)
                for (poi in WorldMapData.regionalPois) {
                    val isStreetServicePoi = poi.category == PoiCategory.GARAGE ||
                        poi.category == PoiCategory.PETROL_PUMP ||
                        poi.category == PoiCategory.DHABA
                    if (isStreetServicePoi && zoom < 1.45f) continue
                    val mx = sx(poi.x)
                    val my = sy(poi.z)
                    if (mx !in -24f..(size.width + 24f) || my !in -24f..(size.height + 24f)) continue

                    val discovered = telemetry.discoveredZoneIds.contains(poi.id)
                    val markerCol = if (discovered) EmeraldPulse else poiColor(poi.category)
                    val markerR = (4.5f + min(zoom, 4f) * 0.8f).coerceIn(4.5f, 7.8f)
                    drawCategoryMarker(poi.category, mx, my, markerR, markerCol)

                    tryDrawNonOverlappingLabel(
                        text = poi.name.uppercase(),
                        centerX = mx,
                        baselineY = my - markerR - 7f,
                        paint = poiLabelPaint,
                        padX = 10f,
                        padY = 6f
                    )
                }

                // Core Saini Metro District Names (shown when zoomed into Saini Metro)
                if (zoom >= 5.5f) {
                    for (z in CityZones.all) {
                        if (abs(z.x) > 480f || abs(z.z) > 450f) continue
                        val zx = sx(z.x)
                        val zy = sy(z.z)
                        if (zx !in -24f..(size.width + 24f) || zy !in -24f..(size.height + 24f)) continue
                        val discovered = telemetry.discoveredZoneIds.contains(z.id)
                        val zColor = if (discovered) EmeraldPulse else TacticalCyan
                        drawCircle(color = zColor.copy(alpha = 0.24f), radius = 8.5f, center = Offset(zx, zy))
                        drawCircle(color = zColor, radius = 4.6f, center = Offset(zx, zy))
                        tryDrawNonOverlappingLabel(
                            text = z.name.uppercase(),
                            centerX = zx,
                            baselineY = zy - 10f,
                            paint = poiLabelPaint,
                            padX = 12f,
                            padY = 8f
                        )
                    }
                }
            }

            // TIER 3: Street-Level High Zoom (11.5x+) -> Enterable Buildings, Shops & Airport sub-landmarks!
            if (zoom >= 11.5f) {
                subLabelPaint.textSize = (16f + (zoom - 11.5f) * 0.5f).coerceIn(16f, 23f)
                for (eb in enterables) {
                    val ex = sx(eb.centerX)
                    val ey = sy(eb.centerZ - eb.halfD) - 6f
                    tryDrawNonOverlappingLabel(eb.name, ex, ey, subLabelPaint, padX = 8f, padY = 5f)
                }
                shopLabelPaint.textSize = (15f + (zoom - 11.5f) * 0.4f).coerceIn(15f, 21f)
                for (shop in shops) {
                    val shx = sx(shop.x)
                    val shy = sy(shop.z - shop.depth * 0.5f) - 5f
                    tryDrawNonOverlappingLabel(shop.name, shx, shy, shopLabelPaint, padX = 7f, padY = 4f)
                }
                tryDrawNonOverlappingLabel("ATC TOWER", sx(CityWorldData.AIRPORT_TOWER_X), sy(CityWorldData.AIRPORT_TOWER_Z) - 10f, shopLabelPaint)
                tryDrawNonOverlappingLabel("JET HANGAR", sx(CityWorldData.AIRPORT_HANGAR_X), sy(CityWorldData.AIRPORT_HANGAR_Z) - 10f, shopLabelPaint)
                tryDrawNonOverlappingLabel("RUNWAY 01", sx(CityWorldData.AIRPORT_RUNWAY_X), sy(CityWorldData.AIRPORT_RUNWAY_Z), shopLabelPaint)
            }

            // 1h. Active Curved Highway GPS Route Line + Distance Pill + Checkmark Pin!
            if (waypointX != null && waypointZ != null) {
                val wx = sx(waypointX)
                val wz = sy(waypointZ)

                if (gpsRoutePoints.size >= 2) {
                    val routePath = Path().apply {
                        moveTo(sx(gpsRoutePoints[0].first), sy(gpsRoutePoints[0].second))
                        for (i in 1 until gpsRoutePoints.size) {
                            lineTo(sx(gpsRoutePoints[i].first), sy(gpsRoutePoints[i].second))
                        }
                    }
                    drawPath(
                        path = routePath,
                        color = TacticalAccent.copy(alpha = 0.30f),
                        style = Stroke(width = 8.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = routePath,
                        color = TacticalAccent,
                        style = Stroke(
                            width = 3.6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 8f))
                        )
                    )
                } else {
                    drawLine(
                        color = TacticalAccent,
                        start = Offset(px, pz),
                        end = Offset(wx, wz),
                        strokeWidth = 3.2f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 9f))
                    )
                }

                val midPt = if (gpsRoutePoints.size >= 3) {
                    gpsRoutePoints[gpsRoutePoints.size / 2]
                } else {
                    ((telemetry.playerX + waypointX) * 0.5f) to ((telemetry.playerZ + waypointZ) * 0.5f)
                }
                val midX = sx(midPt.first)
                val midY = sy(midPt.second)
                val distText = formatMapDistance(waypointDistMeters ?: 0)
                distBadgePaint.textSize = 19f
                val pillW = (distBadgePaint.measureText(distText) + 22f).coerceAtLeast(72f)
                val pillH = 26f
                drawRoundRect(
                    color = TacticalAccent,
                    topLeft = Offset(midX - pillW * 0.5f, midY - pillH * 0.5f),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawContext.canvas.nativeCanvas.drawText(distText, midX, midY + 6.5f, distBadgePaint)

                // Waypoint Checkmark Pin
                drawCircle(color = TacticalAccent.copy(alpha = 0.26f), radius = 20f, center = Offset(wx, wz))
                drawCircle(color = TacticalAccent, radius = 11f, center = Offset(wx, wz))
                drawCircle(color = Color.White, radius = 11f, center = Offset(wx, wz), style = Stroke(width = 1.8f))
                val tickPath = Path().apply {
                    moveTo(wx - 4.8f, wz + 0.4f)
                    lineTo(wx - 1.0f, wz + 4.2f)
                    lineTo(wx + 5.6f, wz - 4.0f)
                }
                drawPath(
                    path = tickPath,
                    color = Color.White,
                    style = Stroke(width = 2.6f, cap = StrokeCap.Round)
                )
            }

            // 1i. Player Heading Arrow (Crisp Ice Cyan + White stroke)
            val deg = -(telemetry.playerYawRad * 180f / PI.toFloat()) + 180f
            drawCircle(color = TacticalCyan.copy(alpha = 0.24f), radius = 13f, center = Offset(px, pz))
            rotate(degrees = deg, pivot = Offset(px, pz)) {
                val arrow = Path().apply {
                    moveTo(px, pz - 10f)
                    lineTo(px + 7.2f, pz + 7f)
                    lineTo(px, pz + 3f)
                    lineTo(px - 7.2f, pz + 7f)
                    close()
                }
                drawPath(arrow, color = TacticalCyan)
                drawPath(arrow, color = Color.White, style = Stroke(width = 1.5f))
            }
        }

        // 2. Compact Free Fire MAX Tactical Map Controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MapGlassPanel)
                        .border(0.8.dp, MapBorderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SAINI STATE • BHARAT MAP",
                        style = MaterialTheme.typography.labelMedium,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${telemetry.currentLocationLabel} • ${String.format("%.2f", zoom)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }

                if (waypointDistMeters != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TacticalAccent)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("map_waypoint_distance")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = "Waypoint Distance",
                            tint = TextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "GPS • ${formatMapDistance(waypointDistMeters)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Compact Top-Right Close Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MapGlassPanel)
                    .border(0.8.dp, MapBorderColor, RoundedCornerShape(6.dp))
                    .clickable(onClick = onDismiss)
                    .testTag("btn_close_city_map")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Map",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Right Side Compact Tool Rail
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                MapToolButton(
                    icon = Icons.Rounded.Add,
                    description = "Zoom In",
                    testTag = "btn_map_zoom_in",
                    onClick = { zoom = (zoom * 1.40f).coerceAtMost(MAX_MAP_ZOOM) }
                )
                MapToolButton(
                    icon = Icons.Rounded.Remove,
                    description = "Zoom Out",
                    testTag = "btn_map_zoom_out",
                    onClick = { zoom = (zoom / 1.40f).coerceAtLeast(MIN_MAP_ZOOM) }
                )
                MapToolButton(
                    icon = Icons.Rounded.MyLocation,
                    description = "Center on Player",
                    testTag = "btn_map_center_player",
                    onClick = {
                        centerWorldX = telemetry.playerX
                        centerWorldZ = telemetry.playerZ
                        val inMetro = abs(telemetry.playerX) < 520f && telemetry.playerZ in -500f..500f
                        zoom = if (inMetro) maxOf(zoom, 12.0f) else maxOf(zoom, 2.2f)
                    }
                )
                MapToolButton(
                    icon = Icons.Rounded.Public,
                    description = "Full Archipelago View",
                    testTag = "btn_map_full_world",
                    onClick = {
                        centerWorldX = 0f
                        centerWorldZ = 2000f
                        zoom = 0.27f
                    }
                )
                if (waypointX != null && waypointZ != null) {
                    MapToolButton(
                        icon = Icons.Rounded.DeleteOutline,
                        description = "Clear Pin",
                        accent = TacticalAccent,
                        testTag = "btn_map_clear_pin",
                        onClick = onClearWaypoint
                    )
                }
            }

            // Compact Bottom Legend Strip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MapGlassPanel)
                    .border(0.8.dp, MapBorderColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Tap to pin route • Pinch to zoom (50 km × 65 km • 6 Islands)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 9.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapLegendDot(color = HighwayFillColor, label = "Highway")
                    MapLegendDot(color = SeaBridgeCasingColor, label = "Bridge")
                    MapLegendDot(color = Color(0xFFFBBF24), label = "Landmark")
                    MapLegendDot(color = TacticalCyan, label = "District")
                    MapLegendDot(color = EmeraldPulse, label = "Garage")
                    MapLegendDot(color = Color(0xFFFB923C), label = "Petrol")
                    MapLegendDot(color = ShopMarkerTeal, label = "Dhaba/Shop")
                }
            }
        }
    }
}

@Composable
private fun MapToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    accent: Color = TextPrimary,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MapGlassPanel)
            .border(0.8.dp, MapBorderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = accent,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 8.5.sp
        )
    }
}
