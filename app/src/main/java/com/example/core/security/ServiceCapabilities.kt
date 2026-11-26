package com.example.core.security

enum class ServiceAccessType {
    OFFLINE_FREE,
    ONLINE_FREE_NO_KEY,
    ONLINE_FREE_WITH_KEY,
    ONLINE_PAID_OR_QUOTA
}

data class ServiceCapabilityInfo(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val accessType: ServiceAccessType,
    val freeQuotaNoteEn: String,
    val freeQuotaNoteHi: String,
    val fallbackEn: String,
    val fallbackHi: String
)

object ServiceCapabilities {
    val all: List<ServiceCapabilityInfo> = listOf(
        ServiceCapabilityInfo(
            id = "manual_editor_export",
            titleEn = "Manual Timeline Editor & Media3 MP4 Export",
            titleHi = "मैन्युअल टाइमलाइन एडिटर और Media3 MP4 एक्सपोर्ट",
            accessType = ServiceAccessType.OFFLINE_FREE,
            freeQuotaNoteEn = "100% free & offline on-device rendering (H.264 + AAC).",
            freeQuotaNoteHi = "100% मुफ्त और ऑफलाइन ऑन-डिवाइस रेंडरिंग (H.264 + AAC)।",
            fallbackEn = "Battery-saver 720p/24fps mode available for low-RAM devices.",
            fallbackHi = "कम-RAM फोन के लिए बैटरी-सेवर 720p/24fps मोड उपलब्ध।"
        ),
        ServiceCapabilityInfo(
            id = "tts_voiceover",
            titleEn = "AI Voice Studio (On-Device Android TTS)",
            titleHi = "AI वॉइस स्टूडियो (ऑन-डिवाइस Android TTS)",
            accessType = ServiceAccessType.OFFLINE_FREE,
            freeQuotaNoteEn = "Unlimited free offline synthesis in Hindi & English using device TTS voices.",
            freeQuotaNoteHi = "डिवाइस TTS आवाज़ों से हिंदी और अंग्रेज़ी में असीमित मुफ्त ऑफलाइन वॉइसओवर।",
            fallbackEn = "Import custom WAV/MP3 narration files anytime.",
            fallbackHi = "कभी भी अपनी WAV/MP3 वॉइस रिकॉर्डिंग इम्पोर्ट करें।"
        ),
        ServiceCapabilityInfo(
            id = "subtitles_audio_sfx",
            titleEn = "Subtitles, SRT, Beat Sync & Royalty-Free Audio",
            titleHi = "सबटाइटल्स, SRT, बीट सिंक और रॉयल्टी-फ्री ऑडियो",
            accessType = ServiceAccessType.OFFLINE_FREE,
            freeQuotaNoteEn = "On-device SRT import/export, PCM beat detection, and built-in CC0 synth loops/SFX.",
            freeQuotaNoteHi = "ऑन-डिवाइस SRT इम्पोर्ट/एक्सपोर्ट, बीट डिटेक्शन और बिल्ट-इन CC0 म्यूज़िक/SFX।",
            fallbackEn = "Import your own licensed audio tracks.",
            fallbackHi = "अपना लाइसेंस प्राप्त ऑडियो ट्रैक जोड़ें।"
        ),
        ServiceCapabilityInfo(
            id = "wikimedia_stock",
            titleEn = "Wikimedia Commons Media Search",
            titleHi = "Wikimedia Commons मीडिया खोज",
            accessType = ServiceAccessType.ONLINE_FREE_NO_KEY,
            freeQuotaNoteEn = "Free public API, no key needed. Tracks source URL, author, and CC/PD license.",
            freeQuotaNoteHi = "मुफ्त पब्लिक API, कोई कुंजी नहीं चाहिए। सोर्स URL और लाइसेंस ट्रैक करता है।",
            fallbackEn = "Import local photos/videos via Android Photo Picker offline.",
            fallbackHi = "ऑफलाइन Android Photo Picker से लोकल मीडिया चुनें।"
        ),
        ServiceCapabilityInfo(
            id = "firebase_ai_logic",
            titleEn = "Gemini Script, Storyboard, Fact-Check & SEO",
            titleHi = "Gemini स्क्रिप्ट, स्टोरीबोर्ड, फैक्ट-चेक और SEO",
            accessType = ServiceAccessType.ONLINE_FREE_NO_KEY,
            freeQuotaNoteEn = "Powered by Firebase AI Logic (gemini-3.8-flash / gemini-3.5-flash-lite) with free Spark limits; no API key required.",
            freeQuotaNoteHi = "Firebase AI Logic द्वारा संचालित; फ्री कोटा के साथ कोई API कुंजी आवश्यक नहीं।",
            fallbackEn = "Manual script & storyboard editor works offline if quota is reached.",
            fallbackHi = "कोटा पूरा होने पर मैन्युअल स्क्रिप्ट और स्टोरीबोर्ड ऑफलाइन काम करता है।"
        ),
        ServiceCapabilityInfo(
            id = "ai_image_video_gen",
            titleEn = "AI Image (Nano Banana) & Text/Image-to-Video",
            titleHi = "AI इमेज और टेक्स्ट/इमेज-टू-वीडियो जेनरेशन",
            accessType = ServiceAccessType.ONLINE_PAID_OR_QUOTA,
            freeQuotaNoteEn = "AI images (gemini-nano-banana-2.1) require Firebase Blaze; Interactions API video (gemini-omni-1.2-flash) requires a user API key with video quota.",
            freeQuotaNoteHi = "AI इमेज के लिए Firebase Blaze और वीडियो जेनरेशन के लिए यूज़र API कुंजी व कोटा आवश्यक है।",
            fallbackEn = "Use free Wikimedia/Pexels/Pixabay stock footage + Media3 Ken Burns motion effects.",
            fallbackHi = "मुफ्त स्टॉक मीडिया और Media3 मोशन इफेक्ट्स का उपयोग करें।"
        ),
        ServiceCapabilityInfo(
            id = "pexels_pixabay",
            titleEn = "Pexels & Pixabay Stock Video/Photo Search",
            titleHi = "Pexels और Pixabay स्टॉक वीडियो/फोटो खोज",
            accessType = ServiceAccessType.ONLINE_FREE_WITH_KEY,
            freeQuotaNoteEn = "Free developer tiers (Pexels 200 req/hr, Pixabay 100 req/min) using your encrypted API key.",
            freeQuotaNoteHi = "आपकी एन्क्रिप्टेड API कुंजी के साथ मुफ्त डेवलपर टियर।",
            fallbackEn = "Wikimedia Commons & local Photo Picker work without any API key.",
            fallbackHi = "Wikimedia Commons और Photo Picker बिना API कुंजी के काम करते हैं।"
        ),
        ServiceCapabilityInfo(
            id = "youtube_upload",
            titleEn = "YouTube Channel OAuth 2.0 & Direct Upload",
            titleHi = "YouTube चैनल OAuth 2.0 और डायरेक्ट अपलोड",
            accessType = ServiceAccessType.ONLINE_FREE_WITH_KEY,
            freeQuotaNoteEn = "Official YouTube Data API v3 (10,000 free quota units/day; ~1,600 units per video upload). Requires your OAuth Client ID.",
            freeQuotaNoteHi = "आधिकारिक YouTube Data API v3 (10,000 यूनिट/दिन मुफ्त कोटा)। OAuth Client ID आवश्यक।",
            fallbackEn = "Export MP4 to Movies/AI Video Studio and share directly to the YouTube app.",
            fallbackHi = "MP4 एक्सपोर्ट करें और सीधे YouTube ऐप पर शेयर करें।"
        ),
        ServiceCapabilityInfo(
            id = "cloud_backup",
            titleEn = "Firestore Cloud Project Backup & Brand Kit Sync",
            titleHi = "Firestore क्लाउड बैकअप और ब्रांड किट सिंक",
            accessType = ServiceAccessType.ONLINE_FREE_NO_KEY,
            freeQuotaNoteEn = "Optional sync via Google Sign-In on Firebase Spark free tier (metadata/scripts only; large video files stay local).",
            freeQuotaNoteHi = "Google साइन-इन के साथ वैकल्पिक सिंक (केवल स्क्रिप्ट/प्रोजेक्ट डेटा; वीडियो लोकल रहते हैं)।",
            fallbackEn = "Export/import full .aivs.json project backups locally.",
            fallbackHi = "लोकल .aivs.json प्रोजेक्ट बैकअप एक्सपोर्ट/इम्पोर्ट करें।"
        )
    )
}
