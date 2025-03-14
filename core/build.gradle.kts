import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("java")
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.mavenPublish)
}

repositories {
    mavenCentral()
    maven { url = uri("https://jogamp.org/deployment/maven") }
}

sourceSets{
    main{
        java{
            srcDirs("src")
        }
        resources{
            srcDirs("src")
            exclude("**/*.java")
        }
    }
    test{
        java{
            srcDirs("test")
        }
    }
}

dependencies {
    implementation(libs.jogl)
    implementation(libs.gluegen)

    testImplementation(libs.junit)
}

mavenPublishing{
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom{
        name.set("Processing Core")
        description.set("Processing Core")
        url.set("https://processing.org")
        licenses {
            license {
                name.set("LGPL")
                url.set("https://www.gnu.org/licenses/lgpl-2.1.html")
            }
        }
        developers {
            developer {
                id.set("steftervelde")
                name.set("Stef Tervelde")
            }
            developer {
                id.set("benfry")
                name.set("Ben Fry")
            }
        }
        scm{
            url.set("https://github.com/processing/processing4")
            connection.set("scm:git:git://github.com/processing/processing4.git")
            developerConnection.set("scm:git:ssh://git@github.com/processing/processing4.git")
        }
    }
}


tasks.test {
    useJUnit()
}
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.compileJava{
    options.encoding = "UTF-8"
}
