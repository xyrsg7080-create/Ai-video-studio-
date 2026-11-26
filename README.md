# AI Video Studio (Android)

Native Kotlin + Jetpack Compose Android video creation studio (`minSdk 24`, `targetSdk 36`) supporting AI-assisted Shorts/long-form video production, an offline multi-track timeline editor, Media3 MP4 export, bilingual English & Hindi UI/narration, and optional OAuth 2.0 YouTube uploading.

## Architecture & Tech Stack
- **UI**: Jetpack Compose + Material 3 dark studio theme (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`) + adaptive `NavigationBar` / `NavigationRail`.
- **State & Local Persistence**: `StudioViewModel`, Room (`video_projects`, `project_scenes`, `timeline_clips`, `project_subtitles`, `media_assets`, `brand_kits`, `scheduled_uploads`, `background_jobs`), DataStore preferences, `.aivs.json` local import/export.
- **Video & Audio Engine**: AndroidX Media3 `Transformer` (`1.11.1`) H.264 + AAC export (`9:16`, `16:9`, `1:1`, Custom; `720p`/`1080p`/`4K` where supported by device codecs), Ken Burns/pan/shake matrix effects, brightness/contrast/color grading, burned-in subtitle/watermark `BitmapOverlay`, PCM WAV audio mixer with voice ducking & beat onset detection, and on-device `TextToSpeech` synthesis.
- **AI & Cloud Services**:
  - **Firebase AI Logic + App Check**: Keyless Gemini script/storyboard/fact-check/SEO/translation (`gemini-3.8-flash`, `gemini-3.5-flash-lite`, `gemini-4-argon`) and multimodal video frame analysis.
  - **Firebase Auth (Credential Manager Google Sign-In) + Firestore**: Optional cloud backup & restore for project metadata/scripts/Brand Kits (offline mode works without sign-in).
- **Security & Play Policy Compliance**:
  - Zero broad storage permissions (`READ_MEDIA_*` / `READ_EXTERNAL_STORAGE` omitted); uses Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (`OpenDocument` / `CreateDocument`).
  - User-provided API keys and OAuth tokens are encrypted at rest with Android Keystore AES-256-GCM (`SecureKeyStore`).

## Service Tiers, Keys & Limitations Matrix

| Feature | Access Tier | Requirements / Limits | Offline / Free Fallback |
| --- | --- | --- | --- |
| Multi-Track Timeline Editor & MP4 Export | **Offline Free** | Device CPU/MediaCodec (`4K` disabled on devices without 4K H.264 encoder) | Battery-saver `720p / 24fps` mode |
| AI Voice Studio (EN & HI) | **Offline Free** | Device `TextToSpeech` voices installed | Import local WAV/MP3 narration |
| Subtitles, SRT, Beat Sync, Music & SFX | **Offline Free** | Built-in CC0 synthesized music & SFX | Import custom licensed audio |
| Wikimedia Commons Stock Search | **Online Free (No Key)** | Internet access; tracks license & attribution | Photo Picker local media |
| Gemini Script, Storyboard, Fact-Check, SEO | **Online Free (No Key)** | Firebase AI Logic Spark free quota + App Check | Manual script & storyboard editing |
| Pexels & Pixabay Stock Footage | **Online Free (User Key)** | Free Pexels (`200/hr`) / Pixabay (`100/min`) API key entered in Settings | Wikimedia Commons & Photo Picker |
| AI Image (`gemini-nano-banana-2.1`) | **Quota / Blaze** | Requires Firebase Blaze billing on project | Stock footage + Ken Burns motion |
| AI Video (`gemini-omni-1.2-flash` Interactions) | **User Key / Quota** | Requires user Gemini API key with video quota | Stock video/images + Media3 effects |
| YouTube OAuth 2.0 & Direct Upload | **Online Free (Client ID)** | Google Cloud OAuth 2.0 Android/Installed Client ID (`10,000` units/day) | Export MP4 & Share to YouTube app |
| Cloud Project Backup | **Online Free (Sign-In)** | Google Sign-In via Credential Manager | Local `.aivs.json` export/import |

## Optional API Setup (Inside the App)
All optional third-party keys are entered securely by the user in **Settings -> Encrypted User API Keys** (stored via Android Keystore AES-256-GCM, never hardcoded):
1. **Pexels API Key**: Create a free developer key at pexels.com/api.
2. **Pixabay API Key**: Create a free key at pixabay.com/api/docs.
3. **YouTube OAuth 2.0 Client ID**:
   - In Google Cloud Console, enable **YouTube Data API v3**.
   - Create an OAuth 2.0 Client ID (Android or Desktop/Installed flow supporting custom URI scheme `com.aistudio.aivideostudio.kqzxpm:/oauth2redirect`).
   - Add the scopes `youtube.upload` and `youtube.readonly` in OAuth Consent Screen.
   - Paste the Client ID into **Settings -> YouTube OAuth 2.0 Client ID**, then connect in **YouTube Studio**.

## Building & Testing Locally
- Run JVM unit & Robolectric tests:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- Build Debug APK:
  ```bash
  gradle :app:assembleDebug
  ```
  Output: `app/build/outputs/apk/debug/app-debug.apk`
- Build Signed Release APK/AAB (set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` env vars):
  ```bash
  gradle :app:assembleRelease
  gradle :app:bundleRelease
  ```
