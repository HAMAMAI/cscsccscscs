import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun config(name: String, fallback: String): String =
    providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name)
        ?: providers.gradleProperty(name).orNull
        ?: fallback

android {
    namespace = "app.takt.messenger"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.takt.messenger"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // A Supabase publishable key is designed for mobile clients. Row Level
        // Security protects every exposed row; no secret/service key is embedded.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${config("SUPABASE_URL", "https://qewgunjxdpliyeazyjkn.supabase.co")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${config("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_oFE1KGP-BLnRJT_IaJ30Bg_eaFyPaf2")}\"",
        )
        // Compatibility aliases keep the isolated source copy buildable while
        // the new client uses the names above.
        buildConfigField(
            "String",
            "SUPABASE_KEY",
            "\"${config("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_oFE1KGP-BLnRJT_IaJ30Bg_eaFyPaf2")}\"",
        )
        buildConfigField(
            "String",
            "TAKT_API_BASE_URL",
            "\"${config("TAKT_API_BASE_URL", "")}\"",
        )
        // This remains empty until the supplied LiveKit infrastructure has a
        // public token endpoint. Calls fail closed instead of exposing a secret.
        buildConfigField(
            "String",
            "LIVEKIT_TOKEN_URL",
            "\"${config("LIVEKIT_TOKEN_URL", "")}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.browser:browser:1.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    implementation("io.livekit:livekit-android:2.27.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
