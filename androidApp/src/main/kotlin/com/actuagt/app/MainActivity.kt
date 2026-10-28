package com.actuagt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.actuagt.shared.media.AndroidCamera
import com.actuagt.shared.media.AndroidVoice
import com.actuagt.shared.ui.navigation.App

// Android entry point. Firebase initializes automatically from
// google-services.json via the google-services Gradle plugin.
class MainActivity : ComponentActivity() {
    private var deepLinkReportId by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidCamera.register(this)
        AndroidVoice.register(this)
        deepLinkReportId = intent?.getStringExtra(EXTRA_REPORT_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            App(initialReportId = deepLinkReportId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkReportId = intent.getStringExtra(EXTRA_REPORT_ID)
    }

    companion object {
        const val EXTRA_REPORT_ID = "reportId"
    }
}
