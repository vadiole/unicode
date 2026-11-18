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
        versionCode = 176
        versionName = "1.10.0"
        buildConfigField("String", "UNICODE_VERSION", "\"18.0.0\"")
        base.archivesName.set("unicode-v$versionName")
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "cs", "da", "de", "el", "es", "fr", "hi", "it", "ja", "ka", "nb", "nl", "pl", "pt", "pt-rBR", "ru", "sl", "sv", "tr", "uk", "zh-rCN")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val storeFile = providers.gradleProperty("unicode.signing.storeFile").orNull
        if (storeFile != null) {
            create("release") {
                val password = file(providers.gradleProperty("unicode.signing.passwordFile").get()).readText().trim()
                this.storeFile = file(storeFile)
                storePassword = password
                keyAlias = providers.gradleProperty("unicode.signing.keyAlias").get()
                keyPassword = password
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles("proguard-rules.pro")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles("proguard-rules.pro")
        }

        create("alpha") {
            initWith(getByName("release"))
            applicationIdSuffix = ".alpha"
            signingConfig = signingConfigs.getByName("debug")
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

    sourceSets {
        getByName("debug") {
            assets.srcDir("src/testDebug/resources")
        }
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

val copyReleaseMapping = tasks.register<Copy>("copyReleaseMapping") {
    from(layout.buildDirectory.file("outputs/mapping/release/mapping.txt"))
    into(layout.projectDirectory.dir("mapping"))
}

tasks.matching { it.name == "minifyReleaseWithR8" }.configureEach {
    finalizedBy(copyReleaseMapping)
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
