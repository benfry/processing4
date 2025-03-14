plugins {
    kotlin("jvm") version libs.versions.kotlin apply false
    alias(libs.plugins.kotlinMultiplatform) apply false

    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
}

// Set the build directory to not /build to prevent accidental deletion through the clean action
// Can be deleted after the migration to Gradle is complete
layout.buildDirectory = file(".build")