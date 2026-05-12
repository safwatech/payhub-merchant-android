import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// The Google Services plugin wires google-services.json into the build. We only
// apply it when that file is present, so a fresh clone (no Firebase project)
// still builds — FCM is then a runtime no-op (the push layer checks Play
// services availability). Drop a real `app/google-services.json` to enable it.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val appVersionName = file("../VERSION").takeIf { it.exists() }?.readText()?.trim() ?: "0.1.0"

android {
    namespace = "ly.payhub.merchant"
    compileSdk = 34

    defaultConfig {
        applicationId = "ly.payhub.merchant"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // Optional release signing: reads keystore.properties (gitignored) if present,
    // else falls back to env vars (CI), else leaves release unsigned (local debug builds only).
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
    }
    signingConfigs {
        create("release") {
            val storePathStr = keystoreProps.getProperty("storeFile") ?: System.getenv("ANDROID_KEYSTORE_PATH")
            if (storePathStr != null) {
                storeFile = file(storePathStr)
                storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rsc = signingConfigs.getByName("release")
            if (rsc.storeFile != null) signingConfig = rsc
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ktlint {
    version.set("1.3.1")
    android.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/generated/") }
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.security.crypto)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.base)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    // Client-side QR rendering for the in-app two-factor enrolment screen.
    implementation(libs.zxing.core)

    // The PayHub bearer-token merchant client. See settings.gradle.kts for the
    // mavenLocal() note until 1.1.0 lands on Maven Central.
    implementation(libs.payhub.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
