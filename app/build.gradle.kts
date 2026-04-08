plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "vadiole.unicode"
    compileSdk = 35

    defaultConfig {
        applicationId = "vadiole.unicode"
        minSdk = 26
        targetSdk = 35
        versionCode = 152
        versionName = "1.5.2"
        setProperty("archivesBaseName", "unicode-v$versionName")
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
            proguardFiles("proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes.addAll(
            listOf(
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/java.properties",
            )
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}