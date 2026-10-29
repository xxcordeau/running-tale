pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK (Vehicle, NaviHelper, FusedLocation, STT, TTS)
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}

rootProject.name = "running-tale"

// 순수 Kotlin 스토리 엔진. Android SDK 없이도 빌드·테스트된다.
include(":story")

// Android 앱은 Android SDK가 있을 때만 포함한다 (Android Studio로 열면 local.properties가 생긴다).
val localProps = file("local.properties")
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (localProps.exists() && localProps.readText().contains("sdk.dir"))
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.warn("Android SDK를 찾지 못해 :app 모듈을 건너뜁니다. Android Studio로 프로젝트를 열면 포함됩니다.")
}
