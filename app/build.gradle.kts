plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.outsmartis.sofa"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.outsmartis.sofa"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("SOFA_VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("SOFA_VERSION_NAME") ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("SOFA_KEYSTORE")?.let { file(it) }
            if (ks != null && ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("SOFA_KEYSTORE_PASSWORD")
                keyAlias = "sofa"
                keyPassword = System.getenv("SOFA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 keeps the APK small despite material-icons-extended
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
