import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    id("androidx.baselineprofile")
    id("app.accrescent.tools.bundletool")
    id("app.cash.licensee")
    id("io.github.usefulness.licensee-for-android")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "dev.clombardo.dnsnet"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.clombardo.dnsnet"
        minSdk = 24
        targetSdk = 35
        versionCode = 23
        versionName = "1.0.9"
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
        debug {
            val debug = ".debug"
            applicationIdSuffix = debug
            versionNameSuffix = debug
            resValue("string", "app_name", "DNSNet Debug")
        }

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

        create("benchmark") {
            val benchmark = ".benchmark"
            applicationIdSuffix = benchmark
            versionNameSuffix = benchmark
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            resValue("string", "app_name", "DNSNet Benchmark")
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
    implementation("dnsjava:dnsjava:3.6.2")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
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
    implementation("com.google.accompanist:accompanist-permissions:$accompanistVersion")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    implementation("androidx.navigation:navigation-compose:2.8.4")

    val lifecycleVersion = "2.8.7"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")

    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("org.jetbrains.kotlinx:atomicfu:0.26.1")

    // Baseline profiles
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    "baselineProfile"(project(":baselineprofile"))

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.preference:preference-ktx:1.2.1")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("io.github.t895:materialswitch:0.1.3")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")

    implementation("dev.chrisbanes.haze:haze:1.1.1")
}

licensee {
    allow("MIT")
    allow("Apache-2.0")
    allow("BSD-3-Clause")
    allowUrl("https://opensource.org/licenses/mit")
    allowUrl("https://github.com/usefulness/licensee-for-android/blob/master/LICENSE")
}

licenseeForAndroid {
    enableKotlinCodeGeneration = true
}

detekt {
    toolVersion = "1.23.7"
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = true
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
    }
}
