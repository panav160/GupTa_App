package com.voicecommand.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectScreen(
    isConnectedToServer: Boolean,
    serverUrl: String
) {
    val green = Color(0xFF2E7D32)
    val greenBg = Color(0xFF2E7D32).copy(alpha = 0.08f)
    val amber = Color(0xFFF57C00)
    val amberBg = Color(0xFFFFF3E0)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Text(
                text = "Connect to Laptop",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "for better transcription",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Status card ───────────────────────────────────────────────────
            if (isConnectedToServer) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = greenBg),
                    border = BorderStroke(1.dp, green.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = null,
                            tint = green,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Connected to Laptop",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = green
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = green,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = green.copy(alpha = 0.75f)
                            )
                            Text(
                                text = "Transcription is running on your laptop GPU for best accuracy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = amberBg),
                    border = BorderStroke(1.dp, amber.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = amber,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Running on Phone",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = amber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Gupta is transcribing on your phone. Accuracy may be lower for complex or noisy commands.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Prerequisites ─────────────────────────────────────────────────
            SectionLabel(text = "PREREQUISITES")
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PrereqRow(
                        title = "Python on your laptop",
                        description = "Download from python.org — any version 3.9 or above."
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    PrereqRow(
                        title = "A GPU helps (not required)",
                        description = "Any modern dedicated or integrated GPU speeds up transcription significantly."
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Steps ─────────────────────────────────────────────────────────
            SectionLabel(text = "HOW TO CONNECT THROUGH LOCAL WIFI")
            Spacer(modifier = Modifier.height(10.dp))

            StepCard(
                number = "1",
                icon = Icons.Default.Download,
                title = "Download the Gupta laptop app",
                description = "Coming soon — link will be available here."
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepCard(
                number = "2",
                icon = Icons.Default.Settings,
                title = "Click Setup (first time only)",
                description = "Opens a terminal and installs all required packages automatically. Takes a few minutes."
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepCard(
                number = "3",
                icon = Icons.Default.Laptop,
                title = "Click Start Server",
                description = "Loads the AI model and starts the transcription server. A QR code will appear once it's ready."
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepCard(
                number = "4",
                icon = Icons.Default.QrCodeScanner,
                title = "Scan the QR code",
                description = "Open Settings in this app (⚙ top right on the Voice tab) and tap the QR scan icon next to the server field."
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(24.dp))

            // ── Tailscale section ─────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONNECT FROM ANYWHERE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "By default Gupta only works when your phone and laptop are on the same WiFi. " +
                       "To use it from anywhere — different network, mobile data, away from home — " +
                       "install Tailscale on both devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            StepCard(
                number = "1",
                icon = Icons.Default.Laptop,
                title = "Install Tailscale on your laptop",
                description = "Download from tailscale.com — free. Sign in with Google, GitHub or Microsoft."
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepCard(
                number = "2",
                icon = Icons.Default.PhoneAndroid,
                title = "Install Tailscale on your phone",
                description = "Search \"Tailscale\" on the Play Store. Sign in with the exact same account you used on the laptop."
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepCard(
                number = "3",
                icon = Icons.Default.Wifi,
                title = "Find your laptop's Tailscale IP",
                description = "Open the Tailscale app on your laptop — your device will show an IP starting with 100.x.x.x. Copy that number."
            )
            Spacer(modifier = Modifier.height(8.dp))
            StepCard(
                number = "4",
                icon = Icons.Default.EditNote,
                title = "Enter it manually in Settings",
                description = "Open ⚙ Settings in this app and type your Tailscale IP with the port — e.g. 100.x.x.x:8765. The QR scan won't work for Tailscale as the QR shows your local WiFi IP."
            )

            Spacer(modifier = Modifier.height(28.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Footer note ───────────────────────────────────────────────────
            Text(
                text = "About accuracy & speed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Connecting to your laptop runs a much larger AI model, which improves transcription accuracy — especially for names, mixed languages, and noisy environments. " +
                       "Speed depends on your hardware. A dedicated GPU will feel just as fast as the phone. An older laptop CPU may be a second or two slower.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PrereqRow(title: String, description: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StepCard(
    number: String,
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Number badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = number,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
