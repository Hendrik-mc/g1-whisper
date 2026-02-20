package com.evenai.companion.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.evenai.companion.domain.model.GlassesState
import com.evenai.companion.ui.viewmodel.MainViewModel

/**
 * Consumer-friendly onboarding: Welcome → Permissions → Pairing → Tutorial → Dashboard.
 * No technical jargon exposed to the user.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    var step by remember { mutableIntStateOf(0) }
    val glassesState by viewModel.glassesState.collectAsState()

    // Auto-advance from Pairing step when glasses are ready
    LaunchedEffect(glassesState, step) {
        if (step == 2 && glassesState is GlassesState.Ready) {
            kotlinx.coroutines.delay(800)
            step = 3
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding_step"
    ) { currentStep ->
        when (currentStep) {
            0 -> WelcomeStep(onNext = { step++ })
            1 -> PermissionsStep(onNext = { step++ })
            2 -> PairingStep(
                glassesState = glassesState,
                onStartScan  = { viewModel.startScan() },
                onNext       = { step++ }
            )
            3 -> TutorialStep(onNext = {
                viewModel.completeOnboarding()
                onComplete()
            })
        }
    }
}

// ── Step 1: Welcome ───────────────────────────────────────────────────────────
@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    OnboardingScaffold {
        Spacer(Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SmartToy, null,
                Modifier.size(56.dp), MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(32.dp))
        Text("Welcome to G1 Companion",
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(16.dp))
        Text(
            "Your Even Realities G1 glasses are about to get five powerful AI superpowers.\n\nLet's get you set up in 60 seconds.",
            fontSize  = 16.sp,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            lineHeight = 24.sp
        )

        Spacer(Modifier.weight(1f))
        OnboardingButton("Let's go", onNext)
        Spacer(Modifier.height(32.dp))
    }
}

// ── Step 2: Permissions ───────────────────────────────────────────────────────
@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
    }

    var allGranted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allGranted = results.values.all { it }
        if (allGranted) onNext()
    }

    OnboardingScaffold {
        Spacer(Modifier.height(48.dp))

        Text("A few quick permissions",
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(24.dp))

        PermissionRow(Icons.Filled.Bluetooth, "Connect to your glasses",
            "So the app can find and talk to your G1.")
        Spacer(Modifier.height(12.dp))
        PermissionRow(Icons.Filled.Mic, "Microphone",
            "Only used when you tap to speak — mic is off by default.")

        Spacer(Modifier.weight(1f))
        OnboardingButton("Allow and continue") { launcher.launch(permissionsToRequest) }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(32.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

// ── Step 3: Pairing ───────────────────────────────────────────────────────────
@Composable
private fun PairingStep(
    glassesState: GlassesState,
    onStartScan: () -> Unit,
    onNext: () -> Unit
) {
    var scanStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!scanStarted) {
            onStartScan()
            scanStarted = true
        }
    }

    OnboardingScaffold {
        Spacer(Modifier.height(48.dp))

        Icon(Icons.Filled.Search, null,
            Modifier.size(64.dp), MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(24.dp))
        Text("Finding your G1 glasses",
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(16.dp))

        val statusText = when (glassesState) {
            GlassesState.Scanning   -> "Searching nearby… make sure your glasses are on."
            GlassesState.Connecting -> "Found them! Connecting now…"
            GlassesState.Connected  -> "Almost there…"
            GlassesState.Ready      -> "Connected!"
            is GlassesState.Error   -> "Couldn't find glasses. Make sure they're charged and nearby."
            else -> "Tap below if your glasses aren't found automatically."
        }

        Text(statusText, textAlign = TextAlign.Center, fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

        Spacer(Modifier.height(24.dp))

        when (glassesState) {
            GlassesState.Scanning, GlassesState.Connecting, GlassesState.Connected ->
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            GlassesState.Ready ->
                Icon(Icons.Filled.Check, null,
                    Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
            else -> {}
        }

        Spacer(Modifier.weight(1f))

        if (glassesState is GlassesState.Error) {
            OnboardingButton("Try again") {
                onStartScan()
            }
        }
        if (glassesState is GlassesState.Ready) {
            OnboardingButton("Continue", onNext)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ── Step 4: Tutorial ──────────────────────────────────────────────────────────
@Composable
private fun TutorialStep(onNext: () -> Unit) {
    OnboardingScaffold {
        Spacer(Modifier.height(48.dp))
        Text("Here's how it works",
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            color      = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(24.dp))

        TutorialItem("1", "Pick a widget", "Choose from five AI tools on the dashboard.")
        Spacer(Modifier.height(12.dp))
        TutorialItem("2", "Tap the arm once", "Single tap activates your selected widget.")
        Spacer(Modifier.height(12.dp))
        TutorialItem("3", "Double-tap to dismiss", "Clear the lens anytime with a double-tap.")
        Spacer(Modifier.height(12.dp))
        TutorialItem("4", "Mic is off by default", "You control when your voice is heard.")

        Spacer(Modifier.weight(1f))
        OnboardingButton("Take me to the dashboard", onNext)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TutorialItem(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(number, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

// ── Shared layout ─────────────────────────────────────────────────────────────
@Composable
private fun OnboardingScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun OnboardingButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
