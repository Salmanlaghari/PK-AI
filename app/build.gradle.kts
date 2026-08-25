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
        val openRouterApiKey = System.getenv("OPENROUTER_API_KEY") ?: localProperties.getProperty("OPENROUTER_API_KEY") ?: ""

        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "CLOUDFLARE_API_TOKEN", "\"$cloudflareApiToken\"")
        buildConfigField("String", "CLOUDFLARE_ACCOUNT_ID", "\"$cloudflareAccountId\"")
        buildConfigField("String", "LLM7_API_KEY", "\"$llm7ApiKey\"")
        buildConfigField("String", "MISTRAL_API_KEY", "\"$mistralApiKey\"")
        buildConfigField("String", "COHERE_API_KEY", "\"$cohereApiKey\"")
        buildConfigField("String", "CEREBRAS_API_KEY", "\"$cerebrasApiKey\"")
        buildConfigField("String", "HUGGINGFACE_API_KEY", "\"$huggingfaceApiKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterApiKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("pk-ai-upload-key.jks")

            val envPassword = System.getenv("KEYSTORE_PASSWORD")
            val defaultPassword = "PkAiUploadKey99#@!"
            val chosenPassword = if (!envPassword.isNullOrBlank()) {
                try {
                    val keystore = KeyStore.getInstance("PKCS12")
                    storeFile?.inputStream()?.use { keystore.load(it, envPassword.toCharArray()) }
                    envPassword
                } catch (e: Exception) {
                    defaultPassword
                }
            } else {
                localProperties.getProperty("KEYSTORE_PASSWORD") ?: defaultPassword
            }

            storePassword = chosenPassword
            keyAlias = "pk_ai_upload"
            keyPassword = chosenPassword
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
    implementation(libs.google.identity.googleid)

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
