package com.pureenhance.ai.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pureenhance.ai.presentation.components.GlassCard

/** These claims are true for this code base: the manifest has no INTERNET permission and no network SDK is bundled. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("‹  Back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(48.dp).clickable(onClick = onBack))
        Spacer(Modifier.height(8.dp))
        Text("Privacy", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "Your photos are processed on your device and are not uploaded by the app.",
                Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(20.dp))
        listOf(
            "On-device AI" to "Enhancement, face restoration and upscaling all run locally. It works in airplane mode.",
            "No internet access" to "This app doesn't request the internet permission, so it technically cannot send your photos anywhere.",
            "No account, no ads, no tracking" to "No login, no analytics, no advertising or purchase SDKs.",
            "Your originals are safe" to "The original photo is never changed. Results are saved as a new file in Pictures/PureEnhance.",
            "No temporary copies" to "Photos are processed in memory only. Nothing is left behind when processing ends or is cancelled.",
            "Location is not copied" to "Saved results keep date and camera details, but GPS location is intentionally left out.",
        ).forEach { (title, body) ->
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
        }
        Text(
            "AI enhancement reconstructs plausible detail; it can't recover information that isn't in the photo.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
