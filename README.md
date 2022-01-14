# Processing 4.0

Processing 4 makes important updates to the code to prepare the platform for its future. Most significantly, this includes the move to Java 17 and support for new Java language features. The changes should be transparent to most users, but because of the massive shift behind the scenes, this is 4.0.

We've also moved to a new repository for this release so that we could cull a lot of the accumulated mess of the last 20 years, which makes `git clone` (and most other `git` operations) a lot faster.


## Roadmap

This software is currently in beta. We don't have a schedule for the final 4.0 release. This work is being done by a [tiny number of people](https://github.com/processing/processing4/graphs/contributors?from=2019-10-01&to=2022-03-01&type=c) who continue working on it, unpaid, because they care about it. The beta is the default download on the site because it's more usable than the 3.5.4 release.

* We're currently using JDK 17, which is the latest “Long Term Support” (LTS) release. (Before beta 3, we were using JDK 11. In 3.x we were using Java 8.)

* The current release runs well on Apple Silicon using Rosetta. We are currently unable to move to a fully native version for Apple Silicon because of other libraries that we rely upon (JavaFX, JOGL, etc). Once those are ready, we'll need to do additional work to add Apple Silicon as a target (the same way we support both 64-bit and 32-bit, or ARM instead of Intel.) If you'd like to help, or would like to check for updates, you'll find more [here](https://github.com/processing/processing4/issues/128).


## API and Internal Changes

As with all releases, we'll do [everything possible](https://twitter.com/ben_fry/status/1426282574683516928) to avoid breaking API. However, there will still be tweaks that have to be made. We'll try to keep them minor. Our goal is stability, and keeping everyone's code running.

The full list of changes can be seen in [the release notes for each version](https://github.com/processing/processing4/blob/master/build/shared/changes.md), this is only a list of things that may break existing projects (whether sketches, Libraries, Modes, etc.)


### Beta 3

* Now using JDK 17.0.1 and JavaFX 17.0.1.

* Major changes to `theme.txt` and theme handling in general. Now rendering toolbar icons from SVG images. More documentation later.

* Made `DrawListener` public in `PSurfaceJOGL`.


### Beta 2

* Added a workaround so that `color` can be part of package names, which gets some older code (i.e. toxiclibs) running again.


### Beta 1

* Now using JDK 11.0.12+7.


### Alpha 6

* `Editor.applyPreferences()` was `protected`, now it's `public`.

* Removed `Editor.repaintErrorBar()` and `Editor.showConsole()`. Does not appear to be in use anywhere, easy to add back if we hear otherwise.

* Renamed `TextAreaPainter.getCompositionTextpainter()` to `getCompositionTextPainter()`. This was an internal function and inconsistent with the rest of the function naming.


### Alpha 5

* ~~Known bug: code completion is currently broken. Any updates will be posted [here](https://github.com/processing/processing4/issues/177).~~ Fixed in alpha 6.

* Moved from the 11.0.2 LTS version of JavaFX to the in-progress version 16. This fixes a [garbled text](https://bugs.openjdk.java.net/browse/JDK-8234916) issue that was breaking Tools that used JavaFX.

* The minimum system version for macOS (for the PDE and exported applications) is now set to 10.14.6 (the last update of Mojave). 10.13 (High Sierra) is no longer supported by Apple as of September or December 2020 (depending on what you read), and for our sanity, we're dropping it as well.

* JavaFX has been moved out of core and into a separate library. After Java 8, it's no longer part of the JDK, so it requires additional files that were formerly included by default. The Processing version of that library comes in at 180 MB, which seems excessive to include with every project, regardless of whether it's used. (And that's not including the full Webkit implementation, which adds \~80 MB per platform.)


### Alpha 4

* `EditorState(List<Editor> editors)` changed to `EditorState.nextEditor(List<Editor> editors)`, reflecting its nature as closer to a factory method (that makes use of the Editor list) than a constructor that will also be storing information about the list of Editor objects in the created object.


### Alpha 3

* `export.embed_java.for` changed to `export.include_java` which also embeds a string for the platform for better localization support.


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


## Building the Code

[Instructions on how to build the code](https://github.com/processing/processing4/blob/master/build/README.md) are found in a README inside the `build` folder.
