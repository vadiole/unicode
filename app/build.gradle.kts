plugins {
    id("com.android.application")
}

android {
    namespace = "vadiole.unicode"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "vadiole.unicode"
        minSdk = 26
        targetSdk = 36
        versionCode = 171
        versionName = "1.7.0"
        base.archivesName.set("unicode-v$versionName")
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "cs", "da", "el", "es", "fr", "hi", "it", "ja", "ka", "nb", "nl", "pt", "pt-rBR", "ru", "sl", "sv", "tr", "uk", "zh-rCN")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles("proguard-rules.pro")
        }
    }

    packaging {
        resources {
            excludes += "kotlin/**"
            excludes += "DebugProbesKt.bin"
            excludes += "META-INF/**"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        disable.addAll(
            listOf(
                "UseKtx",
                "SetTextI18n",
                "RtlHardcoded", "RtlCompat", "RtlEnabled",
                "ViewConstructor",
                "UnusedAttribute",
                "NotifyDataSetChanged",
                "ktNoinlineFunc",
                "ClickableViewAccessibility",
            )
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
