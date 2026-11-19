import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Release signing: CI supplies env vars; locally, use an untracked keystore.properties.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    System.getenv(env) ?: keystoreProps.getProperty(key)

val releaseStoreFile = signingValue("storeFile", "NOTED_KEYSTORE_PATH")

android {
    namespace = "io.github.interloper.noted"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.interloper.noted"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("noted.versionCode").get().toInt()
        versionName = providers.gradleProperty("noted.versionName").get()
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "NOTED_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NOTED_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NOTED_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Keep the Google-encrypted dependency metadata blob out of the APK (cleaner for
    // Obtainium/F-Droid-style distribution).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        // Robolectric needs merged resources and the manifest for in-memory Room tests.
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    // Exported schema JSON is committed so future migrations can be tested against it.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    // Only the foundation-level editor is used; leaving out the library's Material wrappers keeps the app on the
    // stable Material 3 release from the Compose BOM.
    implementation(libs.richeditor.compose) {
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.material.color.utilities)
    implementation(libs.androidx.biometric)
    // Biometric 1.1.0 pulls in a Fragment release from before the Activity Result API; with it, launching any
    // file picker from a FragmentActivity crashes ("Can only use lower 16 bits for requestCode").
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
