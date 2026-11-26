package com.example.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "AuthScreen"

suspend fun attemptAutoSignIn(context: Context): FirebaseUser? {
    val existing = FirebaseAuth.getInstance().currentUser
    if (existing != null) return existing
    return try {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(context, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
            val authResult = FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            authResult.user
        } else {
            null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(TAG, "Silent auto sign-in skipped: ${e.message}")
        null
    }
}

suspend fun signOutFromFirebaseAndCredentials(context: Context) {
    withContext(NonCancellable) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        FirebaseAuth.getInstance().signOut()
    }
}

@Composable
fun AuthScreen(
    onSignedIn: (FirebaseUser) -> Unit,
    onContinueOffline: () -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val scope = rememberCoroutineScope()

    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(StudioObsidian, StudioSurface, StudioObsidian)
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.appName,
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
                OutlinedButton(
                    onClick = {
                        onToggleLanguage(if (lang == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH)
                    },
                    modifier = Modifier.testTag("auth_language_toggle")
                ) {
                    Text(if (lang == AppLanguage.ENGLISH) "हिन्दी" else "English")
                }
            }

            Image(
                painter = painterResource(id = R.drawable.img_studio_hero_1795671821141),
                contentDescription = "AI Video Studio workspace banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Text(
                text = if (lang == AppLanguage.HINDI) {
                    "शॉर्ट्स और लॉन्ग-फॉर्म YouTube वीडियो मिनटों में बनाएं"
                } else {
                    "Create Shorts & Long-Form YouTube Videos End-to-End"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (lang == AppLanguage.HINDI) {
                    "ऑफलाइन टाइमलाइन एडिटिंग, TTS वॉइस, MP4 एक्सपोर्ट तुरंत मुफ्त काम करते हैं। क्लाउड बैकअप के लिए Google से साइन इन करें।"
                } else {
                    "Offline timeline editing, on-device TTS voiceovers, and Media3 MP4 exports work immediately. Sign in with Google for optional Firestore cloud project backup."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Filled.AutoAwesome,
                        title = "AI Script, Storyboard & SEO",
                        desc = "Firebase AI Logic + Fact-Check flags"
                    )
                    FeatureRow(
                        icon = Icons.Filled.OfflineBolt,
                        title = "100% Free Local Multi-Track Editor",
                        desc = "Media3 MP4 export, TTS voice, SRT, beat sync"
                    )
                    FeatureRow(
                        icon = Icons.Filled.CloudDone,
                        title = "Firestore Cloud Sync & Brand Kit",
                        desc = "Backed by your Google account"
                    )
                    FeatureRow(
                        icon = Icons.Filled.Security,
                        title = "Android Keystore Encrypted Keys",
                        desc = "Optional Pexels, Pixabay, & YouTube OAuth PKCE"
                    )
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (isSigningIn) return@Button
                    errorMessage = null
                    isSigningIn = true
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val option = GetSignInWithGoogleOption.Builder(
                                context.getString(R.string.default_web_client_id)
                            ).build()
                            val req = GetCredentialRequest.Builder()
                                .addCredentialOption(option)
                                .build()
                            val result = credentialManager.getCredential(context, req)
                            val cred = result.credential
                            if (cred is CustomCredential &&
                                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                                val authRes = FirebaseAuth.getInstance()
                                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                                    .await()
                                val user = authRes.user
                                if (user != null) {
                                    onSignedIn(user)
                                } else {
                                    errorMessage = "Google sign-in returned no user."
                                }
                            } else {
                                errorMessage = "Unsupported credential type returned."
                            }
                        } catch (_: GetCredentialCancellationException) {
                            errorMessage = "Sign-in cancelled. You can continue offline anytime."
                        } catch (e: GetCredentialException) {
                            errorMessage = e.localizedMessage ?: "Google Sign-In failed on this device. You can continue offline."
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Authentication error."
                        } finally {
                            isSigningIn = false
                        }
                    }
                },
                enabled = !isSigningIn,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = StudioObsidian
                    )
                } else {
                    Text(
                        text = strings.signInWithGoogle,
                        color = StudioObsidian,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onContinueOffline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("continue_offline_button")
            ) {
                Text(strings.continueOffline)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
