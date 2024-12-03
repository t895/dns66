// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    val agpVersion = "8.7.3"
    id("com.android.application") version agpVersion apply false
    id("com.android.library") version agpVersion apply false
    id("com.android.test") version agpVersion apply false

    val kotlinVersion = "2.1.0"
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion apply false

    id("org.jetbrains.kotlinx.atomicfu") version "0.26.1" apply false

    id("androidx.baselineprofile") version "1.3.3" apply false

    id("app.accrescent.tools.bundletool") version "0.2.4" apply false

    id("app.cash.licensee") version "1.12.0" apply false
    id("io.github.usefulness.licensee-for-android") version "0.2.2" apply false
}
