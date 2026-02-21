import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// ── Versioning ───────────────────────────────────────────────────────────────
// versionCode is set by CI via -PversionCode=<run_number>, defaulting to 1
val ciVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
// versionName is set by CI via -PversionName=<tag>, defaulting to 0.0.1
val ciVersionName = (project.findProperty("versionName") as String?) ?: "0.0.1"

// ── Signing ───────────────────────────────────────────────────────────────────
// Keystore is provided via environment variables in CI; never stored in repo.
// Uses layout.buildDirectory instead of deprecated buildDir.
fun decodeKeystoreIfNeeded(): File? {
    val b64 = System.getenv("EVENAI_KEYSTORE_BASE64") ?: return null
    val file = layout.buildDirectory.file("release.jks").get().asFile
    file.parentFile.mkdirs()
    file.writeBytes(Base64.getDecoder().decode(b64))
    return file
}

// ── Read local.properties for dev convenience ─────────────────────────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val openAiKey: String = System.getenv("OPENAI_API_KEY")
    ?: localProps.getProperty("OPENAI_API_KEY", "")

// ── Keystore env vars (CI sets these; local dev can leave them blank) ─────────
val ksFile: File? = decodeKeystoreIfNeeded()
val ksPassword: String  = System.getenv("EVENAI_KEYSTORE_PASSWORD") ?: ""
val ksAlias: String     = System.getenv("EVENAI_KEY_ALIAS")          ?: ""
val ksKeyPass: String   = System.getenv("EVENAI_KEY_PASSWORD")        ?: ""

android {
    namespace = "com.evenai.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.evenai.companion"
        minSdk = 31
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiKey\"")
    }

    signingConfigs {
        // Only create a release signing config when all env vars are present.
        // This prevents Gradle configuration errors on local dev machines and
        // on CI debug builds where no keystore is provided.
        if (ksFile != null && ksPassword.isNotEmpty() && ksAlias.isNotEmpty()) {
            create("release") {
                storeFile     = ksFile
                storePassword = ksPassword
                keyAlias      = ksAlias
                keyPassword   = ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply release signing only when the config was created above
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable          = true
            applicationIdSuffix   = ".debug"
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
        compose     = true
        buildConfig = true
    }

    // ── Lint ──────────────────────────────────────────────────────────────────
    // Treat lint errors as warnings in CI so the build doesn't hard-fail on
    // style issues before the app is in production. abortOnError = false means
    // lint runs and uploads its report but does not break the workflow.
    lint {
        abortOnError   = false
        checkReleaseBuilds = false
        // Write the HTML report that CI uploads as an artifact
        htmlReport     = true
        htmlOutput     = file("build/reports/lint-results-debug.html")
        xmlReport      = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // Networking (OpenAI WebSocket)
    implementation(libs.okhttp)

    // DataStore (preferences persistence)
    implementation(libs.datastore.preferences)

    // Accompanist permissions
    implementation(libs.accompanist.permissions)

    // Window size (foldable support)
    implementation(libs.window)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}
