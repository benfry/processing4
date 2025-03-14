plugins {
    id("java")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jogamp.org/deployment/maven") }
}

dependencies {
    implementation(project(":core"))
}
