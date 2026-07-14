import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing reads from keystore.properties at the repo root (gitignored,
// never committed). When the file is absent (fresh clone / CI without secrets)
// the release build falls back to the debug key so it still assembles.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.glyphnavtoy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glyphnavtoy"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Real upload key when keystore.properties is present; otherwise the
            // debug key (lets the release variant assemble without secrets).
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Two flavors, installable side by side:
    //   user → the clean product (no dev tools, no on-disk capture logging)
    //   dev  → everything: dev screen, route simulator, capture logging
    // BuildConfig.IS_DEV gates the difference in code; the .dev applicationId
    // suffix lets both live on the phone at once.
    flavorDimensions += "tier"
    productFlavors {
        create("user") {
            dimension = "tier"
            isDefault = true
            buildConfigField("boolean", "IS_DEV", "false")
            // app_name comes from src/main → "GlyphMaps"
        }
        create("dev") {
            dimension = "tier"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("boolean", "IS_DEV", "true")
            // app_name overridden in src/dev → "GlyphMaps Dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))
    debugImplementation(libs.androidx.ui.tooling)
}
