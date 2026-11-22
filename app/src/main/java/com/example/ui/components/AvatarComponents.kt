package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.model.GameAvatar
import com.example.game.model.GameAvatars
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityCard
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.TacticalAccent
import com.example.ui.theme.TacticalAccentDark
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Crisp Free Fire MAX style Tactical Vector Avatar rendered via Canvas (replaces old face photo).
 */
@Composable
fun TacticalAvatar(
    avatarId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    borderWidth: Dp = 1.dp,
    selected: Boolean = false
) {
    val avatar: GameAvatar = remember(avatarId) { GameAvatars.byId(avatarId) }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(avatar.badgeBg, CityObsidian)
                )
            )
            .border(
                width = if (selected) borderWidth + 0.8.dp else borderWidth,
                color = if (selected) Color.White else avatar.primaryColor.copy(alpha = 0.85f),
                shape = shape
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val cy = h * 0.5f
            val p = avatar.primaryColor
            val a = avatar.accentColor

            // Subtle diagonal tech accent stripe in background
            drawLine(
                color = p.copy(alpha = 0.16f),
                start = Offset(w * 0.1f, h * 0.9f),
                end = Offset(w * 0.9f, h * 0.1f),
                strokeWidth = w * 0.16f
            )

            // Stylized Operator Shoulders / Tactical Collar (common silhouette base)
            val shoulders = Path().apply {
                moveTo(w * 0.14f, h * 0.96f)
                lineTo(w * 0.24f, h * 0.72f)
                lineTo(w * 0.76f, h * 0.72f)
                lineTo(w * 0.86f, h * 0.96f)
                close()
            }
            drawPath(shoulders, color = a.copy(alpha = 0.85f))
            drawPath(shoulders, color = p.copy(alpha = 0.45f), style = Stroke(width = w * 0.03f))

            when (avatar.styleIndex) {
                0 -> {
                    // Viper: Tactical Spec-Ops Helmet + Glowing Cyan Visor
                    val helmet = Path().apply {
                        moveTo(cx, h * 0.16f)
                        lineTo(w * 0.72f, h * 0.30f)
                        lineTo(w * 0.68f, h * 0.64f)
                        lineTo(cx, h * 0.72f)
                        lineTo(w * 0.32f, h * 0.64f)
                        lineTo(w * 0.28f, h * 0.30f)
                        close()
                    }
                    drawPath(helmet, color = Color(0xFF1A2535))
                    drawPath(helmet, color = p, style = Stroke(width = w * 0.04f))
                    val visor = Path().apply {
                        moveTo(w * 0.31f, h * 0.41f)
                        lineTo(w * 0.69f, h * 0.41f)
                        lineTo(w * 0.63f, h * 0.52f)
                        lineTo(cx, h * 0.48f)
                        lineTo(w * 0.37f, h * 0.52f)
                        close()
                    }
                    drawPath(visor, color = p)
                }
                1 -> {
                    // Phantom: Crimson Hooded Mask + Sharp Eyes
                    val hood = Path().apply {
                        moveTo(cx, h * 0.13f)
                        lineTo(w * 0.78f, h * 0.36f)
                        lineTo(w * 0.70f, h * 0.68f)
                        lineTo(w * 0.30f, h * 0.68f)
                        lineTo(w * 0.22f, h * 0.36f)
                        close()
                    }
                    drawPath(hood, color = Color(0xFF1F1118))
                    drawPath(hood, color = p, style = Stroke(width = w * 0.04f))
                    drawLine(p, Offset(w * 0.34f, h * 0.42f), Offset(w * 0.46f, h * 0.46f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                    drawLine(p, Offset(w * 0.66f, h * 0.42f), Offset(w * 0.54f, h * 0.46f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                    val maskV = Path().apply {
                        moveTo(w * 0.38f, h * 0.56f)
                        lineTo(cx, h * 0.64f)
                        lineTo(w * 0.62f, h * 0.56f)
                    }
                    drawPath(maskV, color = Color.White.copy(alpha = 0.75f), style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
                }
                2 -> {
                    // Apex: Recon Cap + Dual Tactical Goggles
                    drawCircle(color = Color(0xFF182825), radius = w * 0.23f, center = Offset(cx, h * 0.45f))
                    drawArc(
                        color = p,
                        startAngle = 195f,
                        sweepAngle = 150f,
                        useCenter = true,
                        topLeft = Offset(w * 0.24f, h * 0.18f),
                        size = Size(w * 0.52f, h * 0.36f)
                    )
                    drawCircle(color = p, radius = w * 0.09f, center = Offset(w * 0.40f, h * 0.45f))
                    drawCircle(color = p, radius = w * 0.09f, center = Offset(w * 0.60f, h * 0.45f))
                    drawCircle(color = CityObsidian, radius = w * 0.04f, center = Offset(w * 0.40f, h * 0.45f))
                    drawCircle(color = CityObsidian, radius = w * 0.04f, center = Offset(w * 0.60f, h * 0.45f))
                }
                3 -> {
                    // Falcon: Pilot Flight Helmet + Wing Crest
                    drawCircle(color = Color(0xFF162238), radius = w * 0.25f, center = Offset(cx, h * 0.44f))
                    drawCircle(color = p, radius = w * 0.25f, center = Offset(cx, h * 0.44f), style = Stroke(width = w * 0.04f))
                    drawRoundRect(
                        color = p,
                        topLeft = Offset(w * 0.30f, h * 0.36f),
                        size = Size(w * 0.40f, h * 0.14f),
                        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
                    )
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(w * 0.35f, h * 0.40f), Offset(w * 0.52f, h * 0.40f), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
                }
                4 -> {
                    // Nova: Cyber Star Visor + Headset
                    val hex = Path().apply {
                        moveTo(cx, h * 0.17f)
                        lineTo(w * 0.74f, h * 0.32f)
                        lineTo(w * 0.74f, h * 0.58f)
                        lineTo(cx, h * 0.71f)
                        lineTo(w * 0.26f, h * 0.58f)
                        lineTo(w * 0.26f, h * 0.32f)
                        close()
                    }
                    drawPath(hex, color = Color(0xFF23153A))
                    drawPath(hex, color = p, style = Stroke(width = w * 0.04f))
                    drawLine(p, Offset(w * 0.32f, h * 0.44f), Offset(w * 0.68f, h * 0.44f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                    drawCircle(Color.White, radius = w * 0.045f, center = Offset(cx, h * 0.44f))
                }
                5 -> {
                    // Ronin: Samurai Kabuto Crest + Oni Half-Mask
                    val kabuto = Path().apply {
                        moveTo(w * 0.22f, h * 0.40f)
                        lineTo(cx, h * 0.16f)
                        lineTo(w * 0.78f, h * 0.40f)
                        lineTo(w * 0.68f, h * 0.66f)
                        lineTo(cx, h * 0.72f)
                        lineTo(w * 0.32f, h * 0.66f)
                        close()
                    }
                    drawPath(kabuto, color = Color(0xFF25121B))
                    drawPath(kabuto, color = p, style = Stroke(width = w * 0.04f))
                    val horn = Path().apply {
                        moveTo(w * 0.32f, h * 0.22f)
                        lineTo(cx, h * 0.32f)
                        lineTo(w * 0.68f, h * 0.22f)
                    }
                    drawPath(horn, color = Color.White, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round))
                    drawRect(color = p, topLeft = Offset(w * 0.34f, h * 0.44f), size = Size(w * 0.32f, h * 0.08f))
                }
                6 -> {
                    // Titan: Heavy Armored Juggernaut Helmet
                    drawRoundRect(
                        color = Color(0xFF1D2633),
                        topLeft = Offset(w * 0.25f, h * 0.18f),
                        size = Size(w * 0.50f, h * 0.52f),
                        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
                    )
                    drawRoundRect(
                        color = p,
                        topLeft = Offset(w * 0.25f, h * 0.18f),
                        size = Size(w * 0.50f, h * 0.52f),
                        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                        style = Stroke(width = w * 0.045f)
                    )
                    drawRect(color = TacticalCyan, topLeft = Offset(w * 0.32f, h * 0.38f), size = Size(w * 0.36f, h * 0.09f))
                    drawLine(p, Offset(w * 0.38f, h * 0.56f), Offset(w * 0.62f, h * 0.56f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
                }
                else -> {
                    // Cyber: Futuristic Exo-Skull + Dual Neon Bars
                    val skull = Path().apply {
                        moveTo(cx, h * 0.15f)
                        lineTo(w * 0.72f, h * 0.30f)
                        lineTo(w * 0.65f, h * 0.68f)
                        lineTo(w * 0.35f, h * 0.68f)
                        lineTo(w * 0.28f, h * 0.30f)
                        close()
                    }
                    drawPath(skull, color = Color(0xFF102A2B))
                    drawPath(skull, color = p, style = Stroke(width = w * 0.04f))
                    drawCircle(p, radius = w * 0.075f, center = Offset(w * 0.41f, h * 0.43f))
                    drawCircle(p, radius = w * 0.075f, center = Offset(w * 0.59f, h * 0.43f))
                    drawLine(Color.White, Offset(w * 0.42f, h * 0.58f), Offset(w * 0.58f, h * 0.58f), strokeWidth = w * 0.045f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/**
 * First-Time Launch Onboarding Modal: asks player for their Username & Avatar once.
 */
@Composable
fun UsernameOnboardingOverlay(
    initialAvatarId: String,
    onComplete: (username: String, avatarId: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var selectedAvatarId by remember { mutableStateOf(initialAvatarId) }
    val trimmed = username.trim()
    val isValid = trimmed.length >= 2

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF06090F).copy(alpha = 0.90f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("modal_username_onboarding")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CitySlate)
                .border(1.dp, TacticalCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TacticalAccent)
                )
                Column {
                    Text(
                        text = "WELCOME TO SAINI SIMULATOR",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.0.sp
                    )
                    Text(
                        text = "Enter your Player Name & choose your Tactical Avatar",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TacticalAvatar(
                    avatarId = selectedAvatarId,
                    size = 56.dp,
                    cornerRadius = 6.dp,
                    borderWidth = 1.4.dp,
                    selected = true
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "USERNAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CityObsidian)
                            .border(
                                1.dp,
                                if (isValid) TacticalCyan else CityBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .padding(horizontal = 10.dp)
                    ) {
                        if (username.isEmpty()) {
                            Text(
                                text = "Type your name (e.g. AjaySaini)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                        BasicTextField(
                            value = username,
                            onValueChange = { next ->
                                if (next.length <= 16) username = next.replace("\n", "")
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            cursorBrush = SolidColor(TacticalCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_onboarding_username")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "SELECT AVATAR",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(5.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GameAvatars.all.forEach { av ->
                    val active = av.id == selectedAvatarId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (active) TacticalCyan.copy(alpha = 0.16f) else CityCard)
                            .border(
                                0.9.dp,
                                if (active) TacticalCyan else CityBorder,
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { selectedAvatarId = av.id }
                            .padding(vertical = 4.dp)
                            .testTag("onboarding_avatar_${av.id}")
                    ) {
                        TacticalAvatar(
                            avatarId = av.id,
                            size = 32.dp,
                            cornerRadius = 4.dp,
                            selected = active
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = av.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) TacticalCyan else TextSecondary,
                            fontSize = 7.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (isValid) {
                            Brush.horizontalGradient(listOf(TacticalAccent, TacticalAccentDark))
                        } else {
                            Brush.horizontalGradient(listOf(CityCard, CityCard))
                        }
                    )
                    .border(
                        0.9.dp,
                        if (isValid) Color.White.copy(alpha = 0.25f) else CityBorder,
                        RoundedCornerShape(5.dp)
                    )
                    .clickable(enabled = isValid) { onComplete(trimmed, selectedAvatarId) }
                    .testTag("btn_confirm_username")
            ) {
                Text(
                    text = if (isValid) "ENTER SAINI CITY" else "ENTER AT LEAST 2 CHARACTERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isValid) TextPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )
            }
        }
    }
}
