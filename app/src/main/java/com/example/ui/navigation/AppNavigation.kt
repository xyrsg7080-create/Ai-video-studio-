package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.localization.stringsFor
import com.example.ui.CloudSyncViewModel
import com.example.ui.StudioContainer
import com.example.ui.StudioViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutFromFirebaseAndCredentials
import com.example.ui.autocreate.AutoVideoCreatorScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.editor.ManualEditorScreen
import com.example.ui.projects.MyProjectsScreen
import com.example.ui.projects.TemplatesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.UsageDashboardScreen
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.tools.BatchCreatorScreen
import com.example.ui.tools.BrandKitScreen
import com.example.ui.tools.MediaLibraryScreen
import com.example.ui.tools.ThumbnailMakerScreen
import com.example.ui.tools.VoiceStudioScreen
import com.example.ui.youtube.UploadSchedulerScreen
import com.example.ui.youtube.YouTubeStudioScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object RouteDashboard
@Serializable data class RouteAutoCreate(val blankMode: Boolean = false)
@Serializable data object RouteManualEditor
@Serializable data object RouteMyProjects
@Serializable data object RouteTemplates
@Serializable data object RouteVoiceStudio
@Serializable data object RouteThumbnailMaker
@Serializable data object RouteMediaLibrary
@Serializable data object RouteYouTubeStudio
@Serializable data object RouteUploadScheduler
@Serializable data object RouteBatchCreator
@Serializable data object RouteBrandKit
@Serializable data object RouteUsageDashboard
@Serializable data object RouteSettings

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun StudioRootApp(
    container: StudioContainer,
    studioViewModel: StudioViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences by studioViewModel.preferences.collectAsStateWithLifecycle()
    val strings = stringsFor(preferences.language)

    var currentUser by remember { mutableStateOf<FirebaseUser?>(FirebaseAuth.getInstance().currentUser) }
    var continueOffline by remember { mutableStateOf(false) }
    var checkedAutoSignIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!checkedAutoSignIn) {
            currentUser = attemptAutoSignIn(context)
            checkedAutoSignIn = true
        }
    }

    CompositionLocalProvider(
        LocalAppLanguage provides preferences.language,
        LocalAppStrings provides strings
    ) {
        if (currentUser == null && !continueOffline) {
            AuthScreen(
                onSignedIn = { user -> currentUser = user },
                onContinueOffline = { continueOffline = true },
                onToggleLanguage = { studioViewModel.setLanguage(it) }
            )
        } else {
            val signedInUser = currentUser
            if (signedInUser != null) {
                key(signedInUser.uid) {
                    val cloudVm: CloudSyncViewModel = viewModel(
                        factory = CloudSyncViewModel.provideFactory(signedInUser.uid)
                    )
                    LaunchedEffect(signedInUser.uid) {
                        cloudVm.syncProfile(signedInUser.displayName ?: "Creator", preferences.language.code)
                    }
                    StudioMainScaffold(
                        container = container,
                        studioViewModel = studioViewModel,
                        cloudSyncViewModel = cloudVm,
                        signedInEmail = signedInUser.email ?: signedInUser.displayName,
                        onSignOut = {
                            scope.launch {
                                signOutFromFirebaseAndCredentials(context)
                                currentUser = null
                                continueOffline = false
                            }
                        },
                        onRequestSignIn = { continueOffline = false }
                    )
                }
            } else {
                StudioMainScaffold(
                    container = container,
                    studioViewModel = studioViewModel,
                    cloudSyncViewModel = null,
                    signedInEmail = null,
                    onSignOut = {},
                    onRequestSignIn = { continueOffline = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
private fun StudioMainScaffold(
    container: StudioContainer,
    studioViewModel: StudioViewModel,
    cloudSyncViewModel: CloudSyncViewModel?,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    onRequestSignIn: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = LocalAppStrings.current

    val preferences by studioViewModel.preferences.collectAsStateWithLifecycle()
    val projects by studioViewModel.projects.collectAsStateWithLifecycle()
    val mediaAssets by studioViewModel.mediaAssets.collectAsStateWithLifecycle()
    val brandKits by studioViewModel.brandKits.collectAsStateWithLifecycle()
    val scheduledUploads by studioViewModel.scheduledUploads.collectAsStateWithLifecycle()
    val backgroundJobs by studioViewModel.backgroundJobs.collectAsStateWithLifecycle()
    val availableVoices by studioViewModel.availableVoices.collectAsStateWithLifecycle()
    val activeProject by studioViewModel.activeProject.collectAsStateWithLifecycle()
    val activeScenes by studioViewModel.activeScenes.collectAsStateWithLifecycle()
    val activeClips by studioViewModel.activeTimelineClips.collectAsStateWithLifecycle()
    val activeSubtitles by studioViewModel.activeSubtitles.collectAsStateWithLifecycle()
    val aiPipelineState by studioViewModel.aiPipelineState.collectAsStateWithLifecycle()
    val stockResults by studioViewModel.stockResults.collectAsStateWithLifecycle()
    val stockSearchError by studioViewModel.stockSearchError.collectAsStateWithLifecycle()
    val isSearchingStock by studioViewModel.isSearchingStock.collectAsStateWithLifecycle()
    val exportProgress by studioViewModel.exportProgress.collectAsStateWithLifecycle()
    val exportMessage by studioViewModel.exportMessage.collectAsStateWithLifecycle()
    val statusBanner by studioViewModel.statusBanner.collectAsStateWithLifecycle()
    val storageStats by studioViewModel.storageStats.collectAsStateWithLifecycle()
    val canUndo by studioViewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by studioViewModel.canRedo.collectAsStateWithLifecycle()
    val videoAnalysisResult by studioViewModel.videoAnalysisResult.collectAsStateWithLifecycle()
    val isAnalyzingVideo by studioViewModel.isAnalyzingVideo.collectAsStateWithLifecycle()

    val cloudProjectsState = cloudSyncViewModel?.cloudProjects?.collectAsStateWithLifecycle()?.value
    val cloudSyncMessage = cloudSyncViewModel?.syncMessage?.collectAsStateWithLifecycle()?.value

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            studioViewModel.clearBanner()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val isOnDashboard = currentDest?.hasRoute<RouteDashboard>() ?: true

    val topDestinations = listOf(
        Triple(RouteDashboard, strings.navHome, Icons.Filled.Home),
        Triple(RouteAutoCreate(false), strings.navCreate, Icons.Filled.AutoAwesome),
        Triple(RouteManualEditor, strings.navEditor, Icons.Filled.MovieCreation),
        Triple(RouteMyProjects, strings.navProjects, Icons.Filled.FolderOpen),
        Triple(RouteYouTubeStudio, strings.navYouTube, Icons.Filled.CloudUpload)
    )

    fun openModule(moduleId: String) {
        when (moduleId) {
            "create_new" -> navController.navigate(RouteAutoCreate(blankMode = true))
            "ai_auto" -> navController.navigate(RouteAutoCreate(blankMode = false))
            "manual_editor" -> navController.navigate(RouteManualEditor)
            "my_projects" -> navController.navigate(RouteMyProjects)
            "templates" -> navController.navigate(RouteTemplates)
            "voice_studio" -> navController.navigate(RouteVoiceStudio)
            "thumbnail_maker" -> navController.navigate(RouteThumbnailMaker)
            "media_library" -> navController.navigate(RouteMediaLibrary)
            "youtube_studio" -> navController.navigate(RouteYouTubeStudio)
            "upload_scheduler" -> navController.navigate(RouteUploadScheduler)
            "batch_creator" -> navController.navigate(RouteBatchCreator)
            "brand_kit" -> navController.navigate(RouteBrandKit)
            "usage_dashboard" -> {
                studioViewModel.refreshStorageStats()
                navController.navigate(RouteUsageDashboard)
            }
            "settings" -> navController.navigate(RouteSettings)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = StudioObsidian,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!isOnDashboard) {
                    TopAppBar(
                        title = { Text(strings.appName) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = StudioSurface),
                        navigationIcon = {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.testTag("top_bar_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = StudioSurface) {
                        topDestinations.forEach { (route, label, icon) ->
                            val selected = when (route) {
                                is RouteDashboard -> currentDest?.hasRoute<RouteDashboard>() == true
                                is RouteAutoCreate -> currentDest?.hasRoute<RouteAutoCreate>() == true
                                is RouteManualEditor -> currentDest?.hasRoute<RouteManualEditor>() == true
                                is RouteMyProjects -> currentDest?.hasRoute<RouteMyProjects>() == true
                                else -> currentDest?.hasRoute<RouteYouTubeStudio>() == true
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo<RouteDashboard> { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.testTag("bottom_nav_$label")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail) {
                    NavigationRail(containerColor = StudioSurface) {
                        topDestinations.forEach { (route, label, icon) ->
                            val selected = when (route) {
                                is RouteDashboard -> currentDest?.hasRoute<RouteDashboard>() == true
                                is RouteAutoCreate -> currentDest?.hasRoute<RouteAutoCreate>() == true
                                is RouteManualEditor -> currentDest?.hasRoute<RouteManualEditor>() == true
                                is RouteMyProjects -> currentDest?.hasRoute<RouteMyProjects>() == true
                                else -> currentDest?.hasRoute<RouteYouTubeStudio>() == true
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo<RouteDashboard> { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(navController = navController, startDestination = RouteDashboard) {
                        composable<RouteDashboard> {
                            DashboardScreen(
                                activeProject = activeProject,
                                projectCount = projects.size,
                                preferences = preferences,
                                isCloudSignedIn = cloudSyncViewModel != null,
                                onModuleClick = ::openModule,
                                onToggleLanguage = { studioViewModel.setLanguage(it) },
                                onQuickCreateBlank = {
                                    studioViewModel.createBlankProject("Quick Studio Video") {
                                        navController.navigate(RouteManualEditor)
                                    }
                                },
                                onDismissOnboarding = {
                                    scope.launch { studioViewModel.prefsRepo.setOnboardingCompleted(true) }
                                }
                            )
                        }

                        composable<RouteAutoCreate> { entry ->
                            val blankMode = entry.arguments?.getBoolean("blankMode") ?: false
                            AutoVideoCreatorScreen(
                                initialCreateBlankMode = blankMode,
                                pipelineState = aiPipelineState,
                                activeProject = activeProject,
                                activeScenes = activeScenes,
                                onGenerateAutoVideo = { topic, cat, ar, dur, lang, aiImg, stock, vo, subs, beat ->
                                    studioViewModel.generateFullAutoVideo(
                                        topic, cat, ar, dur, lang, aiImg, stock, vo, subs, beat
                                    ) {}
                                },
                                onCreateBlankProject = { title, topic, cat, ar, dur, cw, ch ->
                                    studioViewModel.createBlankProject(title, topic, cat, ar, dur, cw, ch) {
                                        navController.navigate(RouteManualEditor)
                                    }
                                },
                                onFactCheck = { studioViewModel.runFactCheckOnActiveProject(it) },
                                onOpenManualEditor = { navController.navigate(RouteManualEditor) },
                                onResetState = { studioViewModel.resetAiPipelineState() }
                            )
                        }

                        composable<RouteManualEditor> {
                            ManualEditorScreen(
                                project = activeProject,
                                scenes = activeScenes,
                                clips = activeClips,
                                subtitles = activeSubtitles,
                                brandKit = brandKits.firstOrNull(),
                                canUndo = canUndo,
                                canRedo = canRedo,
                                exportProgress = exportProgress,
                                exportMessage = exportMessage,
                                is4kSupported = remember { container.videoRenderEngine.is4kSupported() },
                                stockResults = stockResults,
                                onUndo = { studioViewModel.undo() },
                                onRedo = { studioViewModel.redo() },
                                onUpdateProject = { studioViewModel.updateProjectMetadata(it) },
                                onUpdateScene = { studioViewModel.updateScene(it) },
                                onSplitScene = { studioViewModel.splitSceneAtMidpoint(it) },
                                onMoveScene = { s, d -> studioViewModel.moveScene(s, d) },
                                onDuplicateScene = { studioViewModel.duplicateScene(it) },
                                onDeleteScene = { studioViewModel.deleteScene(it) },
                                onAddScene = { studioViewModel.addNewScene(it) },
                                onAddOverlayClip = { pid, tt, lbl, txt, path, st, dur ->
                                    studioViewModel.addOverlayClip(pid, tt, lbl, txt, path, st, dur)
                                },
                                onCopyClip = { studioViewModel.copyClip(it) },
                                onPasteClip = { pid, ms -> studioViewModel.pasteClip(pid, ms) },
                                onDeleteClip = { studioViewModel.deleteClip(it) },
                                onUpdateSubtitle = { studioViewModel.updateSubtitle(it) },
                                onAddSubtitle = { pid, txt, st, en -> studioViewModel.addSubtitle(pid, txt, st, en) },
                                onDeleteSubtitle = { studioViewModel.deleteSubtitle(it) },
                                onImportSrt = { studioViewModel.importSrtIntoActiveProject(it) },
                                onExportSrtText = { studioViewModel.exportActiveSubtitlesToSrt() },
                                onTranslateProject = { studioViewModel.translateProjectToLanguage(it) },
                                onSearchStock = { studioViewModel.searchStockMedia(it) },
                                onAttachStockToScene = { item, scene -> studioViewModel.attachStockMediaToScene(item, scene) },
                                onGenerateAiImage = { sc, ar -> studioViewModel.generateAiImageForScene(sc, ar) },
                                onGenerateAiVideo = { sc, ar -> studioViewModel.generateAiVideoForScene(sc, ar) },
                                onImportLocalMediaToScene = { uri, scene ->
                                    studioViewModel.importLocalUriAsAsset(uri, "IMAGE", scene.title, scene)
                                },
                                onExportNow = { studioViewModel.exportActiveProjectNow(it) },
                                onEnqueueBackgroundExport = { studioViewModel.enqueueBackgroundExport(it) },
                                onCancelExport = { studioViewModel.cancelActiveExport() },
                                onCreateBlankProject = {
                                    studioViewModel.createBlankProject("New Timeline Project")
                                }
                            )
                        }

                        composable<RouteMyProjects> {
                            MyProjectsScreen(
                                projects = projects,
                                activeProject = activeProject,
                                activeScenes = activeScenes,
                                cloudProjectsState = cloudProjectsState,
                                cloudSyncMessage = cloudSyncMessage,
                                onSelectAndOpenEditor = { proj ->
                                    studioViewModel.selectProject(proj.id)
                                    navController.navigate(RouteManualEditor)
                                },
                                onRenameProject = { p, title -> studioViewModel.renameProject(p, title) },
                                onDuplicateProject = { studioViewModel.duplicateProject(it) },
                                onDeleteProject = { studioViewModel.deleteProject(it) },
                                onExportBundleJson = { studioViewModel.exportProjectBundleJson(it) },
                                onImportBundleJson = { studioViewModel.importProjectBundleJson(it) },
                                onBackupProjectToCloud = cloudSyncViewModel?.let { vm ->
                                    { p, s ->
                                        vm.backupProject(p, s) {
                                            studioViewModel.updateProjectMetadata(p.copy(isCloudBackedUp = true))
                                        }
                                    }
                                },
                                onRestoreCloudProject = cloudSyncViewModel?.let { vm ->
                                    { backup ->
                                        val (p, scenes) = vm.buildRestoredProjectFromCloud(backup)
                                        scope.launch {
                                            container.database.projectDao().upsertProject(p)
                                            container.database.projectDao().upsertScenes(scenes)
                                            studioViewModel.selectProject(p.id)
                                            studioViewModel.showBanner("Restored '${p.title}' locally.")
                                        }
                                    }
                                },
                                onDeleteCloudBackup = cloudSyncViewModel?.let { vm -> { vm.deleteCloudBackup(it) } },
                                onNavigateCreateNew = { navController.navigate(RouteAutoCreate(false)) }
                            )
                        }

                        composable<RouteTemplates> {
                            TemplatesScreen(
                                templates = studioViewModel.templates,
                                onUseTemplate = { tpl ->
                                    studioViewModel.createFromTemplate(tpl) {
                                        navController.navigate(RouteManualEditor)
                                    }
                                }
                            )
                        }

                        composable<RouteVoiceStudio> {
                            VoiceStudioScreen(
                                voices = availableVoices,
                                activeProject = activeProject,
                                activeScenes = activeScenes,
                                voiceConsentGranted = preferences.voiceConsentGranted,
                                onSetVoiceConsent = { granted ->
                                    scope.launch { studioViewModel.prefsRepo.setVoiceConsentGranted(granted) }
                                },
                                onPreviewVoice = { txt, lang, vid, sp, pi ->
                                    studioViewModel.previewVoice(txt, lang, vid, sp, pi)
                                },
                                onSynthesizeToScene = { sc, lang, vid, sp, pi ->
                                    studioViewModel.synthesizeVoiceToScene(sc, lang, vid, sp, pi)
                                },
                                onImportAudioUri = { uri, title ->
                                    studioViewModel.importLocalUriAsAsset(uri, "AUDIO", title)
                                }
                            )
                        }

                        composable<RouteThumbnailMaker> {
                            ThumbnailMakerScreen(
                                activeProject = activeProject,
                                activeScenes = activeScenes,
                                brandKit = brandKits.firstOrNull(),
                                onRenderPreview = { cfg -> studioViewModel.generateThumbnailPreview(cfg) },
                                onSaveThumbnail = { proj, bmp -> studioViewModel.saveThumbnailForProject(proj, bmp) }
                            )
                        }

                        composable<RouteMediaLibrary> {
                            MediaLibraryScreen(
                                assets = mediaAssets,
                                stockResults = stockResults,
                                isSearchingStock = isSearchingStock,
                                stockSearchError = stockSearchError,
                                videoAnalysisResult = videoAnalysisResult,
                                isAnalyzingVideo = isAnalyzingVideo,
                                onImportLocalUri = { uri, type, title -> studioViewModel.importLocalUriAsAsset(uri, type, title) },
                                onSearchStock = { studioViewModel.searchStockMedia(it) },
                                onRenameAsset = { id, t -> studioViewModel.renameAsset(id, t) },
                                onDeleteAsset = { studioViewModel.deleteAsset(it) },
                                onAnalyzeVideo = { uri, prompt -> studioViewModel.analyzeSelectedVideo(uri, prompt) }
                            )
                        }

                        composable<RouteYouTubeStudio> {
                            YouTubeStudioScreen(
                                preferences = preferences,
                                hasOAuthClientId = container.youTubeRepository.hasConfiguredClientId(),
                                isConnected = container.youTubeRepository.isConnected(),
                                activeProject = activeProject,
                                onConnectYouTube = { studioViewModel.launchYouTubeOAuth() },
                                onDisconnectYouTube = { studioViewModel.disconnectYouTubeChannel() },
                                onOpenSettingsForKey = { navController.navigate(RouteSettings) },
                                onGenerateSeo = { studioViewModel.refreshSeoForActiveProject() },
                                onScheduleOrUpload = { p, t, d, tags, vis, ts, auth ->
                                    studioViewModel.scheduleOrUploadNow(p, t, d, tags, vis, ts, auth)
                                    navController.navigate(RouteUploadScheduler)
                                }
                            )
                        }

                        composable<RouteUploadScheduler> {
                            UploadSchedulerScreen(
                                uploads = scheduledUploads,
                                onPause = { studioViewModel.pauseUpload(it) },
                                onResumeOrRetry = { studioViewModel.resumeOrRetryUpload(it) },
                                onReschedulePlusOneHour = {
                                    studioViewModel.rescheduleUpload(it, System.currentTimeMillis() + 3_600_000L)
                                },
                                onCancel = { studioViewModel.cancelUpload(it) }
                            )
                        }

                        composable<RouteBatchCreator> {
                            BatchCreatorScreen(
                                projects = projects,
                                backgroundJobs = backgroundJobs,
                                onRunBatchTopics = { topics, lang, render ->
                                    studioViewModel.runBatchShortsGeneration(topics, lang, render)
                                },
                                onConvertLongToShorts = { src, count ->
                                    studioViewModel.convertLongProjectToShorts(src, count)
                                }
                            )
                        }

                        composable<RouteBrandKit> {
                            BrandKitScreen(
                                brandKit = brandKits.firstOrNull(),
                                onSaveBrandKit = { studioViewModel.saveBrandKit(it) },
                                onSyncBrandKitCloud = cloudSyncViewModel?.let { vm -> { vm.backupBrandKit(it) } }
                            )
                        }

                        composable<RouteUsageDashboard> {
                            UsageDashboardScreen(
                                stats = storageStats,
                                preferences = preferences,
                                projectCount = projects.size,
                                assetCount = mediaAssets.size,
                                onCleanCache = { studioViewModel.cleanTemporaryCache() }
                            )
                        }

                        composable<RouteSettings> {
                            SettingsScreen(
                                preferences = preferences,
                                keyStore = studioViewModel.keyStore,
                                signedInUserEmail = signedInEmail,
                                onSetLanguage = { studioViewModel.setLanguage(it) },
                                onSetBatterySaver = { enabled ->
                                    scope.launch { studioViewModel.prefsRepo.setBatterySaverRender(enabled) }
                                },
                                onSetProxyPreview = { enabled ->
                                    scope.launch { studioViewModel.prefsRepo.setProxyPreviewEnabled(enabled) }
                                },
                                onSaveKeysFeedback = { studioViewModel.showBanner(it) },
                                onSignOutCloud = onSignOut,
                                onNavigateToSignIn = onRequestSignIn,
                                onDeleteAllLocalData = { studioViewModel.deleteAllUserLocalData() }
                            )
                        }
                    }
                }
            }
        }
    }
}
