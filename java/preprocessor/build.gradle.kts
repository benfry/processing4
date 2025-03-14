import com.vanniktech.maven.publish.SonatypeHost

plugins{
    id("java")
    alias(libs.plugins.mavenPublish)
}

repositories{
    mavenCentral()
    google()
    maven { url = uri("https://jogamp.org/deployment/maven") }
}

sourceSets{
    main{
        java{
            srcDirs("src/main/java", "../src/", "../generated/")
            include("processing/mode/java/preproc/**/*", "processing/app/**/*")
        }
    }

}

dependencies{
    implementation(libs.antlr)
    implementation(libs.eclipseJDT)

    implementation(project(":core"))
}

mavenPublishing{
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom{
        name.set("Processing Pre-processor")
        description.set("Processing Pre-processor")
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
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.compileJava{
    dependsOn("ant-preproc")
}
ant.importBuild("../build.xml"){ antTaskName ->
    "ant-$antTaskName"
}