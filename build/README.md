Processing Build Instructions
==============================
The following instructions describe how to build and release Processing across Windows, OS X, and Linux.

<br>
<br>


Local Environment Setup
------------------------------
Processing's ant-based build chain can create executables natively runnable for Linux, Mac, and Windows.

<br>

**Pre-Requisites**  
Although Processing will download and use its own copy of OpenJDK and OpenJFX, the build chain itself requires Java 11+ and Ant in addition to getting a copy of the Processing source code.

<br>

**Getting Java and Ant**  
You can choose to install these yourself or use the following guides below:

 - [Instructions for installing Java](https://adoptopenjdk.net/installation.html?variant=openjdk11&jvmVariant=hotspot#x64_mac-jdk)
 - [Instructions for installing Ant](http://ant.apache.org/manual/install.html)
 - Instructions for modifying your environment variables on [windows](https://www.architectryan.com/2018/03/17/add-to-the-path-on-windows-10/), [ mac](https://medium.com/@himanshuagarwal1395/setting-up-environment-variables-in-macos-sierra-f5978369b255) and [linux](https://www.cyberciti.biz/faq/set-environment-variable-linux/).

<br>

**Getting Processing**  
One will also need to clone the repository for Processing itself. Some users who are simply building Processing but not contributing to it may prefer a "shallow" clone which does not copy the full history of the repository:

```
git clone --depth 1 https://github.com/processing/processing4.git
```

Users that are developing for the project may require a full clone:

```
git clone https://github.com/processing/processing4.git
```

<br>
<br>

Building
------------------------------
One can either build for your own operating system (the "host" operating system) or, starting with Processing 4, one can "cross-build" from a host nix system (linux or mac) to any other "target os" build. Before continuing, please be aware that you are agreeing to the [license terms](https://github.com/sampottinger/processing/blob/master/LICENSE.md) and that the developers do not make any guarantee or warranty (implicit or express) for any purpose.

<br>

**Overview of steps**  
Before actually building, it is worth outlining the steps of the build process briefly:

 - The modules outside of `build` are built first. During this process, a number of automated unit tests will be executed with results displayed on the command line.
 - The `build` module itself will built and results will go into `build/{os}/work` where `{os}` is the operating system for which you are building like "windows". Note that both ARM and x64 builds go into the same OS directory.
 - During the build of `build`, the [OpenJDK](https://openjdk.java.net/) and [OpenJFX](https://openjfx.io/) will be downloaded with their binaries copied into the distribution. If building for the first time, these automated downloads from [AdoptOpenJDK](https://adoptopenjdk.net/) and [Gluon](https://gluonhq.com/) may take some time.

Note that one may need to "clean" via `ant linux-clean` or equivalent.

<br>

**Building for the "host" operating system**  
If building for your own system, navigate to where where you pulled the Processing source and execute `ant build` on the command line.

```
$ cd [path to processing repository]
$ cd build
$ ant build
```

The results will go into `build/{os}/work` where `{os}` matches the "host" operating system.

<br>

**Executing a "cross-build"**  
If building for another operating system (if you are on Linux and want to build for Windows), there are a number of "cross-build" targets available. Note that one can only execute "cross-builds" on a *nix system (Linux or Mac) but "cross-builds" are available for all targeted operating systems.

For example, here is how to execute a cross-build "targeting" Windows (the results will go into `build/windows/work`):

```
$ cd [path to processing repository]
$ cd build
$ ant cross-build-windows
```

The following cross-build targets are available:

 - cross-build-macosx
 - cross-build-linux-x64
 - cross-build-linux-aarch64
 - cross-build-windows

<br>
<br>

Automated Tests
------------------------------
Unit tests are available by running the test target:

```
$ cd [path to processing repository]
$ cd build
$ ant test
```

These tests are not executed by default when running the `build` target but are recommended for developers contributing to the project.

<br>
<br>

Running
------------------------------
The build can be run directly or through Ant.

<br>

**Executing via Ant**  
If built for the host operating system, one can use the `ant run` target as shown below:

```
$ cd [path to processing repository]
$ cd build
$ ant run
```

If not yet built, this will cause Processing to be built prior to running.

<br>

**Using Executable Directly**  
Regardless of if cross-building, there are executables generated that can be run directly on the target operating system:

 - **Mac**: the `.app` file can be executed via a double click at `build/macosx/work/Processing.app` or `$ open build/macosx/work/Processing.app`.
 - **Linux**:  The resulting executable ends up at `build/linux/work/processing`.
 - **Windows**: The resulting executable ends up at `build/windows/work/processing.exe`.

<br>
<br>

Distributing
------------------------------
A number of targets are provided for distribution of executables. If the executable is not yet built, it will be created prior to running the dist target.

<br>

**Available targets:**  

 - macosx-dist
 - windows-dist
 - linux-dist

One can also use `ant dist` to distribute for the host OS.

<br>

**Examples**  

For the host system, one can distribute like so:

```
$ cd [path to processing repository]
$ cd build
$ ant dist
```

From a nix system, one can cross-build and distribute for linux like so:

```
$ cd [path to processing repository]
$ cd build
$ ant linux-dist
```

Regardless, the distributable ends up in `build/{os}/work` where `{os}` is the target OS.

<br>

**Code Signing**  
At present, only Mac builds require code signing to avoid an ["App Gateway"](https://support.apple.com/en-us/HT202491) issue. This is not executed by default by `ant dist` or `ant macosx-dist`. One can sign the resulting `.app` file though via:

```
$ /usr/bin/codesign --force --sign "Developer ID Application: Certificate Common Name" Processing.app/Contents/PlugIns/jdk-...
$ /usr/bin/codesign --force --sign "Developer ID Application: Certificate Common Name" Processing.app
```

Note that one will need to complete the `jdk-...` string to be something like `jdk-11.0.1+13` depending on the build. Anyway, this will require an [Apple Developer ID](https://developer.apple.com/developer-id/).

This is not strictly required especially if you are using your own app build.
