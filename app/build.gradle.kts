import org.gradle.kotlin.dsl.support.zipTo
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.compose.internal.de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.fir.scopes.impl.overrides
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// TODO: Update to 2.10.20 and add hot-reloading: https://github.com/JetBrains/compose-hot-reload

plugins{
    id("java")
    kotlin("jvm") version libs.versions.kotlin

    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.serialization)
    alias(libs.plugins.download)
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
        }
        kotlin{
            srcDirs("src")
        }
        resources{
            srcDirs("resources", listOf("languages", "fonts", "theme").map { "../build/shared/lib/$it" })
        }
    }
    test{
        kotlin{
            srcDirs("test")
        }
    }
}

compose.desktop {
    application {
        mainClass = "processing.app.ui.Start"

        jvmArgs(*listOf(
            Pair("processing.version", rootProject.version),
            Pair("processing.revision", findProperty("revision") ?: Int.MAX_VALUE),
            Pair("processing.contributions.source", "https://download.processing.org/contribs.txt"),
            Pair("processing.download.page", "https://processing.org/download/"),
            Pair("processing.download.latest", "https://processing.org/download/latest.txt"),
            Pair("processing.tutorials", "https://processing.org/tutorials/"),
        ).map { "-D${it.first}=${it.second}" }.toTypedArray())

        nativeDistributions{
            modules("jdk.jdi", "java.compiler", "jdk.accessibility", "java.management.rmi")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Processing"

            macOS{
                bundleID = "${rootProject.group}.app"
                iconFile = rootProject.file("build/macos/processing.icns")
                infoPlist{
                    extraKeysRawXml = file("macos/info.plist").readText()
                }
                entitlementsFile.set(file("macos/entitlements.plist"))
                runtimeEntitlementsFile.set(file("macos/entitlements.plist"))
                appStore = true
            }
            windows{
                iconFile = rootProject.file("build/windows/processing.ico")
                menuGroup = "Processing"
                upgradeUuid = "89d8d7fe-5602-4b12-ba10-0fe78efbd602"
            }
            linux {
                appCategory = "Programming"
                menuGroup = "Development;Programming;"
                iconFile = rootProject.file("build/linux/processing.png")
                // Fix fonts on some Linux distributions
                jvmArgs("-Dawt.useSystemAAFontSettings=on")

                fileAssociation("pde", "Processing Source Code", "application/x-processing")
                fileAssociation("pyde", "Processing Python Source Code", "application/x-processing")
                fileAssociation("pdez", "Processing Sketch Bundle", "application/x-processing")
                fileAssociation("pdex", "Processing Contribution Bundle", "application/x-processing")
            }
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.flatlaf)

    implementation(libs.jna)
    implementation(libs.jnaplatform)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)

    implementation(compose.desktop.currentOs)

    implementation(libs.compottie)
    implementation(libs.kaml)
    implementation(libs.markdown)
    implementation(libs.markdownJVM)

    testImplementation(kotlin("test"))
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.junitJupiterParams)
}

tasks.test {
    useJUnitPlatform()
    workingDir = file("build/test")
    workingDir.mkdirs()
}

tasks.compileJava{
    options.encoding = "UTF-8"
}

val version = if(project.version == "unspecified") "1.0.0" else project.version

tasks.register<Exec>("installCreateDmg") {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    commandLine("arch", "-arm64", "brew", "install", "--quiet", "create-dmg")
}
tasks.register<Exec>("packageCustomDmg"){
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    group = "compose desktop"

    val distributable = tasks.named<AbstractJPackageTask>("createDistributable").get()
    dependsOn(distributable, "installCreateDmg")

    val packageName = distributable.packageName.get()
    val dir = distributable.destinationDir.get()
    val dmg = dir.file("../dmg/$packageName-$version.dmg").asFile
    val app = dir.file("$packageName.app").asFile

    dmg.parentFile.deleteRecursively()
    dmg.parentFile.mkdirs()

    val extra = mutableListOf<String>()
    val isSigned = compose.desktop.application.nativeDistributions.macOS.signing.sign.get()

    if(!isSigned) {
        val content = """
        run 'xattr -d com.apple.quarantine Processing-${version}.dmg' to remove the quarantine flag
        """.trimIndent()
        val instructions = dmg.parentFile.resolve("INSTRUCTIONS.txt")
        instructions.writeText(content)
        extra.add("--add-file")
        extra.add("INSTRUCTIONS.txt")
        extra.add(instructions.path)
        extra.add("200")
        extra.add("25")
    }

    commandLine("brew", "install", "--quiet", "create-dmg")

    commandLine("create-dmg",
        "--volname", packageName,
        "--volicon", file("macos/volume.icns"),
        "--background", file("macos/background.png"),
        "--icon", "$packageName.app", "190", "185",
        "--window-pos", "200", "200",
        "--window-size", "658", "422",
        "--app-drop-link", "466", "185",
        "--hide-extension", "$packageName.app",
        *extra.toTypedArray(),
        dmg,
        app
    )
}

tasks.register<Exec>("packageCustomMsi"){
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    dependsOn("createDistributable")
    workingDir = file("windows")
    group = "compose desktop"

    val version = if(version == "unspecified") "1.0.0" else version

    commandLine(
        "dotnet",
        "build",
        "/p:Platform=x64",
        "/p:Version=$version",
        "/p:DefineConstants=\"Version=$version;\""
    )
}

val snapname = findProperty("snapname") ?: rootProject.name
val snaparch = when (System.getProperty("os.arch")) {
    "amd64", "x86_64" -> "amd64"
    "aarch64" -> "arm64"
    else -> System.getProperty("os.arch")
}
tasks.register("generateSnapConfiguration"){
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    val distributable = tasks.named<AbstractJPackageTask>("createDistributable").get()
    dependsOn(distributable)

    val dir = distributable.destinationDir.get()
    val content = """
    name: $snapname
    version: $version
    base: core22
    summary: A creative coding editor
    description: |
      Processing is a flexible software sketchbook and a programming language designed for learning how to code.
    confinement: strict
    
    apps:
      processing:
        command: opt/processing/bin/Processing
        desktop: opt/processing/lib/processing-Processing.desktop
        environment:
            LD_LIBRARY_PATH: ${'$'}SNAP/opt/processing/lib/runtime/lib:${'$'}LD_LIBRARY_PATH
            LIBGL_DRIVERS_PATH: ${'$'}SNAP/usr/lib/${'$'}SNAPCRAFT_ARCH_TRIPLET/dri
        plugs:
          - desktop
          - desktop-legacy
          - wayland
          - x11
          - network
          - opengl
    
    parts:
      processing:
        plugin: dump
        source: deb/processing_$version-1_$snaparch.deb
        source-type: deb
        stage-packages:
          - openjdk-17-jre
        override-prime: |
          snapcraftctl prime
          chmod -R +x opt/processing/lib/app/resources/jdk-*
          rm -vf usr/lib/jvm/java-17-openjdk-*/lib/security/cacerts
    """.trimIndent()
    dir.file("../snapcraft.yaml").asFile.writeText(content)
}

tasks.register<Exec>("packageSnap"){
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    dependsOn("packageDeb", "generateSnapConfiguration")
    group = "compose desktop"

    val distributable = tasks.named<AbstractJPackageTask>("createDistributable").get()
    workingDir = distributable.destinationDir.dir("../").get().asFile
    commandLine("snapcraft")
}
tasks.register<Zip>("zipDistributable"){
    dependsOn("createDistributable", "setExecutablePermissions")
    group = "compose desktop"

    val distributable = tasks.named<AbstractJPackageTask>("createDistributable").get()
    val dir = distributable.destinationDir.get()
    val packageName = distributable.packageName.get()

    from(dir){ eachFile{ permissions{ unix("755") } } }
    archiveBaseName.set(packageName)
    destinationDirectory.set(dir.file("../").asFile)
}

afterEvaluate{
    tasks.named("packageDmg").configure{
        dependsOn("packageCustomDmg")
        group = "compose desktop"
        actions = emptyList()
    }

    tasks.named("packageMsi").configure{
        dependsOn("packageCustomMsi")
        group = "compose desktop"
        actions = emptyList()
    }
    tasks.named("packageDistributionForCurrentOS").configure {
        if(org.gradle.internal.os.OperatingSystem.current().isMacOsX
            && compose.desktop.application.nativeDistributions.macOS.notarization.appleID.isPresent
        ){
            dependsOn("notarizeDmg")
        }
        dependsOn("packageSnap", "zipDistributable")
    }
}


// LEGACY TASKS
// Most of these are shims to be compatible with the old build system
// They should be removed in the future, as we work towards making things more Gradle-native
val composeResources = { subPath: String -> layout.buildDirectory.dir("resources-bundled/common/$subPath") }
compose.desktop.application.nativeDistributions.appResourcesRootDir.set(composeResources("../"))

tasks.register<Copy>("includeCore"){
    val core = project(":core")
    dependsOn(core.tasks.jar)
    from(core.layout.buildDirectory.dir("libs"))
    from(core.configurations.runtimeClasspath)
    into(composeResources("core/library"))
}
tasks.register<Copy>("includeJavaMode") {
    val java = project(":java")
    dependsOn(java.tasks.jar)
    from(java.layout.buildDirectory.dir("libs"))
    from(java.configurations.runtimeClasspath)
    into(composeResources("modes/java/mode"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.register<Download>("includeJdk") {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val arch = when (System.getProperty("os.arch")) {
        "amd64", "x86_64" -> "x64"
        else -> System.getProperty("os.arch")
    }
    val platform = when {
        os.isWindows -> "windows"
        os.isMacOsX -> "mac"
        else -> "linux"
    }

    val javaVersion = System.getProperty("java.version").split(".")[0]
    val imageType = "jdk"

    src("https://api.adoptium.net/v3/binary/latest/" +
            "$javaVersion/ga/" +
            "$platform/" +
            "$arch/" +
            "$imageType/" +
            "hotspot/normal/eclipse?project=jdk")

    val extension = if (os.isWindows) "zip" else "tar.gz"
    val jdk = layout.buildDirectory.file("tmp/jdk-$platform-$arch.$extension")
    dest(jdk)
    overwrite(false)
    doLast {
        copy {
            val archive = if (os.isWindows) { zipTree(jdk) } else { tarTree(jdk) }
            from(archive){ eachFile{ permissions{ unix("755") } } }
            into(composeResources(""))
        }
    }
}
tasks.register<Copy>("includeSharedAssets"){
    from("../build/shared/")
    into(composeResources(""))
}
tasks.register<Download>("includeProcessingExamples") {
    val examples = layout.buildDirectory.file("tmp/processing-examples.zip")
    src("https://github.com/processing/processing-examples/archive/refs/heads/main.zip")
    dest(examples)
    overwrite(false)
    doLast{
        copy{
            from(zipTree(examples)){ // remove top level directory
                exclude("processing-examples-main/README.md")
                exclude("processing-examples-main/.github/**")
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
            into(composeResources("/modes/java/examples"))
        }
    }
}
tasks.register<Download>("includeProcessingWebsiteExamples") {
    val examples = layout.buildDirectory.file("tmp/processing-website.zip")
    src("https://github.com/processing/processing-website/archive/refs/heads/main.zip")
    dest(examples)
    overwrite(false)
    doLast{
        copy{
            from(zipTree(examples)){
                include("processing-website-main/content/examples/**")
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(3).toTypedArray()) }
                includeEmptyDirs = false
                exclude { it.name.contains(".es.") || it.name == "liveSketch.js" }
            }
            into(composeResources("modes/java/examples"))
        }
    }
}
tasks.register<Copy>("includeJavaModeResources") {
    val java = project(":java")
    dependsOn(java.tasks.named("extraResources"))
    from(java.layout.buildDirectory.dir("resources-bundled"))
    into(composeResources("../"))
}
tasks.register<Copy>("renameWindres") {
    dependsOn("includeSharedAssets","includeJavaModeResources")
    val dir = composeResources("modes/java/application/launch4j/bin/")
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val platform = when {
        os.isWindows -> "windows"
        os.isMacOsX -> "macos"
        else -> "linux"
    }
    from(dir) {
        include("*-$platform*")
        rename("(.*)-$platform(.*)", "$1$2")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    into(dir)
}
tasks.register("signResources"){
    onlyIf {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX
            &&
        compose.desktop.application.nativeDistributions.macOS.signing.sign.get()
    }
    group = "compose desktop"
    dependsOn(
        "includeCore",
        "includeJavaMode",
        "includeJdk",
        "includeSharedAssets",
        "includeProcessingExamples",
        "includeProcessingWebsiteExamples",
        "includeJavaModeResources",
        "renameWindres"
    )
    finalizedBy("prepareAppResources")

    val resourcesPath = composeResources("")



    // find jars in the resources directory
    val jars = mutableListOf<File>()
    doFirst{
        fileTree(resourcesPath)
            .matching { include("**/Info.plist") }
            .singleOrNull()
            ?.let { file ->
                copy {
                    from(file)
                    into(resourcesPath)
                }
            }
        fileTree(resourcesPath) {
            include("**/*.jar")
            exclude("**/*.jar.tmp/**")
        }.forEach { file ->
            val tempDir = file.parentFile.resolve("${file.name}.tmp")
            copy {
                from(zipTree(file))
                into(tempDir)
            }
            file.delete()
            jars.add(tempDir)
        }
        fileTree(resourcesPath){
            include("**/bin/**")
            include("**/*.jnilib")
            include("**/*.dylib")
            include("**/*aarch64*")
            include("**/*x86_64*")
            include("**/*ffmpeg*")
            include("**/ffmpeg*/**")
            exclude("jdk-*/**")
            exclude("*.jar")
            exclude("*.so")
            exclude("*.dll")
        }.forEach{ file ->
            exec {
                commandLine("codesign", "--timestamp", "--force", "--deep","--options=runtime", "--sign", "Developer ID Application", file)
            }
        }
        jars.forEach { file ->
            FileOutputStream(File(file.parentFile, file.nameWithoutExtension)).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    file.walkTopDown().forEach { fileEntry ->
                        if (fileEntry.isFile) {
                            // Calculate the relative path for the zip entry
                            val zipEntryPath = fileEntry.relativeTo(file).path
                            val entry = ZipEntry(zipEntryPath)
                            zos.putNextEntry(entry)

                            // Copy file contents to the zip
                            fileEntry.inputStream().use { input ->
                                input.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            file.deleteRecursively()
        }
        file(composeResources("Info.plist")).delete()
    }


}
afterEvaluate {
    tasks.named("prepareAppResources").configure {
        dependsOn(
            "includeCore",
            "includeJavaMode",
            "includeJdk",
            "includeSharedAssets",
            "includeProcessingExamples",
            "includeProcessingWebsiteExamples",
            "includeJavaModeResources",
            "renameWindres"
        )
    }
    tasks.register("setExecutablePermissions") {
        description = "Sets executable permissions on binaries in Processing.app resources"
        group = "compose desktop"

        doLast {
            val resourcesPath = layout.buildDirectory.dir("compose/binaries")
            fileTree(resourcesPath) {
                include("**/resources/**/bin/**")
                include("**/resources/**/*.sh")
                include("**/resources/**/*.dylib")
                include("**/resources/**/*.so")
                include("**/resources/**/*.exe")
            }.forEach { file ->
                if (file.isFile) {
                    file.setExecutable(true, false)
                }
            }
        }
    }
    tasks.named("createDistributable").configure {
        dependsOn("signResources")
        finalizedBy("setExecutablePermissions")
    }
}