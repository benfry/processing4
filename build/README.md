# How to Build Processing

The short version:

1. Download and install JDK 17 from <https://adoptium.net/>
2. Make sure `ant` is [installed](https://ant.apache.org/) for your platform.
3. Open a Terminal window/Command Prompt/whatever and type:

        cd /path/to/processing4/build
        ant run


### Updating an older 4.x checkout

If you checked out this repository before, but now it's behaving strangely, try the following:

    git pull
    git checkout main
    ant clean
    ant clean-libs
    ant run

Major changes happened during the 4.x series, including a reorganization of the *core* library, changes to how `.jar` files for libraries were downloaded, and the `master` branch was renamed to `main`.

The `ant clean-libs` task (added in 4.0 beta 9) clears out downloaded support files (JOGL, Ant, etc) and makes sure they're the latest versions.


### Java version complaints

You might have multiple versions of Java installed. Type `java -version` and if it says something other than 17, you'll need to set the `JAVA_HOME` environment variable.

On macOS, you can use:

    export JAVA_HOME="`/usr/libexec/java_home -v 17`"

If you need to go back to Java 8 (i.e. to build Processing 3), you can use:

    export JAVA_HOME="`/usr/libexec/java_home -v 1.8`"

On Windows and Linux, you can set `JAVA_HOME` to point at the installation the way you would any other environment variable.

On Linux (Ubuntu 20.04 in particular), the headless version of OpenJDK may be installed by default. If so, you may get errors when trying to run tests in core:

    java.lang.UnsatisfiedLinkError: Can't load library: /usr/lib/jvm/java-17-openjdk-amd64/lib/libawt_xawt.so

If so, use `sudo apt install openjdk-17-jdk` to install a full version. You could also make use of the JDK that's downloaded by Processing itself to avoid duplication, but that's a little trickier to get everything bootstrapped and (sym)linked properly.


# The Long Version

A more detailed explanation of how to build and release Processing across Windows, macOS, and Linux, and a bit more about how the build system works.


## Local Environment Setup

Processing's ant-based build chain can create executables natively runnable for Linux, Mac, and Windows.

### Pre-Requisites
Although Processing will download and use its own copy of OpenJDK and OpenJFX, the build chain itself requires Java 17 and Ant in addition to getting a copy of the Processing source code.

### Getting Java and Ant
You can choose to install these yourself or use the following guides below:

* [Instructions for installing Java](https://adoptopenjdk.net/installation.html?variant=openjdk17&jvmVariant=hotspot#x64_mac-jdk)
* [Instructions for installing Ant](http://ant.apache.org/manual/install.html)
* Instructions for modifying your environment variables on [Windows](https://www.architectryan.com/2018/03/17/add-to-the-path-on-windows-10/), [macOS](https://medium.com/@himanshuagarwal1395/setting-up-environment-variables-in-macos-sierra-f5978369b255) and [Linux](https://www.cyberciti.biz/faq/set-environment-variable-linux/).

### Download the Processing source code
One will also need to clone the repository for Processing itself.
```
git clone https://github.com/processing/processing4.git
```


## Building

One can either build for your own operating system (the "host" operating system) or, starting with Processing 4, one can "cross-build" from a host nix system (linux or mac) to any other "target os" build. Before continuing, please be aware that you are agreeing to the [license terms](https://github.com/sampottinger/processing/blob/master/LICENSE.md) and that the developers do not make any guarantee or warranty (implicit or express) for any purpose.


### Overview of steps
Before actually building, it is worth outlining the steps of the build process briefly:

 - The modules outside of `build` are built first. During this process, a number of automated unit tests will be executed with results displayed on the command line.
 - The `build` module itself will built and results will go into `build/{os}/work` where `{os}` is the operating system for which you are building like "windows". Note that both ARM and x64 builds go into the same OS directory.
 - During the build of `build`, the [OpenJDK](https://openjdk.java.net/) and [OpenJFX](https://openjfx.io/) will be downloaded with their binaries copied into the distribution. If building for the first time, these automated downloads from [AdoptOpenJDK](https://adoptopenjdk.net/) and [Gluon](https://gluonhq.com/) may take some time.

Note that one may need to “clean” via `ant clean`, which removes previous files (i.e. from the `work` folder).


### Building for the "host" operating system
If building for your own system, navigate to where where you pulled the Processing source and execute `ant build` on the command line.

```
cd /path/to/processing4
cd build
ant build
```

The results will go into `build/{os}/work` where `{os}` matches the "host" operating system.


## Running

The build can be run directly or through Ant.

### Executing via Ant
If built for the host operating system, one can use the `ant run` target as shown below:

```
cd /path/to/processing4
cd build
ant run
```

If not yet built, this will cause Processing to be built prior to running.

#### Using Executable Directly
Except when doing a cross-build (below), the build process creates executables that can be run directly on the target operating system:

 - **macOS**: the `.app` file can be executed via a double click at `build/macosx/work/Processing.app` or `open build/macosx/work/Processing.app`.
 - **Linux**:  The resulting executable ends up at `build/linux/work/processing`.
 - **Windows**: The resulting executable ends up at `build/windows/work/processing.exe`.


## Advanced: cross platform builds, tests, and distribution

### Executing a "cross-build"
If building for another operating system (if you are on Linux and want to build for Windows), there are a number of "cross-build" targets available. Note that one can only execute "cross-builds" on a \*nix system (Linux or Mac) but "cross-builds" are available for all targeted operating systems.

For example, here is how to execute a cross-build "targeting" Windows (the results will go into `build/windows/work`):

```
cd /path/to/processing4
cd build
ant cross-build-windows
```

The following cross-build targets are available:

 - cross-build-macosx
 - cross-build-linux-x64
 - cross-build-linux-aarch64
 - cross-build-windows


### Automated Tests

Unit tests are available by running the test target:

```
cd /path/to/processing4
cd build
ant test
```

These tests are not executed by default when running the `build` target but are recommended for developers contributing to the project.


### Distributing

A number of targets are provided for distribution of executables. If the executable is not yet built, it will be created prior to running the dist target.

**Available targets:**

 - macosx-dist
 - windows-dist
 - linux-dist

One can also use `ant dist` to distribute for the host OS.


### Examples

For the host system, one can distribute like so:

```
cd /path/to/processing4
cd build
ant dist
```

From a \*nix system, one can cross-build and distribute for linux like so:

```
cd /path/to/processing4
cd build
ant linux-dist
```

Regardless, the distributable ends up in `build/{os}/work` where `{os}` is the target OS.


### Code Signing

Mac builds require code signing, due to [Apple requirements](https://support.apple.com/en-us/HT202491) issue. This is not executed by default by `ant dist` or `ant macosx-dist`. One can sign the resulting `.app` file though via:

```
/usr/bin/codesign --force --sign "Developer ID Application: Certificate Common Name" Processing.app/Contents/PlugIns/jdk-...
/usr/bin/codesign --force --sign "Developer ID Application: Certificate Common Name" Processing.app
```

Note that one will need to complete the `jdk-...` string to be something like `jdk-17.0.2+8` depending on the build. Anyway, this will require an [Apple Developer ID](https://developer.apple.com/developer-id/).

This is not strictly required especially if you are using your own app build.

Eventually we'll want to sign [Windows releases](https://github.com/processing/processing4/issues/25), and [exported applications](https://github.com/processing/processing4/issues/173). If you have experience with this, please help!


## Using an IDE for development (Eclipse or IntelliJ)

### Eclipse

If you're using Eclipse, it'll complain about the lack of `jogl-all-src.jar`. Steps to create your own:

    git clone --recurse-submodules git://jogamp.org/srv/scm/jogl.git jogl
    cd jogl
    git checkout 0779f229b0e9538c640b18b9a4e095af1f5a35b3
    zip -r ../jogl-all-src.jar src

Then copy that `jogl-all-src.jar` file to sit next to the `jogl-all.jar` folder inside `/path/to/processing/core/library`.


### IntelliJ

Using Eclipse isn't supported, and I've switched to IntelliJ. However, IntelliJ is baffling enough that I don't have good instructions yet on how to develop inside there. If you and IntelliJ have a better relationship than I do, [please help!](https://github.com/processing/processing4/issues/275)
