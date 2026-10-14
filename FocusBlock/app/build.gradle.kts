plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.sushil.focusblock"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sushil.focusblock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing: values come from ~/.gradle/gradle.properties so secrets never enter the repo.
    // FOCUSBLOCK_STORE_FILE, FOCUSBLOCK_STORE_PASSWORD, FOCUSBLOCK_KEY_ALIAS, FOCUSBLOCK_KEY_PASSWORD
    val storeFilePath = providers.gradleProperty("FOCUSBLOCK_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("FOCUSBLOCK_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("FOCUSBLOCK_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("FOCUSBLOCK_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            // Without a keystore configured the release APK is built unsigned
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
