package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.example.core.security.configureAppCheck
import com.example.ui.StudioContainer
import com.example.ui.StudioViewModel
import com.example.ui.navigation.StudioRootApp
import com.example.ui.theme.AIVideoStudioTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var studioContainer: StudioContainer
    private lateinit var studioViewModel: StudioViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Mandatory first call before Compose / ViewModels / AI Logic
        configureAppCheck(this, intent)

        studioContainer = StudioContainer(applicationContext)
        studioViewModel = ViewModelProvider(
            this,
            StudioViewModel.provideFactory(applicationContext, studioContainer)
        )[StudioViewModel::class.java]

        handleIncomingOAuthIntent(intent)

        setContent {
            AIVideoStudioTheme {
                StudioRootApp(
                    container = studioContainer,
                    studioViewModel = studioViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingOAuthIntent(intent)
    }

    private fun handleIncomingOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "com.aistudio.aivideostudio.kqzxpm" && data.host == "oauth2redirect") {
            studioViewModel.handleYouTubeOAuthRedirect(data)
        }
    }
}
