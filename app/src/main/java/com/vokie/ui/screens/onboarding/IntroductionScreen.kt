package com.vokie.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme

@Composable
internal fun IntroductionScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    val colors = VokieTheme.colors

    Surface(color = colors.background, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Top Bar: Skip button on pages 0 and 1; 52dp spacer on page 2
            if (page < 2) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onFinish, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("SKIP", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    }
                }
            } else {
                Spacer(Modifier.height(52.dp))
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f),
                label = "onboarding_content",
            ) { targetPage ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DesignSpec.setupMargin),
                ) {
                    Spacer(Modifier.height(if (targetPage < 2) 26.dp else 0.dp))
                    Text(
                        listOf("Talk when there\nis no signal", "Everything runs\non your phone", "Set up once,\nthen just talk")[targetPage],
                        style = DesignSpec.introductionTitle,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        listOf(
                            "iTantra turns speech into text on your phone, sends it to nearby devices, and speaks it out loud on the other side.",
                            "Speech recognition and voice generation happen on this phone. No account, no tower, no data.",
                            "Set up once, in about a minute. After that iTantra opens straight to one button.",
                        )[targetPage],
                        style = DesignSpec.introductionBody,
                        color = colors.textSecondary,
                    )

                    if (targetPage == 0) {
                        // Value Screen Illustration: Hello <-> 5 animated voice bars <-> नमस्ते
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp)
                                .padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.border),
                                    shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp),
                                ) {
                                    Text("Hello", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), color = colors.textPrimary, fontSize = 17.sp)
                                }
                                val transition = rememberInfiniteTransition(label = "intro_voice_bars")
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val delays = listOf(0, 120, 240, 360, 480)
                                    repeat(5) { index ->
                                        val scale by transition.animateFloat(
                                            initialValue = 0.18f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(550, delayMillis = delays[index], easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse,
                                            ),
                                            label = "intro_bar_$index",
                                        )
                                        Box(
                                            Modifier
                                                .width(3.dp)
                                                .height(34.dp)
                                                .scale(scaleX = 1f, scaleY = scale)
                                                .background(colors.accent, RoundedCornerShape(2.dp)),
                                        )
                                    }
                                }
                                Surface(
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.border),
                                    shape = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp),
                                ) {
                                    Text("नमस्ते", Modifier.padding(horizontal = 18.dp, vertical = 14.dp), color = colors.textPrimary, fontSize = 17.sp)
                                }
                            }
                        }
                    } else if (targetPage == 1) {
                        // Capability Screen: 4 rows with 44dp icon boxes
                        Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            val facts = listOf(
                                Triple(Icons.Rounded.WifiOff, "Works fully offline", "Speech models live on your phone"),
                                Triple(Icons.Rounded.Lock, "Stays between devices", "Nothing leaves the local link"),
                                Triple(Icons.Rounded.Translate, "Ten Indian languages", "Speak yours, they hear theirs"),
                                Triple(Icons.Rounded.Hub, "Phone to phone", "Bluetooth or Wi-Fi Direct"),
                            )
                            facts.forEach { (icon, title, hint) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = colors.surface,
                                        border = BorderStroke(1.dp, colors.border),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(title, fontSize = 16.sp, lineHeight = 20.8.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                        Text(hint, fontSize = 13.sp, lineHeight = 18.2.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 3.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        // Commit Screen: 190dp pulse ring illustration
                        val transition = rememberInfiniteTransition(label = "commit_pulse")
                        val pulseProgress by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
                            label = "ring_pulse",
                        )
                        val pulseScale = 1f + pulseProgress * 0.9f
                        val pulseAlpha = (0.55f * (1f - (pulseProgress / 0.7f).coerceIn(0f, 1f)))

                        val dot1Alpha by transition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "dot1_alpha",
                        )
                        val dot2Alpha by transition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1000, delayMillis = 350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "dot2_alpha",
                        )
                        val dot3Alpha by transition.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(1000, delayMillis = 700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "dot3_alpha",
                        )

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp)
                                .padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                                // Outer static ring
                                Surface(Modifier.fillMaxSize(), color = Color.Transparent, shape = CircleShape, border = BorderStroke(1.dp, colors.border)) {}
                                // Middle ring (inset 34dp = 122dp size)
                                Surface(Modifier.size(122.dp), color = Color.Transparent, shape = CircleShape, border = BorderStroke(1.dp, VokiePalette.Divider)) {}
                                // Animated pulse ring (expands and fades out)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .scale(pulseScale)
                                        .alpha(pulseAlpha)
                                        .background(Color.Transparent, CircleShape),
                                ) {
                                    Surface(Modifier.fillMaxSize(), color = Color.Transparent, shape = CircleShape, border = BorderStroke(2.dp, colors.accent)) {}
                                }
                                // Center mic button (64dp, background #E8402B, icon 32dp white)
                                Surface(Modifier.size(64.dp), color = colors.accent, shape = CircleShape) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(32.dp))
                                    }
                                }
                                // 3 Green dots (12dp each, #3FA66E)
                                Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 8.dp).size(12.dp).alpha(dot1Alpha).background(colors.success, CircleShape))
                                Box(Modifier.align(Alignment.BottomStart).padding(bottom = 26.dp).size(12.dp).alpha(dot2Alpha).background(colors.success, CircleShape))
                                Box(Modifier.align(Alignment.BottomEnd).padding(bottom = 6.dp, end = 44.dp).size(12.dp).alpha(dot3Alpha).background(colors.success, CircleShape))
                            }
                        }
                    }
                }
            }

            // Bottom controls: Indicators and Action Button
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignSpec.setupMargin)
                    .padding(top = 12.dp, bottom = 30.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(3) { index ->
                        Box(
                            Modifier
                                .width(if (page == index) 22.dp else 4.dp)
                                .height(4.dp)
                                .background(if (page == index) colors.accent else colors.border, RoundedCornerShape(2.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { if (page == 2) onFinish() else page++ },
                    shape = RoundedCornerShape(DesignSpec.ctaRadius),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().heightIn(min = DesignSpec.ctaHeight),
                ) {
                    Text(if (page == 2) "Get started" else "Next", style = DesignSpec.action)
                }
            }
        }
    }
}

