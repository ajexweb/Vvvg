package com.example.ui.screens

import android.graphics.Paint
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.engine.BuildingStyle
import com.example.game.engine.CityWorldData
import com.example.game.model.CityZones
import com.example.game.model.GameTelemetry
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.SunsetGold
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val MapGlassPanel = Color(0xFF0E131B).copy(alpha = 0.88f)
private val MapBorderColor = Color.White.copy(alpha = 0.24f)
private val ShopMarkerTeal = Color(0xFF2DD4BF)
private val VehicleRedDot = Color(0xFFF87171)

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

    val enterables = remember { CityWorldData.createEnterableBuildings() }
    val shops = remember { CityWorldData.createRoadsideShops() }

    var zoom by remember { mutableFloatStateOf(1.15f) }
    var centerWorldX by remember { mutableFloatStateOf(telemetry.playerX * 0.45f) }
    var centerWorldZ by remember { mutableFloatStateOf(telemetry.playerZ * 0.45f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val waypointDistMeters = remember(waypointX, waypointZ, telemetry.playerX, telemetry.playerZ) {
        if (waypointX != null && waypointZ != null) {
            val dx = waypointX - telemetry.playerX
            val dz = waypointZ - telemetry.playerZ
            sqrt(dx * dx + dz * dz).roundToInt()
        } else null
    }

    val districtLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(5f, 0f, 1.5f, android.graphics.Color.BLACK)
        }
    }
    val subLabelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFFDE68A).toArgb()
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
            color = Color(0xFF0D1117).toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F16).copy(alpha = 0.96f))
            .testTag("modal_city_map")
    ) {
        // 1. Fullscreen Interactive Tactical City Canvas (Pinch-to-Zoom, Drag-to-Pan, Tap-to-Pin!)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        val baseScale = min(canvasSize.width, canvasSize.height).coerceAtLeast(1) / 1020f
                        val newZoom = (zoom * gestureZoom).coerceIn(0.80f, 5.5f)
                        val worldPerPx = 1f / (baseScale * newZoom)
                        centerWorldX = (centerWorldX - pan.x * worldPerPx).coerceIn(-490f, 490f)
                        centerWorldZ = (centerWorldZ - pan.y * worldPerPx).coerceIn(-490f, 470f)
                        zoom = newZoom
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val baseScale = min(canvasSize.width, canvasSize.height).coerceAtLeast(1) / 1020f
                        val scale = baseScale * zoom
                        val wx = (centerWorldX + (tapOffset.x - canvasSize.width * 0.5f) / scale).coerceIn(-475f, 475f)
                        val wz = (centerWorldZ + (tapOffset.y - canvasSize.height * 0.5f) / scale).coerceIn(-475f, 445f)
                        onSetWaypoint(wx, wz)
                    }
                }
        ) {
            val baseScale = min(size.width, size.height) / 1020f
            val scale = baseScale * zoom
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f

            fun sx(wx: Float): Float = cx + (wx - centerWorldX) * scale
            fun sy(wz: Float): Float = cy + (wz - centerWorldZ) * scale

            // Background Terrain Landmass (-500..500, -500..410)
            drawRect(
                color = Color(0xFF1A2822),
                topLeft = Offset(sx(-500f), sy(-500f)),
                size = Size(1000f * scale, 910f * scale)
            )

            // Subtle Tactical Grid Lines (Free Fire map coordinates feel)
            val gridColor = Color.White.copy(alpha = 0.055f)
            var gx = -400f
            while (gx <= 400f) {
                drawLine(gridColor, Offset(sx(gx), sy(-500f)), Offset(sx(gx), sy(480f)), strokeWidth = 1f)
                drawLine(gridColor, Offset(sx(-500f), sy(gx)), Offset(sx(500f), sy(gx)), strokeWidth = 1f)
                gx += 100f
            }

            // Ocean Bay, Sandy Shoreline, Waterfront Boardwalk & Wooden Pier
            val shoreY = sy(CityWorldData.WATERFRONT_Z)
            drawRect(
                color = Color(0xFF113554),
                topLeft = Offset(sx(-540f), shoreY),
                size = Size(1080f * scale, 220f * scale)
            )
            drawRect(
                color = Color(0xFF8B7355).copy(alpha = 0.72f),
                topLeft = Offset(sx(-500f), shoreY - 2f * scale),
                size = Size(1000f * scale, 5.5f * scale)
            )
            drawRect(
                color = Color(0xFF56606E),
                topLeft = Offset(sx(-480f), sy(CityWorldData.WATERFRONT_Z - 6.6f)),
                size = Size(960f * scale, 8.2f * scale)
            )
            // Harbor Dock Yards
            drawRect(
                color = Color(0xFF3E454E),
                topLeft = Offset(sx(72f), sy(369f)),
                size = Size(136f * scale, 30f * scale)
            )
            drawRect(
                color = Color(0xFF3E454E),
                topLeft = Offset(sx(-168f), sy(369f)),
                size = Size(96f * scale, 30f * scale)
            )
            // Wooden Pier
            drawRect(
                color = Color(0xFFA47344),
                topLeft = Offset(sx(-7f), shoreY),
                size = Size(14f * scale, 36f * scale)
            )

            // Emerald City Park (-96..-56, 56..96)
            drawRect(
                color = Color(0xFF1F5D30),
                topLeft = Offset(sx(-96f), sy(56f)),
                size = Size(40f * scale, 40f * scale)
            )

            // Saini Army Base Compound & Helipads
            drawRect(
                color = Color(0xFF3A3F38),
                topLeft = Offset(sx(CityWorldData.ARMY_BASE_CENTER_X - 62f), sy(CityWorldData.ARMY_BASE_CENTER_Z - 48f)),
                size = Size(124f * scale, 96f * scale)
            )
            for (hp in CityWorldData.helipads) {
                val hcx = sx(hp.x); val hcy = sy(hp.z)
                val hr = (hp.size * 0.45f * scale).coerceAtLeast(3f)
                drawCircle(Color(0xFF555E54), radius = hr, center = Offset(hcx, hcy))
                drawCircle(SunsetGold.copy(alpha = 0.75f), radius = hr * 0.72f, center = Offset(hcx, hcy), style = Stroke(width = 1.4f))
            }

            // Saini International Airport Apron & Runway
            val rx = CityWorldData.AIRPORT_RUNWAY_X
            val rz = CityWorldData.AIRPORT_RUNWAY_Z
            val rw = CityWorldData.AIRPORT_RUNWAY_WIDTH
            val rl = CityWorldData.AIRPORT_RUNWAY_LENGTH
            drawRect(
                color = Color(0xFF39414C),
                topLeft = Offset(sx(rx - 4f - (rw + 22f) * 0.5f), sy(rz - (rl + 10f) * 0.5f)),
                size = Size((rw + 22f) * scale, (rl + 10f) * scale)
            )
            drawRect(
                color = Color(0xFF1F242C),
                topLeft = Offset(sx(rx - rw * 0.5f), sy(rz - rl * 0.5f)),
                size = Size(rw * scale, rl * scale)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(sx(rx), sy(rz - rl * 0.44f)),
                end = Offset(sx(rx), sy(rz + rl * 0.44f)),
                strokeWidth = (1.6f * zoom).coerceIn(1.2f, 3.5f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )

            // Urban Colony Concrete/Paver Pads
            val padColor = Color(0xFF26303B)
            for (pad in CityWorldData.colonyPads) {
                drawRect(
                    color = padColor,
                    topLeft = Offset(sx(pad.x - pad.width * 0.5f), sy(pad.z - pad.depth * 0.5f)),
                    size = Size(pad.width * scale, pad.depth * scale)
                )
            }

            // Narrow Colony Alleys ("Patli Galiyan") — revealed cleanly when zooming in (>= 1.25x)!
            if (zoom >= 1.25f) {
                val alleyColor = Color(0xFF475262)
                for (al in CityWorldData.colonyAlleys) {
                    val aw = if (al.isVertical) al.width else al.length
                    val ad = if (al.isVertical) al.length else al.width
                    drawRect(
                        color = alleyColor,
                        topLeft = Offset(sx(al.x - aw * 0.5f), sy(al.z - ad * 0.5f)),
                        size = Size(aw * scale, ad * scale)
                    )
                }
            }

            // Main City Road Grid & 4-Way Intersections
            val mainRoadColor = Color(0xFF4A5566)
            for (r in CityWorldData.roadSegments) {
                val rwSeg = if (r.isVertical) r.width else r.length
                val rdSeg = if (r.isVertical) r.length else r.width
                drawRect(
                    color = mainRoadColor,
                    topLeft = Offset(sx(r.x - rwSeg * 0.5f), sy(r.z - rdSeg * 0.5f)),
                    size = Size(rwSeg * scale, rdSeg * scale)
                )
            }
            for (ix in CityWorldData.intersections) {
                drawRect(
                    color = Color(0xFF546072),
                    topLeft = Offset(sx(ix.x - ix.sizeX * 0.5f), sy(ix.z - ix.sizeZ * 0.5f)),
                    size = Size(ix.sizeX * scale, ix.sizeZ * scale)
                )
            }

            // City Buildings (color-coded subtly by style for realistic GTA tactical map clarity!)
            for (b in CityWorldData.buildings) {
                val left = sx(b.x - b.width * 0.5f)
                val top = sy(b.z - b.depth * 0.5f)
                val bw = b.width * scale
                val bh = b.depth * scale
                if (left + bw < -10f || left > size.width + 10f || top + bh < -10f || top > size.height + 10f) continue
                val bCol = when (b.style) {
                    BuildingStyle.GLASS_SKYSCRAPER -> Color(0xFF5D7690)
                    BuildingStyle.APARTMENT_WINDOWS -> Color(0xFF7B8088)
                    BuildingStyle.RED_BRICK_HOUSE -> Color(0xFF8C6255)
                    BuildingStyle.FACTORY_INDUSTRIAL -> Color(0xFF636B73)
                }
                drawRect(color = bCol, topLeft = Offset(left, top), size = Size(bw, bh))
                if (zoom >= 1.6f) {
                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(left, top),
                        size = Size(bw, bh),
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Enterable Buildings (Gold Tactical Outlines)
            for (eb in enterables) {
                val left = sx(eb.centerX - eb.halfW)
                val top = sy(eb.centerZ - eb.halfD)
                val sz = Size(eb.width * scale, eb.depth * scale)
                drawRect(color = SunsetGold.copy(alpha = 0.32f), topLeft = Offset(left, top), size = sz)
                drawRect(color = SunsetGold, topLeft = Offset(left, top), size = sz, style = Stroke(width = 1.8f))
            }

            // Roadside Shops (Teal Diamonds/Rects when zoomed >= 1.4x)
            if (zoom >= 1.4f) {
                for (shop in shops) {
                    val left = sx(shop.x - shop.width * 0.5f)
                    val top = sy(shop.z - shop.depth * 0.5f)
                    val sz = Size(shop.width * scale, shop.depth * scale)
                    drawRect(color = ShopMarkerTeal.copy(alpha = 0.38f), topLeft = Offset(left, top), size = sz)
                    drawRect(color = ShopMarkerTeal, topLeft = Offset(left, top), size = sz, style = Stroke(width = 1.5f))
                }
            }

            // Parked Cars & Bullet 350 Motorcycles (visible on high detail zoom >= 2.1x!)
            if (zoom >= 2.1f) {
                for (pc in CityWorldData.parkedCars) {
                    drawCircle(color = VehicleRedDot, radius = (3.2f).coerceAtLeast(2.2f), center = Offset(sx(pc.x), sy(pc.z)))
                }
                for (bk in CityWorldData.bulletBikes) {
                    drawCircle(color = Color(0xFFFBBF24), radius = 2.8f, center = Offset(sx(bk.x), sy(bk.z)))
                }
            }

            // Player 360-degree Loaded Streaming Zone Circle
            val px = sx(telemetry.playerX)
            val pz = sy(telemetry.playerZ)
            val zoneRadPx = streamingRadiusMeters * scale
            drawCircle(color = Color.White.copy(alpha = 0.06f), radius = zoneRadPx, center = Offset(px, pz))
            drawCircle(color = Color.White.copy(alpha = 0.42f), radius = zoneRadPx, center = Offset(px, pz), style = Stroke(width = 1.3f))

            // Exploration Zones & Major District Names (ALWAYS visible, even without zoom!)
            districtLabelPaint.textSize = (24f + (zoom - 1f) * 3.5f).coerceIn(22f, 34f)
            for (z in CityZones.all) {
                val zx = sx(z.x)
                val zy = sy(z.z)
                val discovered = telemetry.discoveredZoneIds.contains(z.id)
                drawCircle(
                    color = (if (discovered) EmeraldPulse else SunsetGold).copy(alpha = 0.24f),
                    radius = (6.5f * zoom.coerceAtMost(2.2f)),
                    center = Offset(zx, zy)
                )
                drawCircle(
                    color = if (discovered) EmeraldPulse else SunsetGold,
                    radius = 4.2f,
                    center = Offset(zx, zy)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    z.name.uppercase(),
                    zx,
                    zy - 10f,
                    districtLabelPaint
                )
            }

            // Medium Zoom (>= 1.45x): Enterable Building Names
            if (zoom >= 1.45f) {
                subLabelPaint.textSize = (18f + (zoom - 1.45f) * 3f).coerceIn(18f, 26f)
                for (eb in enterables) {
                    val ex = sx(eb.centerX)
                    val ey = sy(eb.centerZ - eb.halfD) - 7f
                    if (ex in -80f..(size.width + 80f) && ey in -40f..(size.height + 40f)) {
                        drawContext.canvas.nativeCanvas.drawText(eb.name, ex, ey, subLabelPaint)
                    }
                }
            }

            // High Zoom (>= 2.15x): Roadside Shop Names + Airport/Army Sub-Landmark Labels
            if (zoom >= 2.15f) {
                shopLabelPaint.textSize = (17f + (zoom - 2.15f) * 2.5f).coerceIn(17f, 24f)
                for (shop in shops) {
                    val shx = sx(shop.x)
                    val shy = sy(shop.z - shop.depth * 0.5f) - 6f
                    if (shx in -80f..(size.width + 80f) && shy in -40f..(size.height + 40f)) {
                        drawContext.canvas.nativeCanvas.drawText(shop.name, shx, shy, shopLabelPaint)
                    }
                }
                drawContext.canvas.nativeCanvas.drawText("ATC TOWER", sx(CityWorldData.AIRPORT_TOWER_X), sy(CityWorldData.AIRPORT_TOWER_Z) - 10f, shopLabelPaint)
                drawContext.canvas.nativeCanvas.drawText("JET HANGAR", sx(CityWorldData.AIRPORT_HANGAR_X), sy(CityWorldData.AIRPORT_HANGAR_Z) - 10f, shopLabelPaint)
                drawContext.canvas.nativeCanvas.drawText("RUNWAY 01", sx(rx), sy(rz), shopLabelPaint)
            }

            // Active Waypoint Tick Mark + Dashed Tactical Route Line from Player -> Destination!
            if (waypointX != null && waypointZ != null) {
                val wx = sx(waypointX)
                val wz = sy(waypointZ)

                // Soft glowing outer path + crisp dashed golden navigation line
                drawLine(
                    color = SunsetGold.copy(alpha = 0.28f),
                    start = Offset(px, pz),
                    end = Offset(wx, wz),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = SunsetGold,
                    start = Offset(px, pz),
                    end = Offset(wx, wz),
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
                )

                // Distance pill along route midpoint
                val midX = (px + wx) * 0.5f
                val midY = (pz + wz) * 0.5f
                val distText = "${waypointDistMeters ?: 0}m"
                distBadgePaint.textSize = 22f
                val pillW = 76f
                val pillH = 30f
                drawRoundRect(
                    color = SunsetGold,
                    topLeft = Offset(midX - pillW * 0.5f, midY - pillH * 0.5f),
                    size = Size(pillW, pillH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                )
                drawContext.canvas.nativeCanvas.drawText(distText, midX, midY + 8f, distBadgePaint)

                // Free Fire style Waypoint Pin + Checkmark ("Tick Mark")
                drawCircle(color = SunsetGold.copy(alpha = 0.22f), radius = 22f, center = Offset(wx, wz))
                drawCircle(color = SunsetGold, radius = 12f, center = Offset(wx, wz))
                drawCircle(color = Color.White, radius = 12f, center = Offset(wx, wz), style = Stroke(width = 2f))
                // Crisp checkmark tick inside the golden waypoint circle
                val tickPath = Path().apply {
                    moveTo(wx - 5.2f, wz + 0.4f)
                    lineTo(wx - 1.2f, wz + 4.6f)
                    lineTo(wx + 6.0f, wz - 4.4f)
                }
                drawPath(
                    path = tickPath,
                    color = CityObsidian,
                    style = Stroke(width = 3.0f, cap = StrokeCap.Round)
                )
            }

            // Player Heading Arrow (Always sharp & clear)
            val deg = -(telemetry.playerYawRad * 180f / PI.toFloat()) + 180f
            drawCircle(color = Color.White.copy(alpha = 0.25f), radius = 14f, center = Offset(px, pz))
            rotate(degrees = deg, pivot = Offset(px, pz)) {
                val arrow = Path().apply {
                    moveTo(px, pz - 11f)
                    lineTo(px + 8f, pz + 7.5f)
                    lineTo(px, pz + 3.2f)
                    lineTo(px - 8f, pz + 7.5f)
                    close()
                }
                drawPath(arrow, color = SunsetGold)
                drawPath(arrow, color = Color.White, style = Stroke(width = 1.6f))
            }
        }

        // 2. Clean Tactical Map Top Header, Zoom Controls & Bottom Legend
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp)
        ) {
            // Top-Left: Map Title, Current District & Active Waypoint Distance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MapGlassPanel)
                        .border(1.dp, MapBorderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "SAINI CITY TACTICAL MAP",
                        style = MaterialTheme.typography.labelLarge,
                        color = SunsetGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${telemetry.currentLocationLabel}  •  Zoom ${String.format("%.1f", zoom)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (waypointDistMeters != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SunsetGold)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("map_waypoint_distance")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Place,
                            contentDescription = "Waypoint Distance",
                            tint = CityObsidian,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "PINNED • ${waypointDistMeters}m",
                            style = MaterialTheme.typography.labelMedium,
                            color = CityObsidian,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Top-Right: Close Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MapGlassPanel)
                    .border(1.dp, MapBorderColor, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .testTag("btn_close_city_map")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Map",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Right Side: Zoom In (+), Zoom Out (-), Center on Player, Clear Pin
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                MapToolButton(
                    icon = Icons.Rounded.Add,
                    description = "Zoom In",
                    testTag = "btn_map_zoom_in",
                    onClick = { zoom = (zoom * 1.38f).coerceAtMost(5.5f) }
                )
                MapToolButton(
                    icon = Icons.Rounded.Remove,
                    description = "Zoom Out",
                    testTag = "btn_map_zoom_out",
                    onClick = { zoom = (zoom / 1.38f).coerceAtLeast(0.80f) }
                )
                MapToolButton(
                    icon = Icons.Rounded.MyLocation,
                    description = "Center on Player",
                    testTag = "btn_map_center_player",
                    onClick = {
                        centerWorldX = telemetry.playerX
                        centerWorldZ = telemetry.playerZ
                        if (zoom < 1.8f) zoom = 2.0f
                    }
                )
                if (waypointX != null && waypointZ != null) {
                    MapToolButton(
                        icon = Icons.Rounded.DeleteOutline,
                        description = "Clear Pin",
                        accent = SunsetGold,
                        testTag = "btn_map_clear_pin",
                        onClick = onClearWaypoint
                    )
                }
            }

            // Bottom Bar: Interactive Hint & Tactical Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MapGlassPanel)
                    .border(1.dp, MapBorderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Tap anywhere to pin route  •  Pinch to zoom alleys & shops",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    fontSize = 11.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MapLegendDot(color = SunsetGold, label = "Enterable")
                    MapLegendDot(color = ShopMarkerTeal, label = "Shop")
                    MapLegendDot(color = EmeraldPulse, label = "Zone")
                    MapLegendDot(color = VehicleRedDot, label = "Vehicle")
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
            .size(48.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(MapGlassPanel)
            .border(1.dp, MapBorderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}
