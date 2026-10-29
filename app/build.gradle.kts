import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.runningtale.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runningtale.app"
        minSdk = 28 // 공모전 개발 환경: Android API 28 이상
        targetSdk = 34 // Pleos Connect Emulator 시스템 이미지가 API 34
        versionCode = 1
        versionName = "0.1.0"

        // Pleos TTS 앱 인증 키. 커밋하지 않도록 local.properties에 둔다:
        //   pleos.tts.clientId=...  /  pleos.tts.clientSecret=...
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        buildConfigField("String", "PLEOS_TTS_CLIENT_ID", "\"${local.getProperty("pleos.tts.clientId", "")}\"")
        buildConfigField("String", "PLEOS_TTS_CLIENT_SECRET", "\"${local.getProperty("pleos.tts.clientSecret", "")}\"")
    }

    buildFeatures { buildConfig = true }

    // AAOS 표준 차속(PERF_VEHICLE_SPEED). Pleos Vehicle SDK에 차속 API가 없어서 쓴다.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":story"))

    // Pleos Connect SDK (document.pleos.ai 각 SDK의 Importing 페이지 기준 버전)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
}
