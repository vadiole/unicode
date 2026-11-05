plugins {
    id("com.android.application")
}

android {
    namespace = "vadiole.unicode"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "vadiole.unicode"
        minSdk = 23
        targetSdk = 37
        versionCode = 173
        versionName = "1.9.0"
        buildConfigField("String", "UNICODE_VERSION", "\"18.0.0\"")
        buildConfigField("String", "DATABASE_NAME", "\"u18.sqlite\"")
        buildConfigField("int", "TOTAL_CHARACTERS", "41341")
        base.archivesName.set("unicode-v$versionName")
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "cs", "da", "de", "el", "es", "fr", "hi", "it", "ja", "ka", "nb", "nl", "pl", "pt", "pt-rBR", "ru", "sl", "sv", "tr", "uk", "zh-rCN")
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
        baseline = file("lint-baseline.xml")
        disable.addAll(
            listOf(
                "UseKtx",
                "SetTextI18n",
                "RtlHardcoded", "RtlCompat", "RtlEnabled",
                "ViewConstructor",
                "UnusedAttribute",
                "NotifyDataSetChanged",
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
