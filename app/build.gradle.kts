plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.navigation.safeargs)
    id("com.google.gms.google-services")
}

import java.util.Properties
import java.security.KeyStore
import java.util.Base64
import org.gradle.api.GradleException

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.salmanlaghari.pkai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.salmanlaghari.pkai"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val groqApiKey = System.getenv("GROQ_API_KEY") ?: localProperties.getProperty("GROQ_API_KEY") ?: ""
        val cloudflareApiToken = System.getenv("CLOUDFLARE_API_TOKEN") ?: localProperties.getProperty("CLOUDFLARE_API_TOKEN") ?: ""
        val cloudflareAccountId = System.getenv("CLOUDFLARE_ACCOUNT_ID") ?: localProperties.getProperty("CLOUDFLARE_ACCOUNT_ID") ?: ""
        val llm7ApiKey = System.getenv("LLM7_API_KEY") ?: localProperties.getProperty("LLM7_API_KEY") ?: ""
        val mistralApiKey = System.getenv("MISTRAL_API_KEY") ?: localProperties.getProperty("MISTRAL_API_KEY") ?: ""
        val cohereApiKey = System.getenv("COHERE_API_KEY") ?: localProperties.getProperty("COHERE_API_KEY") ?: ""
        val cerebrasApiKey = System.getenv("CEREBRAS_API_KEY") ?: localProperties.getProperty("CEREBRAS_API_KEY") ?: ""
        val huggingfaceApiKey = System.getenv("HUGGINGFACE_API_KEY") ?: localProperties.getProperty("HUGGINGFACE_API_KEY") ?: ""
        val groqModel = System.getenv("GROQ_MODEL") ?: localProperties.getProperty("GROQ_MODEL") ?: "openai/gpt-oss-20b"
        val openRouterApiKey = System.getenv("OPENROUTER_API_KEY") ?: localProperties.getProperty("OPENROUTER_API_KEY") ?: ""
        val hackerEarthClientId = System.getenv("HACKEREARTH_CLIENT_ID") ?: localProperties.getProperty("HACKEREARTH_CLIENT_ID") ?: ""
        val hackerEarthClientSecret = System.getenv("HACKEREARTH_CLIENT_SECRET") ?: localProperties.getProperty("HACKEREARTH_CLIENT_SECRET") ?: ""
        val codeRunnerProxyUrl = System.getenv("CODE_RUNNER_PROXY_URL") ?: localProperties.getProperty("CODE_RUNNER_PROXY_URL") ?: ""
        val pollinationsApiKey = System.getenv("POLLINATIONS_API_KEY") ?: localProperties.getProperty("POLLINATIONS_API_KEY") ?: ""
        val puterAuthToken = System.getenv("PUTER_AUTH_TOKEN") ?: localProperties.getProperty("PUTER_AUTH_TOKEN") ?: ""
        val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: localProperties.getProperty("GEMINI_API_KEY") ?: ""

        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "GROQ_MODEL", "\"$groqModel\"")
        buildConfigField("String", "CLOUDFLARE_API_TOKEN", "\"$cloudflareApiToken\"")
        buildConfigField("String", "CLOUDFLARE_ACCOUNT_ID", "\"$cloudflareAccountId\"")
        buildConfigField("String", "LLM7_API_KEY", "\"$llm7ApiKey\"")
        buildConfigField("String", "MISTRAL_API_KEY", "\"$mistralApiKey\"")
        buildConfigField("String", "COHERE_API_KEY", "\"$cohereApiKey\"")
        buildConfigField("String", "CEREBRAS_API_KEY", "\"$cerebrasApiKey\"")
        buildConfigField("String", "HUGGINGFACE_API_KEY", "\"$huggingfaceApiKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")
        buildConfigField("String", "HACKEREARTH_CLIENT_ID", "\"$hackerEarthClientId\"")
        buildConfigField("String", "HACKEREARTH_CLIENT_SECRET", "\"$hackerEarthClientSecret\"")
        buildConfigField("String", "CODE_RUNNER_PROXY_URL", "\"$codeRunnerProxyUrl\"")
        buildConfigField("String", "POLLINATIONS_API_KEY", "\"$pollinationsApiKey\"")
        buildConfigField("String", "PUTER_AUTH_TOKEN", "\"$puterAuthToken\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    signingConfigs {
        create("release") {
            // Read keystore location from environment variable or fallback to local file
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
            val keystoreBase64 = System.getenv("KEYSTORE_BASE64") ?: ""

            val storeFile: File? = try {
                when {
                    keystoreBase64.isNotBlank() -> {
                        val tempFile = File.createTempFile("upload-key", ".jks")
                        tempFile.writeBytes(Base64.getDecoder().decode(keystoreBase64.trim()))
                        tempFile.deleteOnExit()
                        tempFile
                    }
                    keystorePath.isNotBlank() && File(keystorePath).exists() -> File(keystorePath)
                    rootProject.file("pk-ai-upload-key.jks").exists() -> rootProject.file("pk-ai-upload-key.jks")
                    else -> null
                }
            } catch (_: Exception) {
                null
            }

            val storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("KEYSTORE_PASSWORD") ?: ""
            val keyPassword = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("KEY_PASSWORD") ?: ""
            val keyAlias = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("KEY_ALIAS") ?: "pk_ai_upload"

            if (storeFile != null && storeFile.exists()) {
                this.storeFile = storeFile
                this.storePassword = storePassword
                this.keyPassword = keyPassword
                this.keyAlias = keyAlias
            }

            val validStoreFile = storeFile
            project.gradle.taskGraph.whenReady {
                val isReleaseScheduled = allTasks.any { it.name.contains("Release", ignoreCase = true) }
                if (isReleaseScheduled && (validStoreFile == null || !validStoreFile.exists())) {
                    throw GradleException("🚨 ERROR: Missing required release keystore file for release signing. Please set KEYSTORE_BASE64 or KEYSTORE_PATH.")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Let JVM unit tests call framework stubs (e.g. android.util.Log used by the
            // provider debug logging) instead of throwing "not mocked".
            isReturnDefaultValues = true
            all {
                (this as? org.gradle.api.tasks.testing.Test)?.jvmArgs(
                    "-Djdk.attach.allowAttachSelf=true",
                    "-XX:+EnableDynamicAgentLoading"
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Credential Manager & Google ID Services
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation("androidx.webkit:webkit:1.12.1")
    implementation(libs.google.identity.googleid)

    // Encrypted storage for the Flow Music bridge session (refresh token)
    implementation(libs.androidx.security.crypto)

    // Chrome Custom Tabs - used for the Google OAuth hand-off so the sign-in
    // never happens inside an embedded WebView (which Google blocks with
    // "Browser not supported" / disallowed_useragent).
    implementation(libs.androidx.browser)

    // Google AdMob
    implementation(libs.google.admob)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

// ---------------------------------------------------------------------------
// Ultra AI web UI: rebuild + sync into Android assets (best-effort).
// The chat UI lives in ultra-ai-chat-space/ (React/Vite). Its built output is
// what the APK ships under src/main/assets/ultra-ai-chat-space. When node is
// available this task rebuilds it before assets are merged, so the APK never
// ships a stale web UI; when node is missing it keeps the committed assets
// and only warns. Gradle's up-to-date checks skip the rebuild entirely when
// the web sources have not changed.
// ---------------------------------------------------------------------------
val webDir = rootDir.resolve("ultra-ai-chat-space")
val webDistDir = webDir.resolve("dist")
val webAssetsDir = projectDir.resolve("src/main/assets/ultra-ai-chat-space")

val syncWebAssets by tasks.registering(Exec::class) {
    group = "build"
    description = "Rebuilds the Ultra AI web UI and syncs it into Android assets."

    inputs.dir(webDir.resolve("src"))
    inputs.dir(webDir.resolve("public"))
    inputs.file(webDir.resolve("package.json"))
    inputs.file(webDir.resolve("package-lock.json")).optional()
    outputs.dir(webDistDir)
    // NOTE: src/main/assets/ultra-ai-chat-space is intentionally NOT declared
    // as an output. Other tasks (merge assets, lint model, ...) consume
    // src/main/assets without depending on this task, and declaring it as an
    // output fails Gradle validation ("uses this output ... without declaring
    // an explicit or implicit dependency"). Instead this best-effort sentinel
    // re-runs the sync whenever one of the runtime-critical shipped files is
    // missing: index.html (the page), flowmusic-automation.js (injected by
    // AiHubFragment), and the assets/ chunk directory (hashed js/css).
    outputs.upToDateWhen {
        webAssetsDir.resolve("index.html").isFile &&
            webAssetsDir.resolve("flowmusic-automation.js").isFile &&
            webAssetsDir.resolve("assets").isDirectory
    }

    workingDir = webDir
    // Best-effort: the Android build must never fail because of the web
    // toolchain. The exit code is still inspected below — a failed build
    // never touches the shipped assets.
    isIgnoreExitValue = true

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val npmCmd = if (isWindows) "npm.cmd" else "npm"

    // Probe for node first; without it there is nothing to do. didBuild tracks
    // whether the npm build actually ran: the no-node probe below exits 0 by
    // design, so the exit code alone cannot prove a fresh build happened.
    val nodeOk = try {
        val probe = ProcessBuilder(if (isWindows) "node.exe" else "node", "--version")
            .redirectErrorStream(true)
            .start()
        probe.waitFor() == 0
    } catch (_: Exception) {
        false
    }
    val didBuild = nodeOk
    if (!nodeOk) {
        logger.warn("syncWebAssets: node not found - keeping committed web assets.")
        if (isWindows) {
            commandLine("cmd", "/c", "exit", "0")
        } else {
            commandLine("true")
        }
    } else {
        // NOTE: pass each token as its own argument - commandLine does NOT
        // flatten a List, it would stringify it to "[sh, -c]".
        if (isWindows) {
            commandLine("cmd", "/c", "$npmCmd ci --no-audit --no-fund && $npmCmd run build")
        } else {
            commandLine("sh", "-c", "$npmCmd ci --no-audit --no-fund && $npmCmd run build")
        }
    }

    doLast {
        // Never touch the shipped assets unless the npm build really ran.
        // (The no-node probe exits 0 by design, and a stale dist/ from an
        // older run could otherwise be mistaken for a fresh build.)
        if (!didBuild) {
            logger.warn("syncWebAssets: node not available - keeping committed web assets.")
            return@doLast
        }
        // `vite build` empties dist/ before writing, so a build that dies
        // mid-way leaves an empty/partial directory: only sync when npm
        // exited 0 AND dist/index.html actually exists.
        val exit = executionResult.get().exitValue
        val builtIndex = webDistDir.resolve("index.html")
        if (exit != 0 || !builtIndex.isFile) {
            logger.warn("syncWebAssets: web build failed (exit=$exit) or dist/index.html missing - keeping existing assets.")
            return@doLast
        }
        // Copy to a temp dir and verify BEFORE touching the shipped assets,
        // so a partial copy can never leave the APK with a dead web UI.
        val tmp = layout.buildDirectory.dir("tmp/web-assets-sync").get().asFile
        tmp.deleteRecursively()
        webDistDir.copyRecursively(tmp, overwrite = true)
        if (tmp.resolve("index.html").isFile) {
            webAssetsDir.deleteRecursively()
            tmp.copyRecursively(webAssetsDir, overwrite = true)
            logger.lifecycle("syncWebAssets: web UI synced into Android assets.")
        } else {
            logger.warn("syncWebAssets: copy verification failed - keeping existing assets.")
        }
        tmp.deleteRecursively()
    }
}

// Rebuild the web UI before any asset merge (debug/release, unit tests, etc.).
tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
    dependsOn(syncWebAssets)
}
