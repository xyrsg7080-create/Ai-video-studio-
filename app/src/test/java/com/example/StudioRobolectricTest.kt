package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.localization.stringsFor
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectBackupSerializer
import com.example.data.local.ProjectBundle
import com.example.data.local.SceneEntity
import com.example.data.local.StudioPreferences
import com.example.data.local.SubtitleEntity
import com.example.data.local.TimelineClipEntity
import com.example.data.local.VideoProjectEntity
import com.example.media.AudioMixerAndBeatDetector
import com.example.media.SrtCodec
import com.example.media.VideoRenderEngine
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.theme.AIVideoStudioTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `room project scenes clips and subtitles persist and delete cleanly`() = runBlocking {
        val dao = db.projectDao()
        val project = VideoProjectEntity(
            id = "p_1",
            title = "Deep Ocean Facts",
            topic = "Deep ocean trenches",
            aspectRatio = "9:16",
            targetDurationSec = 45
        )
        val scene = SceneEntity(
            id = "s_1",
            projectId = "p_1",
            orderIndex = 0,
            title = "Hook",
            narrationText = "Did you know Mariana Trench is deeper than Everest?",
            visualSearchQuery = "deep ocean"
        )
        val clip = TimelineClipEntity(
            id = "c_1",
            projectId = "p_1",
            trackType = "VIDEO_MAIN",
            trackIndex = 0,
            label = "Hook"
        )
        val sub = SubtitleEntity(
            id = "sub_1",
            projectId = "p_1",
            orderIndex = 0,
            startTimeMs = 0L,
            endTimeMs = 4000L,
            text = "Did you know Mariana Trench is deeper than Everest?"
        )

        dao.upsertProject(project)
        dao.upsertScenes(listOf(scene))
        dao.upsertTimelineClips(listOf(clip))
        dao.upsertSubtitles(listOf(sub))

        assertEquals("Deep Ocean Facts", dao.getProjectById("p_1")?.title)
        assertEquals(1, dao.getScenes("p_1").size)
        assertEquals(1, dao.getTimelineClips("p_1").size)
        assertEquals(1, dao.getSubtitles("p_1").size)

        dao.deleteFullProject("p_1")
        assertEquals(null, dao.getProjectById("p_1"))
        assertTrue(dao.getScenes("p_1").isEmpty())
    }

    @Test
    fun `srt codec round trip preserves timing and text`() {
        val subs = listOf(
            SubtitleEntity("1", "proj", 0, 1200L, 3850L, "Hello Creators"),
            SubtitleEntity("2", "proj", 1, 4000L, 7250L, "नमस्ते क्रिएटर्स")
        )
        val srt = SrtCodec.exportToSrt(subs)
        val parsed = SrtCodec.parseSrt("proj", srt)
        assertEquals(2, parsed.size)
        assertEquals(1200L, parsed[0].startTimeMs)
        assertEquals(3850L, parsed[0].endTimeMs)
        assertEquals("नमस्ते क्रिएटर्स", parsed[1].text)
    }

    @Test
    fun `project bundle json export and import round trip`() {
        val bundle = ProjectBundle(
            project = VideoProjectEntity(id = "b1", title = "Backup Test", topic = "AI"),
            scenes = listOf(SceneEntity(id = "sc1", projectId = "b1", orderIndex = 0, title = "Intro", narrationText = "Hi", visualSearchQuery = "tech")),
            timelineClips = emptyList(),
            subtitles = emptyList()
        )
        val json = ProjectBackupSerializer.exportToJson(bundle)
        val restored = ProjectBackupSerializer.importFromJson(json).getOrThrow()
        assertEquals("Backup Test (Imported)", restored.project.title)
        assertEquals(1, restored.scenes.size)
        assertEquals(restored.project.id, restored.scenes.first().projectId)
    }

    @Test
    fun `audio mixer synthesizes royalty free wav and detects beat timestamps`() = runBlocking {
        val wav = AudioMixerAndBeatDetector.ensureMusicTrackWav(context, "shorts_energy", 6000L)
        assertTrue(wav.exists() && wav.length() > 1024L)
        val beats = AudioMixerAndBeatDetector.detectBeatTimestampsMs(wav)
        assertTrue("Expected detected beats on 128 BPM track", beats.isNotEmpty())

        val mixedOut = File(context.cacheDir, "test_mix.wav")
        val mixRes = AudioMixerAndBeatDetector.mixAudioTracksToWav(
            context = context,
            voiceoverFiles = emptyList(),
            musicFile = wav,
            musicVolume = 0.3f,
            voiceVolume = 1.0f,
            duckingEnabled = true,
            totalDurationMs = 3000L,
            outputFile = mixedOut
        )
        assertTrue(mixRes.isSuccess)
        assertTrue(mixedOut.exists() && mixedOut.length() > 44L)
    }

    @Test
    fun `render engine computes even dimensions for 9_16 16_9 1_1 and custom`() {
        val (w9, h9) = VideoRenderEngine.computeTargetDimensions("9:16", 1080, 1920, 720)
        assertEquals(720, w9)
        assertEquals(1280, h9)

        val (w16, h16) = VideoRenderEngine.computeTargetDimensions("16:9", 1920, 1080, 1080)
        assertEquals(1920, w16)
        assertEquals(1080, h16)

        val (w1, h1) = VideoRenderEngine.computeTargetDimensions("1:1", 1080, 1080, 720)
        assertEquals(720, w1)
        assertEquals(720, h1)

        val (wc, hc) = VideoRenderEngine.computeTargetDimensions("custom", 1081, 1351, 720)
        assertEquals(0, wc % 2)
        assertEquals(0, hc % 2)
    }

    @Test
    fun `dashboard renders modules and toggles english to hindi`() {
        var lang by mutableStateOf(AppLanguage.ENGLISH)
        composeRule.setContent {
            AIVideoStudioTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides lang,
                    LocalAppStrings provides stringsFor(lang)
                ) {
                    DashboardScreen(
                        activeProject = null,
                        projectCount = 0,
                        preferences = StudioPreferences(language = lang, onboardingCompleted = true),
                        isCloudSignedIn = false,
                        onModuleClick = {},
                        onToggleLanguage = { lang = it },
                        onQuickCreateBlank = {},
                        onDismissOnboarding = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("AI Video Studio").assertIsDisplayed()
        composeRule.onNodeWithTag("module_create_new").assertIsDisplayed()
        composeRule.onNodeWithTag("module_ai_auto").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_language_toggle").performClick()
        composeRule.onNodeWithText("AI वीडियो स्टूडियो").assertIsDisplayed()
    }
}
