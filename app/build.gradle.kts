plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    id("androidx.baselineprofile")
    id("app.accrescent.tools.bundletool")
}

android {
    namespace = "com.t895.dnsnet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.t895.dnsnet"
        minSdk = 24
        targetSdk = 35
        versionCode = 7
        versionName = "0.1.8"
    }

    val storeFilePath = System.getenv("STORE_FILE_PATH")
    if (storeFilePath != null) {
        val keyAlias = System.getenv("KEY_ALIAS")
        val keyPassword = System.getenv("KEY_PASSWORD")
        val storeFile = file(storeFilePath)
        val storePassword = System.getenv("STORE_PASSWORD")
        signingConfigs {
            create("release") {
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                this.storeFile = storeFile
                this.storePassword = storePassword
                enableV4Signing = true
            }
        }

        bundletool {
            signingConfig {
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                this.storeFile = storeFile
                this.storePassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            if (storeFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Proxy stuff
    implementation("org.pcap4j:pcap4j-core:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-static:1.8.2")
    implementation("dnsjava:dnsjava:3.6.1")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.01")
    implementation(composeBom)
    debugImplementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")

    val accompanistVersion = "0.36.0"
    implementation("com.google.accompanist:accompanist-drawablepainter:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-permissions:$accompanistVersion")

    implementation("androidx.navigation:navigation-compose:2.8.1")

    val lifecycleVersion = "2.8.6"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    implementation("org.jetbrains.kotlinx:atomicfu:0.25.0")

    // Baseline profiles
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    "baselineProfile"(project(":baselineprofile"))

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("io.github.t895:materialswitch:0.1.2")
}
