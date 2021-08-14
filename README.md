# Processing 4.0

Processing 4 makes important updates to the code to prepare the platform for its future. Most significantly, this includes the move to JDK 11 and support for new Java language features. The changes should be transparent to most users, but because of the massive shift behind the scenes, this will be 4.0.


## Roadmap

We don't have a schedule for a final release. This work is being done by a [tiny number of people](https://github.com/processing/processing4/graphs/contributors?from=2019-10-01&to=2021-12-31&type=c) who continue working on it, unpaid, because they care about it.

* We're currently using JDK 11, which is a “Long Term Support” (LTS) release. Java 17 is the next LTS, and we'll switch to that when it arrives in September 2021.

* The current release runs well on Apple Silicon using Rosetta. We are currently unable to move to a fully native version for Apple Silicon because of other libraries that we rely upon (JavaFX, JOGL, etc). Once those are ready, we'll need to do additional work to add Apple Silicon as a target (the same way we support both 64-bit and 32-bit, or ARM instead of Intel.) If there are updates on this issue, you'll find them [here](https://github.com/processing/processing4/issues/128).


## API and Internal Changes

As with all releases, we'll do everything possible to avoid breaking API. However, there will still be tweaks that have to be made. We'll try to keep them minor. Our goal is stability, and keeping everyone's code running.

The full list of changes can be seen in the release notes for each version, this is a list of things that may break existing projects (sketches, libraries, etc.)


### Alpha 5

* ~~Known bug: code completion is currently broken. Any updates will be posted [here](https://github.com/processing/processing4/issues/177).~~ Fixed in alpha 6.

* Moved from the 11.0.2 LTS version of JavaFX to the in-progress version 16. This fixes a [garbled text](https://bugs.openjdk.java.net/browse/JDK-8234916) issue that was breaking Tools that used JavaFX.

* The minimum system version for macOS (for the PDE and exported applications) is now set to 10.14.6 (the last update of Mojave). 10.13 (High Sierra) is no longer supported by Apple as of September or December 2020 (depending on what you read), and for our sanity, we're dropping it as well.

* JavaFX has been moved out of core and into a separate library. After Java 8, it's no longer part of the JDK, so it requires additional files that were formerly included by default. The Processing version of that library comes in at 180 MB, which seems excessive to include with every project, regardless of whether it's used. (And that's not including the full Webkit implementation, which adds \~80 MB per platform.)


### Alpha 4

* `EditorState(List<Editor> editors)` changed to `EditorState.nextEditor(List<Editor> editors)`, reflecting its nature as closer to a factory method (that makes use of the Editor list) than a constructor that will also be storing information about the list of Editor objects in the created object.


### Alpha 2

* ~~The minimum system version for macOS (for the PDE and exported applications) is now set to 10.13.6 (the last update of High Sierra). Apple will likely be dropping support for High Sierra in late 2020, so we may make the minimum 10.14.x by the time 4.x ships.~~

* ~~See `changes.md` if you're using `surface.setResizable()` with this release on macOS and with P2D or P3D renderers.~~

* The `static` versions of `selectInput()`, `selectOutput()`, and `selectFolder()` in `PApplet` have been removed. These were not documented, hopefully they were not in use anyway.

* The `frame` object has been removed from `PApplet`. We've been warning folks to use `surface` since 2015, but maybe we can provide an [easy way](https://github.com/processing/processing4/issues/59) to update code from inside the PDE.

* `PImage.checkAlpha()` is now `public` instead of `protected`

* All AWT calls have been moved out of `PImage`, which may be a problem for anything that was relying on those internals
    * ~~For instance, `new PImage(java.awt.Image)` is no longer available. It was an undocumented method that was `public` only because it was required by subclasses.~~ As of alpha 4, this is back, because it wasn't deprecated in 3.x, and is likely to break too many things.

* Removed `MouseEvent.getClickCount()` and `MouseEvent.getAmount()`. These had been deprecated, not clear they were used anywhere.


### Alpha 1

* `Base.defaultFileMenu` is now `protected` instead of `static public`

* Processing 4 is 64-bit only. This is the overwhelming majority of users, and we don't have the necessary help to maintain and support 32-bit systems.


## Translation Updates

* `export.embed_java.for` changed to `export.include_java` which also embeds a string for the platform for better localization support.


## Building the Code

We'll eventually create a new wiki page with the build instructions, but for the time being, the instructions are:

1. Download and install JDK 11 from <https://adoptopenjdk.net/>
2. Make sure `ant` is installed for your platform.
3. Open a Terminal window/Command Prompt/whatever and type:

        cd /path/to/processing4/build
        ant run

### Java version complaints

You might have multiple versions of Java installed. Type `java -version` and if it says something other than 11, you'll need to set the `JAVA_HOME` environment variable.

On macOS, you can use:

    export JAVA_HOME="`/usr/libexec/java_home -v 11`"

If you need to go back to Java 8 (i.e. to build Processing 3), you can use:

    export JAVA_HOME="`/usr/libexec/java_home -v 1.8`"

On Windows and Linux, you can set `JAVA_HOME` to point at the installation the way you would any other environment variable.

On Linux (Ubuntu 20.04 in particular), the headless version of OpenJDK may be installed by default. If so, you may get errors when trying to run tests in core:

    java.lang.UnsatisfiedLinkError: Can't load library: /usr/lib/jvm/java-11-openjdk-amd64/lib/libawt_xawt.so

If so, use `sudo apt install openjdk-11-jdk` to install a full version. (You could also make use of the version downloaded by Processing itself to avoid duplication, but that's a little trickier to get everything bootstrapped and (sym)linked properly.)

And again, we'll have more complete instructions later once the dust settles.

### Eclipse

If you're using Eclipse, it'll complain about the lack of `jogl-all-src.jar`. Steps to create your own:

        git clone --recurse-submodules git://jogamp.org/srv/scm/jogl.git jogl
        cd jogl
        git checkout 0779f229b0e9538c640b18b9a4e095af1f5a35b3
        zip -r ../jogl-all-src.jar src

Then copy that `jogl-all-src.jar` file to sit next to the `jogl-all.jar` folder inside `/path/to/processing/core/library`.
