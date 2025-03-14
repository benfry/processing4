import com.vanniktech.maven.publish.SonatypeHost

plugins{
    id("java")
    id("com.vanniktech.maven.publish") version "0.30.0"
}

repositories{
    mavenCentral()
    google()
    maven { url = uri("https://jogamp.org/deployment/maven") }
}

sourceSets{
    main{
        java{
            srcDirs("../src/")
            include("processing/mode/java/lsp/**/*")
        }
    }
}

dependencies{
    implementation(project(":core"))
    implementation(project(":java:preprocessor"))

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.22.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.40.0")

    implementation("org.processing:core:${version}")
}

mavenPublishing{
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom{
        name.set("Processing Language Server")
        description.set("Processing Language Server")
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