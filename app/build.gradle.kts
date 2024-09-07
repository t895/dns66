plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
}

android {
    namespace = "org.jak_linux.dns66"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.jak_linux.dns66"
        minSdk = 23
        targetSdk = 34
        versionCode = 29
        versionName = "0.6.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }

        create("benchmark") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Proxy stuff
    implementation("org.pcap4j:pcap4j-core:1.8.2")
    implementation("org.pcap4j:pcap4j-packetfactory-static:1.8.2")
    implementation("dnsjava:dnsjava:3.0.0")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    debugImplementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    val coilVersion = "2.7.0"
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-compose:$coilVersion")

    implementation("androidx.navigation:navigation-compose:2.8.0")

    val lifecycleVersion = "2.8.5"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.2")

    implementation("org.jetbrains.kotlinx:atomicfu:0.25.0")
}
