package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CityBorder
import com.example.ui.theme.CityObsidian
import com.example.ui.theme.CitySlate
import com.example.ui.theme.TacticalAccent
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalSilver
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Full-artwork splash/loading backdrop:
 * - Outer side margins get a rich blurred blue ambient glow derived from the artwork
 * - Center foreground renders 100% of the artwork with ContentScale.Fit (zero cropping/zoom)
 */
@Composable
fun FullArtworkBlueGlowBackdrop(
    drawableRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF061124))
    ) {
        // 1. Ambient blurred side-fill backdrop (fills any widescreen side margins softly)
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(26.dp)
                .alpha(0.52f)
        )

        // 2. Deep electric-blue atmospheric glow on left & right margins (works cleanly on all Android APIs)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color(0xFF0A2A5E).copy(alpha = 0.86f),
                        0.18f to Color(0xFF0D4E8A).copy(alpha = 0.56f),
                        0.50f to Color(0xFF051329).copy(alpha = 0.25f),
                        0.82f to Color(0xFF0D4E8A).copy(alpha = 0.56f),
                        1.0f to Color(0xFF0A2A5E).copy(alpha = 0.86f)
                    )
                )
        )

        // 3. Complete uncropped artwork centered with ContentScale.Fit so 100% of the image is visible
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // 4. Subtle bottom-only gradient so progress bar & text stay crisp without hiding artwork
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF040B16).copy(alpha = 0.24f),
                        0.66f to Color.Transparent,
                        0.86f to Color(0xFF040B16).copy(alpha = 0.72f),
                        1.0f to Color(0xFF040B16).copy(alpha = 0.94f)
                    )
                )
        )
    }
}

@Composable
fun SplashScreen(
    progress: Float,
    statusText: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "splash_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CityObsidian)
            .testTag("splash_screen")
    ) {
        FullArtworkBlueGlowBackdrop(
            drawableRes = R.drawable.img_saini_splash_2,
            contentDescription = "Saini Simulator Splash Screen"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CityObsidian.copy(alpha = 0.76f))
                        .border(0.8.dp, TacticalCyan.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 9.dp, vertical = 3.5.dp)
                        .testTag("splash_title")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationCity,
                        contentDescription = null,
                        tint = TacticalCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.size(5.dp))
                    Text(
                        text = "OPEN WORLD 3D EDITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalSilver,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TacticalCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CitySlate.copy(alpha = 0.90f))
                        .border(0.6.dp, CityBorder, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress.coerceAtLeast(0.03f))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(TacticalCyan, TacticalAccent, Color.White)
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "Developed by Ajay Saini (@ajayzsaini)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    modifier = Modifier.testTag("splash_developer_credit")
                )
            }
        }
    }
}
