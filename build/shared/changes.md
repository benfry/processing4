# Processing 4.0 beta 1

*Revision 1276 – 9 August 2021*

Celebrating the 20th anniversary of the very first Processing release (revision 0001) which was first posted to be shared as part of a workshop in Japan at Musashino Art University (https://dbn.media.mit.edu/workshops/musabi/). This is the 277th release.

The primary goal for Processing 4 is to keep everyone's code running, even as operating systems, hardware, and hairlines continue to change.

This beta release will become the default download on the site, which means that it is more stable, usable, and better than the 3.5.4 release from January 2020. It's hard to remember what things were like in January 2020. But if you must use pre-pandemic software, we'll be keeping the release online.


## Highlights for Processing 4

Here are some highlights for Processing 4, since this will be the first time a lot of people are taking 4.0 for a spin:

* There's a new Movie Maker that creates MP4s and Animated GIFs!

* You can bundle sketches as double-clickable `.pdez` files and Libraries/Modes/Tools as `.pdex` files for easy sharing and installation.

* We're using Java 11, which should be faster, more up to date, and a lot more compatible with newer machines.

* There have been many updates for the Video and Sound libraries as we chase Apple's ever-changing guidelines for what constitutes “safe” software.

* For users more experienced with Java, you can now use newer language features! Sam Pottinger rewired the preprocessor (and brought us Java 11 support as well).


## Changes since alpha 6

A lot has changed! Trying to get everything in under the wire as we work to finalize 4.0.


### Features and Updates

* We've started work on refreshing the design. This round has a new set of colors, icons, a splash screen, and more. If you like them, great! If not, please hold your complaints. The internet doesn't need more negativity! We still have a lot of work to do, and we think you'll be happy with the final result.

* It's now possible to bundle sketches into a single file to be loaded inside the PDE. This is done with `.pdez` files, which is a sketch folder saved as a `.zip` but with the extension changed to `.pdez`. This means you can post a sketch on the web as a `.pdez`, and someone with Processing can click the link and have it load directly in the PDE. [#73](https://github.com/processing/processing/issues/73), [#3987](https://github.com/processing/processing/issues/3987)

* Similar to `.pdez` files, you can install Libraries, Modes, Tools, and Example sets (things that would normally be installed with the Contribution Manager) by renaming their `.zip` file to `.pdez`. Double-clicking a `.pdez` file will open it with Processing, and ask the user whether they'd like to install it.

* It's now possible to do code completion and refactoring even when `.java` tabs are included in a sketch. Thanks Sam! [#157](https://github.com/processing/processing4/issues/157), [#230](https://github.com/processing/processing4/pull/230)

* Moved the preferences to `~/.config/processing` on Linux instead of `~/.processing`. This means your settings will be reset, but for most, that will be more of a plus with 4.x. [#203]( https://github.com/processing/processing4/issues/203)

* Initial update of the splash screen and icons. These still need some work: the icons are too muddy at smaller sizes, for instance.

* The Welcome screen has been reset, so folks will see it again. We haven't made a decision on the Welcome screen for the final 4.0, but plan for it to be more useful than what's there now.

* “Show Sketch Folder”, “Add File”, and “Export to Application” now require Untitled or Read-Only sketches to be saved first, which avoids a weird situation where the user is dealing with files in hidden temporary folders. [#2459](https://github.com/processing/processing/issues/2459)

* The reference for Java Mode is now served up from a web server that lives inside the PDE. This means that the download has just a single file for the reference, instead of thousands of tiny `.html` files. Far fewer things to copy makes the installation process much smoother.


### Bug Fixes

* Really chatty console messages for longtime users who had older (like 2.x) settings files still on their machine.

* IDE cursor position on Windows was going weird if display scaling used. [#226](https://github.com/processing/processing4/issues/226)

* Only call `errorTable.updateTheme()` if the Mode is using an Error Table (Python was not).

* `PShape.scale()` not working with `PShape.resetMatrix()` when P2D renderer is used. [#217](https://github.com/processing/processing4/issues/217), [#225](https://github.com/processing/processing4/pull/225)


### Should Be Fixed

Several things that should no longer be a problem based on updates we've done in 4.x, but not necessarily verified 100%.

* Undo feature may have undesired results (Fixed in 4.0a4) [#4775](https://github.com/processing/processing/issues/4775)

* HiDPI support for GNOME desktop. [#6059](https://github.com/processing/processing/issues/6059)

* AppKit errors from P2D/P3D. [#5880](https://github.com/processing/processing/issues/5880)

* Export Application broken in Processing 3.5.4 when using P2D or P3D renderers. [#5983](https://github.com/processing/processing/issues/5983)

* Cannot run `rotateZ()` within the `PShape` class. [#5770](https://github.com/processing/processing/issues/5770)

* `Profile GL4bc is not available on X11GraphicsDevice` error fixed with new JOGL release. [#6160](https://github.com/processing/processing/issues/6160), [#6154](https://github.com/processing/processing/issues/6154).

* `Profile GL3bc is not available on X11GraphicsDevice` should also be fixed. [#5476](https://github.com/processing/processing/issues/5476)


### Internal Changes

Things you're unlikely to notice, but in case something seems off, it's better to make note of them in case you see a related problem pop up.

* Now using JDK 11.0.12+7.

* Cleaning up `suggestions.txt` handling and related code.

* Removed code for pulling fonts from `JAVA_HOME/lib/fonts`, because it no longer exists in Java 11.

* Update `EditorFooter.updateMode()` to `EditorFooter.updateTheme()` and add it to the code called by `Editor.updateTheme()`

* Removed the JRE Downloader, because it's no longer necessary. [#155](https://github.com/processing/processing4/issues/155)

* Changed `Messages.loge()` to `Messages.err()`.


### Known Issues

* Support for `.pdez` and `.pdex` is not yet complete on Linux. Please help! [#239](https://github.com/processing/processing4/issues/239)

* Have I mentioned that the design of the icons, theme, layout, etc aren't finished?



# Processing 4.0 alpha 6

*Revision 1275 – 10 July 2021*

Code completion is back!

Added a new Movie Maker that creates MP4s and Animated GIFs!

And a new color scheme sure to bring out the cranks!


### New Features

* Movie Maker has been rewritten and much improved! It can now directly create high quality MPEG-4 videos and Animated GIFs. Due to Apple [removing support for most video codecs](https://support.apple.com/en-us/HT202884) after macOS Mojave, we could no longer export QuickTime videos. The new Tool uses [FFmpeg](https://ffmpeg.org/) behind the scenes. The Tool also supports [Apple ProRes 4444](https://support.apple.com/en-us/HT202410), which is very high quality, and incidentally, the format that Apple's “QTMovieModernizer” formerly used to re-encode “legacy” video formats. [#6110](https://github.com/processing/processing/issues/6110)


### Design and Themes

* We've started work on refreshing the design. This round has a new set of colors. If you like them, great! If not, please hold your complaints. The internet doesn't need more negativity! We know some people won't like it, and we're [still working on it](https://twitter.com/ben_fry/status/1409968426093735941). We think you'll be happy with the final version—we have some exciting updates that we aren't yet ready to share, and it will all make more sense as the system comes together. [#48](https://github.com/processing/processing4/issues/48)

* In the meantime, if you'd like to customize the colors, instructions are [here](https://github.com/processing/processing4/wiki/Themes).

* The "4" icon and the current "About" screen are only placeholders. I was tired of looking at the 3.x design, and also needed to be able to tell the versions apart from one another. It's also necessary to start the replacement process: figuring out what things need to be updated when we incorporate the real versions.

* All that said, major work underway for improvements to how the visual theme is handled. We hope to ship with a dark mode option as well. Stay tuned!

* The splash screen handler has been rewritten because the Windows version in launch4j was too brittle. The downside is that it's a fraction of a second slower to show up, but the upside is that hi-res Linux and Windows displays get a nice 2x version instead of the crunchy low-fi (not in a good way) version.

* Add `ui.font.family` and `ui.font.size` as preferences.

* Added support for `0x` numbers to support alpha colors in `theme.txt`.


### Bug Fixes

* Code completion is fixed. Thanks [Sam](https://github.com/sampottinger)! [#177](https://github.com/processing/processing4/issues/177), [#219](https://github.com/processing/processing4/pull/219)

* `mouseButton` was not set correctly on `mouseReleased()` with Java2D. [#181](https://github.com/processing/processing4/issues/181)

* A more useful message for the `NoClassDefError: processing/core/PApplet` startup error on Windows was in the alpha 5 source, but may not have made it into the actual release. [#154](https://github.com/processing/processing4/issues/154)

* Fix `Module javafx.base not found` on Linux. Thanks [letorbi](https://github.com/letorbi). [#214](https://github.com/processing/processing4/issues/214), [#215](https://github.com/processing/processing4/pull/215)

* After selecting a font other than Source Code Pro, font went to a default. [#216](https://github.com/processing/processing4/pull/216)

* `unregisterMethod()` was broken. [#223](https://github.com/processing/processing4/issues/223)

* Fixed "No library found for org.w3c.dom" message when using that (built-in) package.


### Changes

* When changing to an incompatible Mode, just open a new window instead of giving the user a potentially confusing error message about it. [#189](https://github.com/processing/processing4/issues/189)


### API Changes

None of these should break anything, but if they do, please let us know!

* `Editor.applyPreferences()` was `protected`, now it's `public`.

* Removed `Editor.repaintErrorBar()` and `Editor.showConsole()` because they didn't appear to be in use. Holler if this breaks anything.

* Renamed `TextAreaPainter.getCompositionTextpainter()` to `getCompositionTextPainter()`.


### Internal Changes

* Removed `java.class.path` when launching code from inside the PDE. This should prevent conflicts, and avoid introducing problems when using Export to Application.

* Removed compound key actions (which were undocumented and not in use). This clears up a lot of complexity in `DefaultInputHandler`.

* Remove `jdt.compiler.jar` from subprojects.

* Cleaned up lots of dead/unused parts of `javafx/build.xml`.

* Move `ISSUE_TEMPLATE` to the `.github` subfolder.

* Removed extra files from Tools folders for the download.

* Moved doclet to separate repo. Thanks DSI! [#218](https://github.com/processing/processing4/issues/218), [#222](https://github.com/processing/processing4/pull/222)



# Processing 4.0 alpha 5

*Revision 1274 – 24 June 2021*

Sneaking a release out the door the morning before our company meeting. Don't tell my boss, he's kind of a jerk.


### Known Bugs

* Code completion is currently broken. Any updates will be posted [here](https://github.com/processing/processing4/issues/177).

* Plenty of other issues being tracked in the [Processing 4](https://github.com/processing/processing4/issues) and [Processing 3](https://github.com/processing/processing/issues) repositories. Please help!


### Updates and Additions

* Added a [few more](https://github.com/processing/processing4/commit/7b75acf2799f61c9c22233f38ee73c07635cea14) “entitlements” to the macOS release that may help with using the Video and Sound libraries, especially on Big Sur.

* Moved from the 11.0.2 LTS version of JavaFX to the in-progress version 16. This fixes a [garbled text](https://bugs.openjdk.java.net/browse/JDK-8234916) issue that was breaking Tools that used JavaFX.

* JavaFX has been moved out of core and into a separate library. After Java 8, it's no longer part of the JDK, so it requires additional files that were formerly included by default. The Processing version of that library comes in at 180 MB, which seems excessive to include with every project, regardless of whether it's used. (And that's not including the full Webkit implementation, which adds \~80 MB per platform.)

* JavaFX is back and working again! This also fixes some Tools that relied on it, and Export to Application should be working as well. [#110](https://github.com/processing/processing4/issues/110), [#112](https://github.com/processing/processing4/pull/112), [#3288](https://github.com/processing/processing/issues/3288), [#209](https://github.com/processing/processing4/issues/209), [#210](https://github.com/processing/processing4/issues/210)


### Other Fixes and Changes

* Major font cleanup inside Preferences. It appears that the “Monospace” font may be [missing or broken](https://www.oracle.com/java/technologies/javase/11-relnote-issues.html#JDK-8191522) with OpenJDK 11. Does it show a random sans serif for you? Let us know if it does. But aside from that, the Editor and Console font should be behaving a little more reliably.

* The Windows splash screen should no longer be tiny. Still sorting out some hidpi issues on Windows, but this one might make things a bit less quirky. [#4896](https://github.com/processing/processing/issues/4896), [#145](https://github.com/processing/processing4/issues/145)

* Better handling of `NoClassDefError: processing/core/PApplet` on startup with Windows 10. [#154](https://github.com/processing/processing4/issues/154)

* No longer using `JFileChooser` on macOS; it's too awful. This means a Copy/Paste issue comes back, but the cure was worse than the disease. [#1035](https://github.com/processing/processing/issues/1035), [#77](https://github.com/processing/processing4/issues/77)


### Internal Changes

* The minimum system version for macOS (for the PDE and exported applications) is now set to 10.14.6 (the last update of Mojave). 10.13 (High Sierra) is no longer supported by Apple as of September or December 2020 (depending on what you read), and for our sanity, we're dropping it as well.

* Updated JNA from 5.7.0 to 5.8.0.

* Remove the ant binary from the repo, updated the version we're using from 1.8.2 to 1.10.10.

* Rebuilt the `appbundler` tool for macOS to handle some recent changes, and disabled some logging chatter as well.

* Update to launch4j 3.14, fixing Export to Application on Windows.


# Processing 4.0 alpha 4

*Revision 1273 - 15 June 2021*

Happy birthday to my goddaughter Kelsey! Let's celebrate with another alpha release.

This should be a bit more stable than the last round. I've rolled back some of the more aggressive anti-AWT changes (bad for longevity, good for compatibility) so images in particular are now behaving better.

But enough of that, let's go to the phone lines:


### What bugs have been fixed; why should I care?

* Sketch window location is saved once again: re-running a sketch will open the window in the same location. This was broken for a while! [#158](https://github.com/processing/processing4/issues/158), [#5843](https://github.com/processing/processing/issues/5843), [#5781](https://github.com/processing/processing/issues/5781)

* When using multiple monitors, new Editor windows will open on the same display as the most recently opened Editor window. [#205](https://github.com/processing/processing4/issues/205), formerly [#1566](https://github.com/processing/processing/issues/1566)

* A major Undo fix, this may even be [the big one](https://github.com/processing/processing/issues/4775), but it's not confirmed. (Please help confirm!) [#175](https://github.com/processing/processing4/pull/175)


### Were you too hasty with exorcising AWT?

* `cursor(PImage)` broken everywhere because `PImage.getNative()` returns `null` [#180](https://github.com/processing/processing4/issues/180)

* `PImage.resize()` not working properly. [#200](https://github.com/processing/processing4/issues/200)

* `copy()` not working correctly. [#169](https://github.com/processing/processing4/issues/169)


### Did you find any particularly niggling, but small issues?

* Catch `NoClassDefError` in `Platform.deleteFile()` (still unclear of its cause) on Big Sur. [#159](https://github.com/processing/processing4/issues/159), [#6185](https://github.com/processing/processing/issues/6185)

* Fixed `Exception in thread "Contribution Uninstaller" NullPointerException` when removing an installed contribution. [#174](https://github.com/processing/processing4/issues/174)

* If the default display is selected in the Preferences window, store that, rather than its number. It was discovered that plugging in a second display could bump the “default” display to number 2, even while it was still selected. Yay!

* Sort out calling `unregisterMethod()` for `dispose` from `dispose()` makes for bad state situation. [#199](https://github.com/processing/processing4/pull/199)


### How about contributions from the community?

+ Don't sort user's charset array when calling `createFont()`. [#197](https://github.com/processing/processing4/issues/197), [#198](https://github.com/processing/processing4/pull/198)

* Some exciting things are on the way for the documentation and web site. [#191](https://github.com/processing/processing4/pull/191)

* Update Batik to 1.14. [#179](https://github.com/processing/processing4/issues/179), [#192](https://github.com/processing/processing4/issues/192), [#183](https://github.com/processing/processing4/pull/183)

* Tweak the circle for number of updates based on Akarshit's initial attempt. [#201](https://github.com/processing/processing4/issues/201), [#4097](https://github.com/processing/processing/pull/4097)

* Make `parseJSONObject()` and `parseJSONArray()` return `null` when parsing fails. [#165](https://github.com/processing/processing4/issues/165), [#166](https://github.com/processing/processing4/pull/166)


### Is there anything new?

+ Added `PVector.setHeading()` for parity with p5.js. [#193](https://github.com/processing/processing4/issues/193)

* The default font (what you get if `textFont()` isnot used) has been changed to Source Sans instead of Lucida Sans. I just couldn't take Lucida any longer.


### Were there any internal changes I probably won't notice?

* Updated to JDK 11.0.11+9

* Update from JNA 5.2.0 to 5.7.0

* Modernize the RegisteredMethods code to use collections classes w/ concurrency. [#199](https://github.com/processing/processing4/pull/199)

* Set closed issues to [automatically lock](https://github.com/dessant/lock-threads) after they've been closed for 30 days. (This has no effect on open issues, only closed ones.) Actually this one you may have noticed if you had a lot of notifications turned on.

* Slowly transitioning some of the older code to newer syntax (lambda functions, etc). This is not a priority for anyone else: it's being done slowly, and as a chance to do code review on some very old work.


* Fix `textMode(SHAPE) is not supported by this renderer` message with SVG Export. [#202](https://github.com/processing/processing4/issues/202), [#6169](https://github.com/processing/processing/issues/6169)


# Processing 4.0 alpha 3

*Revision 1272 - 17 January 2021*

Happy [Martin Luther King Day](https://en.wikipedia.org/wiki/Martin_Luther_King_Jr._Day)! (Or MLK Day Eve, if you're reading this on Sunday.)

Several bug fixes and updates in this release, the most significant being video capture on macOS should be working again, and several OpenGL fixes that come with an updated release of JOGL. (Thanks to Sven Göthel, who continues working on it after many years.)

### Known Issues

* The ugly `surface.setResizable()` workaround in the previous release is now properly fixed. [124](https://github.com/processing/processing4/issues/124)
* Haven't had a chance to test much with macOS running on M1 machines. Chances are this should run in Rosetta mode, but I've not had time to find out.

### Fixes and Updates

* Video [was broken](https://github.com/processing/processing-video/issues/134) on macOS because of Apple's security changes.
* Audio was [also broken](https://github.com/processing/processing-sound/issues/51) on macOS because of Apple security changes.
* Fix `NullPointerException` in `getSystemZoom()` on startup in alpha 2. [143](https://github.com/processing/processing4/issues/143)
* `loadJSONObject()` and `loadJSONArray()` now return `null` if the given file was not found (in line with other `loadXxxx()` APIs. [6081](https://github.com/processing/processing/pull/6081)
* Update the splash screen to say 2021 before the pedants can hunt me down.
* Contribution translation updates (thank you!)
    * Updates and fixes for the Portugese translation [133](https://github.com/processing/processing4/pull/133), [134](https://github.com/processing/processing4/pull/134), [147](https://github.com/processing/processing4/pull/147)
    * Correct alphabetical order for the language list. [146](https://github.com/processing/processing4/pull/146)
* Remove zero width no-break space `U+FEFF` character with `trim()`.
* `PShapeOpenGL.setAttrib()` warning referenced `setNormal()` instead of `setAttrib()`. [141](https://github.com/processing/processing4/issues/141)
* Add `var` keyword to highlighting [114](https://github.com/processing/processing4/issues/114)
* Fix revision number in exported code [135](https://github.com/processing/processing4/issues/135)

### And More from Sam

* Fix preprocessor spaces in the `size()` command to follow our guidelines. [136](https://github.com/processing/processing4/issues/136), [138](https://github.com/processing/processing4/pull/138)
* Move `PdePreprocessIssueException` to the test package. [130](https://github.com/processing/processing4/issues/130), [139](https://github.com/processing/processing4/pull/139)
* Fix regression where  `smooth(4)` was showing the “smooth() can only be used inside settings()” error. [149](https://github.com/processing/processing4/issues/149), [152](https://github.com/processing/processing4/pull/152)

### Internal Additions

* You can now create a “source” `.jar` file by typing `ant source-jar` inside the `core` directory. [118](https://github.com/processing/processing4/issues/118)
* Update Batik from 1.8 to 1.13 inside SVG Export library. Fixes incompatibilities with Java 11.
* Automate macOS notarization in the build process (done in 4.0a2) [24](https://github.com/processing/processing4/issues/24)
* Show Tool incompatibilities with a message dialog, and clean up a little of the internal error handling.
* Prevent “illegal line” message when loading library with `0xFEFF` chars in a `.properties` file
* Fixes to `Platform` code
    * Get rid of `editor.laf.vaqua` preference (use the `editor.laf` preference instead)
    * Move macOS-specific code out of `DefaultPlatform` and into `MacPlatform`
* Clean up “Export to Application”
    * Turned off 32-bit and ARM exports (no longer supported)
    * Drop '64' from the folder name (everything 64-bit from now on)
    * Remove “big fat lie“ error spew on export
    * Too many `.dll` and `.jar` files were included
    * Updates and text changes to be a little clearer
    * Fixed links for Java 11
    * Set minimum version on Windows, fix JDK download URL


# Processing 4.0 alpha 2

*Revision 1271 - 15 September 2020*

Several fixes for this round, plus working on the guts quite a bit to prepare for newer/faster/better rendering methods.

The minimum system version for macOS (for the PDE and exported applications) is now set to 10.13.6 (the last update of High Sierra). Apple will likely be dropping support for High Sierra in late 2020, so we may make Mojave (10.14) the minimum by the time Processing 4.x ships.


### Known Issues

* If you're using P2D or P3D on macOS, and have `surface.setResizable(true)` inside `setup()`, you'll need to (temporarily) move that into `draw()`. We had to do an ugly hack at release time due to issue [124](https://github.com/processing/processing4/issues/124). The ugly hack also involves the window flickering once when it first opens in this situation. We should have that fixed for the next release.


### Bug Fixes

* Break `buildMenu()` into `populateMenu()` method to delay Debugger init [73](https://github.com/processing/processing4/issues/73)
* Fix broken macOS build [83](https://github.com/processing/processing4/issues/83)
* Bump JDK to 11.0.8, then rolled back to JDK 11.0.6 again [121](https://github.com/processing/processing4/issues/121), [123](https://github.com/processing/processing4/pull/123)
* Make macOS notarization part of the build process [24](https://github.com/processing/processing4/issues/24)
* `NullPointerException` in `ContributionManager.updateFlagged()` on startup. Now checks for directory `modes` and `tools` directories and read/write access on startup [6034](https://github.com/processing/processing/issues/6034)
* PDF was broken on `getImage()` call [62](https://github.com/processing/processing4/issues/62), [commit](https://github.com/processing/processing4/commit/624e9074aeb1d9e2e6b2943e35ffd97a90b8b737)


### Changes for AWT

To make way for more advanced rendering options, the exorcism of AWT from the base classes inside core has begun. More about that here: <https://github.com/processing/processing4/wiki/Exorcising-AWT>

* Remove all usage of AWT from `PApplet` [55](https://github.com/processing/processing4/issues/55)
* Make edits to core so that AWT can be disabled and LWJGL can run [commit](https://github.com/codeanticode/processing-openjdk/commit/ac9abc18655daaa538ef16945687177334f3596e)
* Add `--disable-awt` option to `PApplet.main()`
* Fix for precision issues with PDF [5801](https://github.com/processing/processing/issues/5801#issuecomment-466632459)
* Implement `displayDensity(int)`, it's been returning the main display's value
* Show “displays have separate spaces” warning message when the param is unset
    * Show it in the console, which allows us to get rid of `JOptionPane`
    * Catalina seems to have it un-set, but the default is the same
* Move `selectInput/Output/Folder` to `ShimAWT` class
* remove the `java.awt.Frame` object from `PApplet`
* Move `loadImage()` into `ShimAWT`
* `desktopFile()` and `desktopPath()` methods are supported, unless we find they're trouble
* Move `ShimAWT.loadImage()` to the `PSurface` subclasses
* Move all `java.awt` and `javax.imageio` out of `PImage`
* Make the switch to `getModifiersEx()` instead of `getModifiers()` [4](https://github.com/processing/processing4/issues/4)
* Fix `PImage.save()` breakage due to AWT changes above (saving to PNG was broken in anything but the default renderer) [80](https://github.com/processing/processing4/issues/80)


### Sam was at it again

* Zoom dialog fonts based on user scale setting [111](https://github.com/processing/processing4/issues/111), [125](https://github.com/processing/processing4/pull/125)
* Resolve rewrite of pixelDensity to settings in preproc [58](https://github.com/processing/processing4/issues/58), [60](https://github.com/processing/processing4/pull/60)
* Resolve PDF renderer parse issue in preproc [66](https://github.com/processing/processing4/issues/66), [68](https://github.com/processing/processing4/pull/68)
* Bump JOGL 2.4 to the new release candidate (20200307) [85](https://github.com/processing/processing4/pull/85)
* Remove debugging printout from Open Recent [78](https://github.com/processing/processing4/issues/78), [79](https://github.com/processing/processing4/pull/79)
* Fix broken tests [92](https://github.com/processing/processing4/issues/92), [93](https://github.com/processing/processing4/pull/93)
* Refactor out preproc.issue [96](https://github.com/processing/processing4/pull/96)
* Debug button in the toolbar is currently broken [94](https://github.com/processing/processing4/issues/94), [95](https://github.com/processing/processing4/pull/95)
* Fix `WARNING: Illegal reflective access by processing.opengl.PSurfaceJOGL” on getContextCapabilities()` [50](https://github.com/processing/processing4/issues/50), [76](https://github.com/processing/processing4/pull/76)
* Migrate JSSC to sampottinger/jssc [71](https://github.com/processing/processing4/issues/71), [75](https://github.com/processing/processing4/pull/75)
* Rewrite size call for all renderers [90](https://github.com/processing/processing4/issues/90), [91](https://github.com/processing/processing4/pull/91)
* Switch to JFileChooser on Mac with VAqua [88](https://github.com/processing/processing4/pull/88)
* Cut/Copy/Paste while saving a sketch on macOS was going to the editor, not the save dialog [77](https://github.com/processing/processing4/issues/77)
* Implement the basics of Dark Mode for the Mac [89](https://github.com/processing/processing4/issues/89)
* `color` as return type was broken [104](https://github.com/processing/processing4/issues/104), [105](https://github.com/processing/processing4/pull/105)
* Automated (jenkins) build broken because ant 1.10.7 download no longer available [106](https://github.com/processing/processing4/issues/106), [107](https://github.com/processing/processing4/pull/107)
* Processing IDE interface too small on high-res Windows displays [102](https://github.com/processing/processing4/issues/102)
* Ensure not trying to use Toolkit zoom before ready [103](https://github.com/processing/processing4/pull/103)


### Other Contributed Changes

* Remove some redundant boxing and casting [51](https://github.com/processing/processing4/pull/51)


# Processing 4.0 alpha 1

*Revision 1270 - 18 January 2020*

This is a massive update! With the help of [Sam Pottinger](https://gleap.org/), we're working to get Processing to run with Java 11. This will give us a more stable platform for the next few years.

In the process, there are also significant updates which include updated Java syntax support and lots of other long-awaited features.

We've started a [Changes in 4.0](https://github.com/processing/processing4/wiki/Changes-in-4.0) document to keep track of all the details.

We recommend using a different sketchbook location for 4.0, to avoid confusion with things that might be incompatible with your 3.0 work.


### Sam Did a Lot of Work

* [This](https://github.com/processing/processing4/pull/1) massive pull request has the changes that got things kicked off. It closes several issues and pull requests, including [5750](https://github.com/processing/processing/issues/5750), [5753](https://github.com/processing/processing/pull/5753), [4415](https://github.com/processing/processing/issues/4415), and others.
* ANTLR has been updated from version 2 to version 4. The grammar has been updated for Java 8 and a new pre-processor created. [3054](https://github.com/processing/processing/issues/3054) and [3055](https://github.com/processing/processing/issues/3055)
* Support for Travis CI has been added. [2747](https://github.com/processing/processing/issues/2747)
* The macOS integration layer has been updated for Java 11. [5747](https://github.com/processing/processing/pull/5747)
* Nested generics now work properly. [4514](https://github.com/processing/processing/issues/4514)
* Several fixes for the build process. [12](https://github.com/processing/processing4/pull/12), [6](https://github.com/processing/processing4/issues/6)
* Update to JNA 5, and migrate code `Native.load()` calls. [7](https://github.com/processing/processing4/issues/7), [15](https://github.com/processing/processing4/pull/15)
* Fix console font spacing. [19](https://github.com/processing/processing4/issues/19), [20](https://github.com/processing/processing4/pull/20)
* Implement `import static`. [18](https://github.com/processing/processing4/pull/18), [5577](https://github.com/processing/processing/issues/5577)
* Fixing problems with Windows UI scaling. [21](https://github.com/processing/processing4/issues/21), [30](https://github.com/processing/processing4/pull/30)
* Fix display density detection and use `GraphicsConfiguration`. [32](https://github.com/processing/processing4/issues/32), [35](https://github.com/processing/processing4/issues/35), [34](https://github.com/processing/processing4/pull/34)
* Fix `WARNING: Illegal reflective access by processing.app.ui.Toolkit to field sun.awt.CGraphicsDevice.scale` warning on startup.
* Replace macOS-specific fullscreen option for `setResizable()`. [36](https://github.com/processing/processing4/issues/36)
* Several tests have been added, and are called by default during `ant dist`. [38](https://github.com/processing/processing4/pull/38), [8](https://github.com/processing/processing4/issues/8)
* Update from Java 11.0.2 to 11.0.5, and eventually 11.0.6. [40](https://github.com/processing/processing4/pull/40), [39](https://github.com/processing/processing4/issues/39)
* Fix Java 11 incompatibilities inside `PSurfaceFX`. [5286](https://github.com/processing/processing/issues/5286)
* Fixed `Table`'s use of deprecated `isAccessible()`. [33](https://github.com/processing/processing4/pull/33), [3](https://github.com/processing/processing4/issues/3)


### Other Contributed Fixes

* Disable FBO when using Intel HD 3000 Graphics. [4104](https://github.com/processing/processing/issues/4104), [5881](https://github.com/processing/processing/pull/5881)
* `rotateZ()` was breaking in `PShapeOpenGL`. [28](https://github.com/processing/processing4/issues/28), [41](https://github.com/processing/processing4/pull/41)


### Cross-ported from 3.5.4

* This release includes all updates found in [Processing 3.5.4](https://github.com/processing/processing/releases/tag/processing-0270-3.5.4)


### Known Issues

* Only basic updates have been made to remove references to 3.x. [48](https://github.com/processing/processing4/issues/48)
* A complete list of issues can be found [here](https://github.com/processing/processing4/issues)


***


Revisions for Processing 3.x and earlier have been removed. [You can find them here.](https://raw.githubusercontent.com/processing/processing/master/build/shared/revisions.txt)