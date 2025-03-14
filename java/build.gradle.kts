plugins {
    id("java")
}

repositories{
    mavenCentral()
    google()
    maven { url = uri("https://jogamp.org/deployment/maven") }
}

sourceSets{
    main{
        java{
            srcDirs("src")
            exclude("processing/mode/java/preproc/**")
        }
    }
    test{
        java{
            srcDirs("test")
        }
    }
}

dependencies{
    implementation(project(":app"))
    implementation(project(":core"))
    implementation(project(":java:preprocessor"))

    implementation(libs.eclipseJDT)
    implementation(libs.eclipseJDTCompiler)
    implementation(libs.classpathExplorer)
    implementation(libs.netbeansSwing)
    implementation(libs.ant)
    implementation(libs.lsp4j)
    implementation(libs.jsoup)
    implementation(libs.antlr)

    testImplementation(libs.junit)
    testImplementation(libs.mockito)
}

tasks.compileJava{
    options.encoding = "UTF-8"
}

// LEGACY TASKS
// Most of these are shims to be compatible with the old build system
// They should be removed in the future, as we work towards making things more Gradle-native
tasks.register<Copy>("extraResources"){
    dependsOn(":java:copyCore")
    from(".")
    include("keywords.txt")
    include("theme/**/*")
    include("application/**/*")
    into( layout.buildDirectory.dir("resources-bundled/common/modes/java"))
}
tasks.register<Copy>("copyCore"){
    val coreProject = project(":core")
    dependsOn(coreProject.tasks.jar)
    from(coreProject.tasks.jar) {
        include("core*.jar")
    }
    rename("core.+\\.jar", "core.jar")
    into(coreProject.layout.projectDirectory.dir("library"))
}

val libraries = arrayOf("dxf","io","net","pdf","serial","svg")
libraries.forEach { library ->
    tasks.register<Copy>("library-$library-extraResources"){
        val build = project(":java:libraries:$library").tasks.named("build")
        build.configure {
            dependsOn(":java:copyCore")
        }
        dependsOn(build)
        from("libraries/$library")
        include("*.properties")
        include("library/**/*")
        include("examples/**/*")
        into( layout.buildDirectory.dir("resources-bundled/common/modes/java/libraries/$library"))
    }
    tasks.named("extraResources"){ dependsOn("library-$library-extraResources") }
}
tasks.jar { dependsOn("extraResources") }
tasks.processResources{ finalizedBy("extraResources") }
tasks.compileTestJava{ finalizedBy("extraResources") }

tasks.test {
    useJUnit()
}