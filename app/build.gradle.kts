plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.navigation.safeargs)
}

import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.salmanlaghari.pkai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salmanlaghari.pkai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Secure helper to read non-blank values from System Env or local.properties
        fun getSecureProperty(propKey: String): String {
            val envVal = System.getenv(propKey)
            if (!envVal.isNullOrBlank()) return envVal
            val propVal = localProperties.getProperty(propKey)
            if (!propVal.isNullOrBlank()) return propVal
            return ""
        }

        val geminiApiKey = getSecureProperty("GEMINI_API_KEY")
        val openrouterApiKey = getSecureProperty("OPENROUTER_API_KEY")
        val groqApiKey = getSecureProperty("GROQ_API_KEY")
        val togetherApiKey = getSecureProperty("TOGETHER_API_KEY")
        val cohereApiKey = getSecureProperty("COHERE_API_KEY")
        val cerebrasApiKey = getSecureProperty("CEREBRAS_API_KEY")
        val openaiApiKey = getSecureProperty("OPENAI_API_KEY")
        val sambanovaApiKey = getSecureProperty("SAMBANOVA_API_KEY")

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openrouterApiKey\"")
        buildConfigField("String", "GROQ_API_KEY", "\"$groqApiKey\"")
        buildConfigField("String", "TOGETHER_API_KEY", "\"$togetherApiKey\"")
        buildConfigField("String", "COHERE_API_KEY", "\"$cohereApiKey\"")
        buildConfigField("String", "CEREBRAS_API_KEY", "\"$cerebrasApiKey\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openaiApiKey\"")
        buildConfigField("String", "SAMBANOVA_API_KEY", "\"$sambanovaApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
