package com.voicecommand.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.voicecommand.app.MainViewModel.UiState

@Composable
fun MainScreen(
    uiState: UiState,
    statusText: String,
    resultText: String,
    commandText: String?,
    errorText: String?,
    pendingCommandLabel: String?,
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    missingA11y: Boolean = false,
    missingBattery: Boolean = false,
    onFixA11y: () -> Unit = {},
    onFixBattery: () -> Unit = {},
) {
    var showSettings by remember { mutableStateOf(false) }
    var warningExpanded by remember { mutableStateOf(false) }
    var commandsExpanded by remember { mutableStateOf(false) }
    val hasWarning = missingA11y || missingBattery

    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    val isActive = uiState == UiState.PROCESSING || uiState == UiState.EXECUTING || uiState == UiState.ERROR

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GupTa",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                // Warning icon — shown when a permission is missing
                if (hasWarning) {
                    IconButton(onClick = { warningExpanded = !warningExpanded }) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Permissions warning",
                            tint = Color(0xFFFFA000)
                        )
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    )
                }
            }

            // ── Expandable permission warning banner ───────────────────────────
            AnimatedVisibility(
                visible = warningExpanded && hasWarning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                PermissionWarningBanner(
                    missingBattery = missingBattery,
                    missingA11y = missingA11y,
                    onFixBattery = onFixBattery,
                    onFixA11y = onFixA11y
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Command structure — collapsible, shown only when idle
                if (commandText == null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Toggle row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "COMMAND STRUCTURE",
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                )
                                if (!commandsExpanded) {
                                    Text(
                                        text = "Tap to see how to call commands",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { commandsExpanded = !commandsExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (commandsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (commandsExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = commandsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            ExamplesCard()
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Wake-word hero ────────────────────────────────────────────
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SAY",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Light,
                            letterSpacing = 6.sp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.65f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "HI GUPTA",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = FontFamily.Cursive,
                            letterSpacing = 3.sp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                // ── Heartbeat with ring ───────────────────────────────────────
                val ringColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = ringColor.copy(alpha = glowAlpha * 0.18f),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = ringColor.copy(alpha = glowAlpha * 0.08f),
                            radius = size.minDimension / 2f - 14.dp.toPx(),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    HeartbeatSignal(
                        isActive = isActive,
                        glowAlpha = glowAlpha,
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(72.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Status text ───────────────────────────────────────────────
                val displayStatus = when {
                    statusText == "Say 'Hi Gupta'…" -> ""
                    statusText == "Try again" -> ""
                    else -> statusText
                }
                if (displayStatus.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (uiState == UiState.ERROR)
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = displayStatus,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState == UiState.ERROR)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Result / feedback cards ───────────────────────────────────
                if (commandText != null) {
                    val isMatched = commandText.startsWith("Matched") || commandText.startsWith("Running")
                    val cardColor = if (isMatched) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    val cardBg = if (isMatched)
                        Color(0xFF2E7D32).copy(alpha = 0.10f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = cardColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = commandText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = cardColor
                            )
                        }
                    }
                }

                if (pendingCommandLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFA000).copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.30f))
                    ) {
                        Text(
                            text = "Queued: $pendingCommandLabel — unlock phone to execute",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE65100),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }

                if (resultText.isNotEmpty() && commandText == null) {
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }

    // ── Settings dialog ────────────────────────────────────────────────────────
    if (showSettings) {
        val context = LocalContext.current
        var url by remember(serverUrl) { mutableStateOf(serverUrl) }
        var scanError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showSettings = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "LAPTOP IPv4 / TAILSCALE IP : PORT",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter your laptop's IP and port, or scan the QR code shown in the laptop app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; scanError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server Address") },
                        placeholder = { Text("e.g. 192.168.x.x:8765") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = {
                                scanError = null
                                val options = GmsBarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .build()
                                GmsBarcodeScanning.getClient(context, options)
                                    .startScan()
                                    .addOnSuccessListener { barcode ->
                                        val raw = barcode.rawValue ?: return@addOnSuccessListener
                                        // QR format: "host:port" (legacy) or "host:port|token|aeskey" (secure)
                                        // Pass the full raw value to updateServerUrl so it can extract
                                        // token+key. Show only the host:port part in the text field.
                                        val cleaned = raw.trim()
                                            .removePrefix("ws://")
                                            .removePrefix("wss://")
                                            .removePrefix("tcp://")
                                        url = cleaned.substringBefore("|")
                                        onUpdateServerUrl(cleaned)
                                    }
                                    .addOnFailureListener { e ->
                                        if (e.message?.contains("CANCELED") == false) {
                                            scanError = "Scan failed: ${e.message}"
                                        }
                                    }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR code",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )

                    if (scanError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = scanError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showSettings = false
                                onUpdateServerUrl(url.trim())
                            },
                            enabled = url.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    missingBattery: Boolean,
    missingA11y: Boolean,
    onFixBattery: () -> Unit,
    onFixA11y: () -> Unit
) {
    val amber = Color(0xFFFFA000)
    val amberDark = Color(0xFFE65100)
    val amberBg = Color(0xFFFFF8E1)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = amberBg
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = amber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gupta needs these permissions to run in the background",
                    style = MaterialTheme.typography.labelMedium,
                    color = amberDark
                )
            }

            if (missingBattery) {
                Spacer(modifier = Modifier.height(12.dp))
                PermissionRow(
                    title = "Battery Optimization",
                    description = "Android may put Gupta to sleep and stop it from listening for your wake word.",
                    buttonText = "Fix",
                    buttonColor = amber,
                    onClick = onFixBattery
                )
            }

            if (missingA11y) {
                Spacer(modifier = Modifier.height(12.dp))
                PermissionRow(
                    title = "Accessibility Service",
                    description = "Required to open apps and execute commands from the background.",
                    buttonText = "Enable",
                    buttonColor = amber,
                    onClick = onFixA11y
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF5D4037)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF5D4037).copy(alpha = 0.75f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonColor),
            border = BorderStroke(1.dp, buttonColor.copy(alpha = 0.6f))
        ) {
            Text(buttonText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private data class CommandPart(
    val label: String,
    val example: String,
    val type: Int // 0=trigger, 1=contact, 2=content, 3=extra
)

private data class CommandExample(val parts: List<CommandPart>)

@Composable
private fun ExamplesCard() {
    val examples = listOf(
        CommandExample(listOf(
            CommandPart("trigger", "Message", 0),
            CommandPart("contact", "e.g. Mom", 1),
            CommandPart("text", "e.g. Hi", 2)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Call", 0),
            CommandPart("contact", "e.g. Dad", 1)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Pay", 0),
            CommandPart("contact", "e.g. Uncle", 1),
            CommandPart("amount", "e.g. ₹100", 3)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Set Timer", 0),
            CommandPart("duration", "e.g. 5 minutes", 2)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Search", 0),
            CommandPart("query", "e.g. the weather", 2)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Launch", 0),
            CommandPart("app", "e.g. Camera", 2)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Play", 0),
            CommandPart("song", "e.g. Blinding Lights", 2)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Navigate To", 0),
            CommandPart("place", "e.g. Bengaluru", 2)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Next Track", 0)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Replay", 0)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Previous Track", 0)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Stop", 0)
        )),
        CommandExample(listOf(
            CommandPart("trigger", "Start", 0)
        ))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Wake word shown once at the top
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "Hi Gupta",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Cursive,
                                letterSpacing = 0.sp
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "— say this first, then your command",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))

                // Command examples without repeating wake word
                examples.forEachIndexed { index, example ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(vertical = 7.dp)
                    ) {
                        example.parts.forEachIndexed { i, part ->
                            if (i > 0) Spacer(modifier = Modifier.width(6.dp))
                            CommandPartChip(part)
                        }
                    }
                    if (index < examples.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandPartChip(part: CommandPart) {
    val (bgColor, textColor) = when (part.type) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = bgColor
        ) {
            Text(
                text = part.example,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = if (part.type == 0) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        Text(
            text = part.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}
