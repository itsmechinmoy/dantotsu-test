import java.util.Properties

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
}

if (gradle.startParameter.taskNames.any { it.contains("google", true) }) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}


val gitCommitHash = providers.exec {
    commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
}.standardOutput.asText.get().trim()

android {
    namespace = "ani.dantotsu"
    compileSdk = 36

    val apikeyProperties = Properties()
    val apikeyPropertiesFile = rootProject.file("apikey.properties")
    if (apikeyPropertiesFile.exists()) {
        apikeyPropertiesFile.reader(Charsets.UTF_8).use<java.io.Reader, Unit> { apikeyProperties.load(it) }
    }

    defaultConfig {
        applicationId = "ani.dantotsu"
        minSdk = 21
        targetSdk = 36

        versionName = "3.2.2"
        versionCode = (versionName ?: "1.0.0").split(".")
            //noinspection WrongGradleMethod
            .map { it.toInt() * 100 }
            .joinToString("")
            .toInt()

        signingConfig = signingConfigs.getByName("debug")

        // Simkl
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"${apikeyProperties["SIMKL_CLIENT_ID"] ?: System.getenv("SIMKL_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "SIMKL_CLIENT_SECRET", "\"${apikeyProperties["SIMKL_CLIENT_SECRET"] ?: System.getenv("SIMKL_CLIENT_SECRET") ?: ""}\"")
    }

    flavorDimensions += "store"

    productFlavors {
        create("fdroid") {
            dimension = "store"
            versionNameSuffix = "-fdroid"
        }
        create("google") {
            dimension = "store"
            isDefault = true
        }
    }

    buildTypes {
        create("alpha") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-alpha01-$gitCommitHash"
            manifestPlaceholders["icon_placeholder"] = "@mipmap/ic_launcher_alpha"
            manifestPlaceholders["icon_placeholder_round"] = "@mipmap/ic_launcher_alpha_round"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            isDefault = true
        }

        getByName("debug") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta01"
            manifestPlaceholders["icon_placeholder"] = "@mipmap/ic_launcher_beta"
            manifestPlaceholders["icon_placeholder_round"] = "@mipmap/ic_launcher_beta_round"
            isDebuggable = false
        }

        getByName("release") {
            manifestPlaceholders["icon_placeholder"] = "@mipmap/ic_launcher"
            manifestPlaceholders["icon_placeholder_round"] = "@mipmap/ic_launcher_round"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-Xmulti-platform"
        )
    }
}

dependencies {

    // Firebase
    add("googleImplementation", platform(libs.firebase.bom))
    add("googleImplementation", libs.bundles.firebase)

    // AndroidX
    implementation(libs.bundles.androidx)

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib)

    // Core libs
    implementation(libs.bundles.misc)

    // Glide
    implementation(libs.bundles.glide)
    ksp(libs.glide.ksp)

    implementation(libs.bundles.media3)
    implementation(libs.bundles.subtitles)
    implementation(libs.mediarouter)

    // UI
    implementation(libs.material)
    implementation(files("libs/AnimatedBottomBar-7fcb9af.aar"))
    implementation(libs.flexbox)
    implementation(libs.kenburns)
    implementation(libs.subsampling)
    implementation(libs.gesture)
    implementation(libs.ebook)
    implementation(libs.dialogs)
    implementation(libs.charts)

    // Markwon
    implementation(libs.bundles.markwon)

    // Groupie
    implementation(libs.bundles.groupie)

    // Rx
    implementation(libs.bundles.rx)

    // OkHttp
    implementation(libs.bundles.okhttp)

    // Ktor
    implementation(libs.bundles.ktor)

    // Others
    implementation(libs.okio)

    // XML Util
    implementation(libs.xmlutil.core)
    implementation(libs.xmlutil.serialization)

    // Archive
    implementation(libs.libarchive)

    // Logging
    implementation(libs.slf4j.android)
}