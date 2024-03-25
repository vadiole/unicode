plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "vadiole.unicode"
        minSdk = 26
        targetSdk = 34
        versionCode = 142
        versionName = "1.4.2"
        resourceConfigurations.addAll(listOf("en"))
        setProperty("archivesBaseName", "unicode-v$versionName")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
        }

        getByName("release") {
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
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    lint {
        disable.addAll(
            listOf(
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
    namespace = "vadiole.unicode"
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0-alpha03")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.0.0-alpha03")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
