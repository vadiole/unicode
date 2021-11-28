plugins {
    id("com.android.application")
    kotlin("android")
//    kotlin("kapt")
}

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "vadiole.unicode"
        minSdk = 26
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations.addAll(listOf("en"))
        setProperty("archivesBaseName", "Unicode v$versionName ($versionCode)")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packagingOptions {
        resources.excludes.addAll(
            listOf(
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/java.properties",
            )
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        disable(
            "SetTextI18n",
            "RtlHardcoded", "RtlCompat", "RtlEnabled",
            "ViewConstructor",
            "UnusedAttribute"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.7.0")
}