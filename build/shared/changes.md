# Processign 4.0 beta 6

*Revision 1281 – 13 February 2022*

Continuing the unintentional tradition of Processing releases on [Super Bowl Sundays](https://en.wikipedia.org/wiki/Super_Bowl_Sunday), here's beta 6.


## Scaling and stretching on Windows

* Windows scaling at 125% was stretching sketches to make them look crusty. [#378](https://github.com/processing/processing4/issues/378)

* `pixelDensity()` not working in exported Windows applications. [#5414](https://github.com/processing/processing/issues/5414#issuecomment-841088518)

* Implement sketch scaling in `PApplet` so that they take UI sizing into account. [#4897](https://github.com/processing/processing/issues/4897), [#4894](https://github.com/processing/processing/issues/4894)


## Hell freezes over

For those of you that use Git (or other version control) this release includes an experimental(!) feature that lets you separate the name of the sketch from the name of the main tab. This means that you can use "Save As…" and it won't rename the file for the main tab. *Or* you can rename a sketch folder and the sketch inside will still work properly.

Uncheck the “Keep sketch name and main tab in sync” option in Preferences, and you're on your way. This is a *major* change to some of the oldest code in the PDE. If it has bugs, it may eat code. **Use with caution and report any issues you see immediately!**

Again, please help test this feature and [file issues](https://github.com/processing/processing4/issues/new) if you run into trouble.


## Bug fixes

* Actually set the correct names for the `windowMove()` and `windowResize()` functions that were announced in beta 5. [#53](https://github.com/processing/processing4/issues/53)

* PDF export not working with Processing 4.0 beta 5. [#395](https://github.com/processing/processing4/issues/395)

* Lots of issues discovered when making the sketch naming change. For instance, it looks like it was sometimes necessary to twice select the Mode for Android sketches. Also sorts out issues with when `sketch.properties` was or was not written.

* Fix `NullPointerException` on first use of Export to Application.

* RGB image created with `createImage()` has alpha channel. [#388](https://github.com/processing/processing4/issues/388)

* `CODED` is inconsistent between the default renderer and P2D/P3D. [#376](https://github.com/processing/processing4/issues/376)

* Fix multi-line strings not terminating properly. [#398](https://github.com/processing/processing4/issues/398), [#400](https://github.com/processing/processing4/pull/400)

* Clean up text sizes in the warning dialog box shown when sketch files change outside the Editor.

* Clean up error text in the console that showed up when sketch files removed from a sketch while it was still open (after the user was asked about them).


## Minor changes

* Fix up “Display N does not exist, using the default display instead” message. If more than one display, shows list of available displays (size and index).

* Move "Add Examples" to bottom of the Examples window

* Change cmd-click in window title to point to sketch folder, not the main PDE.


## Things you're less likely to notice

* Updated to Java 17.0.2+8. We hope this may fix the `java.lang.NoSuchMethodError: accessibilityHitTest` error. [#408](https://github.com/processing/processing4/issues/408)

* Removed anachronistic `Preferences.save()` that happened after opening a sketch.


## Until further notice

**The macOS-aarch64 (Apple Silicon) release is not yet complete. If you want to use P2D and P3D (OpenGL) on macOS, use the Intel (x86_64) build, which is the only one that will be linked from processing.org/download until this situation is resolved.** See [\#370](https://github.com/processing/processing4/issues/370) for status updates.


# Processing 4.0 beta 5

*Revision 1280 – 3 February 2022*

Tomorrow morning is the first day of using Processing in class, so I thought I'd add some chaos to that by posting a release the night before. Having started the first day of class being locked out of the classroom, I'm just looking for ways to keep the students from getting too comfortable.


## Things you might notice in this release

* We've hopefully finally, *finally*, _finally_ fixed the problems with the text caret position. [\#194](https://github.com/processing/processing4/issues/194), [\#226](https://github.com/processing/processing4/issues/226), and [\#342](https://github.com/processing/processing4/issues/342)

* Syntax coloring updates based on the themes, mostly for the darker varieties.

* Added “Refresh” and “Show Folder” buttons to the bottom of the sketchbook window. Also cleaned up some of the guts to make it faster and less… quirky.

* Several fixes for resizing windows: P2D and P3D sketches should flicker red less, and the `NullPointerException`s when resizing sketches with the default renderer should be resolved. [\#386](https://github.com/processing/processing4/issues/386), [\#162](https://github.com/processing/processing4/issues/162), [\#4129](https://github.com/processing/processing/issues/4129), and [\#186](https://github.com/processing/processing4/issues/186)

* When running a sketch, it shows the “Play” icon instead of the Processing icon for its windows (just like it does when running an exported application).


## Out with the old…

* The twenty year old (and sometimes buggy) TIFF reader/writer has been removed, so Java's internal ImageIO is now used for TIFF files. The code is slightly slower, but the saved files are more compatible, and `loadImage("blah.tif")` will also work on more files than in the past.

* Also, image file names passed to `save()` and `saveFrame()` must have an extension. We are no longer adding `.tif` to files with no extension, because that can lead to confusing results, and the behavior is inconsistent with the rest of the API.

* Rewrote the ten year old (since we're keeping track of these things) code that handles `fullScreen()` to get it working on Apple Silicon. [\#221](https://github.com/processing/processing4/issues/221) and [\#372](https://github.com/processing/processing4/issues/372)


## Sam fixing up the preprocessor

* Several bug fixes to the preprocessor. [\#384](https://github.com/processing/processing4/pull/384)

* Better error message when mixing "active" (with setup and draw) and "static" (no function definitions) code styles. [\#290](https://github.com/processing/processing4/issues/290)

* Fix problem with function `size(int, int)` in classes. [\#317](https://github.com/processing/processing4/issues/317)

* Add (provisional) support for multi-line string text blocks. [\#371](https://github.com/processing/processing4/issues/371)

* `fullScreen()` when specifying the display number was broken. [\#352](https://github.com/processing/processing4/issues/352) and [\#392](https://github.com/processing/processing4/pull/392)


## Feature improvements in progress

* Adding core functions to replace the `surface` methods:
    * `surface.setSize(w, h)` now available as `windowResize(w, h)`
    * `surface.setResizable()` is now `windowResizable()`
    * `surface.setLocation(x, y)` is now `windowMove(x, y)`
    * `surface.setTitle()` is now `windowTitle()`

    There are also new event handlers `windowResized()` and `windowMoved()` that are called when the sketch window is resized or moved. We may still tweak the naming and behavior of these a little (consider these an "alpha" feature), but it's progress. [\#53](https://github.com/processing/processing4/issues/53)

* Added necessary infrastructure to support language translations in Modes. [\#236](https://github.com/processing/processing4/issues/236), [\#237](https://github.com/processing/processing4/pull/237), [\#2833](https://github.com/processing/processing/pull/2833), [\#3154](https://github.com/processing/processing/issues/3154), and [\#3337](https://github.com/processing/processing/pull/3337)

* Plumbing is in place to set the DPI of PNG images and the compression level of JPEG images. Though we still need some API for it.


## Plumbing you probably won't notice

* Several changes to how image saving is handled. There were lots of ways this could go wrong, this gets rid of several of them. Hopefully no new regressions!

* Tweaked how URLs are opened for better compatibility on different operating systems.

* Debugging failed installation of `.pdez` files.


# Processing 4.0 beta 4

*Revision 1279 – 24 January 2022*

Loooots of changes under the hood to better support more platforms. And by that, we mean Apple Silicon (now in alpha!) and Raspberry Pi devices (once again). And whatever else gets cooked up in the coming years.

For the Windows users, the scaling issues have finally been sorted out. Use 125% and 250% and whatever weird Windows scaling you want, and it will work. You can safely uncheck the “Disable HiDPI scaling” checkbox in Preferences.

Against my better judgement, I'm posting a release that's native for Apple Silicon. OpenGL is currently broken. Sketches crash immediately. You should only use it if you're a masochist. Or a masochist that likes *extremely fast* but very incomplete software, and won't file bug reports saying “OpenGL is broken.” But it's being posted for folks who do not need OpenGL, and/or want to help debug. [\#370](https://github.com/processing/processing4/issues/370)

We've also moved the FX2D (JavaFX) renderer to its own library, which is available from the Contributions Manager. In related news, the download size is now 230 MB instead of 493 MB.


## New features!

* New downloads for Apple Silicon (incomplete! [\#128](https://github.com/processing/processing4/issues/128)), and Raspberry Pi (32- and 64-bit). Some things are still broken, so the releases will be on Github but not processing.org/download.

* The design continues coming together, with new icons for exported applications, document files (`.pde`, `.pdex`, and `.pdez`), plus the Foundation icon used in the Contributions Manager. This work is still in progress, at least until [\#48](https://github.com/processing/processing4/issues/48) is closed.

* Re-implement settings.path to support portable versions. [\#3948](https://github.com/processing/processing/issues/3948), [362](https://github.com/processing/processing4/issues/362), [\#360](https://github.com/processing/processing4/pull/360), fixed with [1a49263a](https://github.com/processing/processing4/commit/1a49263a94a2d7af2b4686286406f7896d207cd9)


## We're all regressing, why can't your software?

* Double-clicking .pde files on macOS was broken in beta 3. [\#347](https://github.com/processing/processing4/issues/347)

* PShape not allowing attibutes of type `int`. [\#344](https://github.com/processing/processing4/issues/344), [\#363](https://github.com/processing/processing4/pull/363)

* Using `P2D` with `createShape()` causing `GLException`. [\#353](https://github.com/processing/processing4/issues/353), [\#367](https://github.com/processing/processing4/pull/367)

* Fix error in internal utility function to invert scaling. [\#366](https://github.com/processing/processing4/issues/366), [\#217](https://github.com/processing/processing4/issues/217), [\#367](https://github.com/processing/processing4/pull/367)


## Other fixes…

* Tons of work to get the Windows version of the application to scale properly. Even the opening splash screen is behaving better. [\#342](https://github.com/processing/processing4/issues/342)

* `displayDensity()` was returning 1 when run from the PDE on Windows. [\#339](https://github.com/processing/processing4/issues/339)

* Fix "Could not delete disable_hidpi" message that was showing up on macOS and Linux after closing the Preferences window.

* Major work inside the Contributions Manager: links to projects, links in description text now working. Some fixes to speed things up. Removing hundreds of lines of vestigial code. Fix the font being used on the progress bar.

* Removed "Illegal reflective access" warning on Linux. [\#207](https://github.com/processing/processing4/issues/207)


## Changes

* Native libraries for OpenGL are no longer packed inside `.jar` files. This fixes slowdowns on Windows caused by antivirus software kicking into high gear. It also gives us a path to get OpenGL working on Apple Silicon. Unfortunately, this also means you'll need to set `java.library.path` in an IDE outside the PDE. [\#4783](https://github.com/processing/processing/issues/4783#issuecomment-269328168), [\#4239](https://github.com/processing/processing/issues/4239), [JOGL](https://jogamp.org/bugzilla/show_bug.cgi?id=1301), [Stack Overflow](https://stackoverflow.com/a/51100411)

* JavaFX moved to its own library. It no longer works with Tools and Modes as a result, but it was just too hefty relative to its importance. Read about it in [\#348](https://github.com/processing/processing4/issues/348)

* A massive amount of work for our supported platforms. Some of the gritty details are [here](https://github.com/processing/processing4/wiki/Supported-Platforms) but we have six of them. Export to Application has been rewritten. It's now possible to build Processing on all six of these platforms.

* Cross-platform support for native libraries has also changed significantly. The `export.txt` file is ignored completely (no libraries required it anyway), and the subfolders names for native libraries have changed. See the layout of `core/library` for an example. Again, this enables all these platforms from Apple Silicon to Raspberry Pi.


## Contributed tidbits

* Select the entire line when doing Edit → Copy on an empty selection. [\#100](https://github.com/processing/processing4/pull/100)


## You probably won't notice, but it's noted here in case you do

* Set the minimum Java version for Windows launcher to 17.0.1.

* Bumped Ant from 1.10.10 to 1.10.12.

* Upgraded JNA from 5.8.0 to 5.10.0.

* Spent a bunch of time looking into RSyntaxArea. It's not gonna happen. [\#355](https://github.com/processing/processing4/issues/355), [\#3199](https://github.com/processing/processing/issues/3199), and [the details](https://github.com/processing/processing4/blob/master/app/src/processing/app/syntax/README.md).


# Processing 4.0 beta 3

*Revision 1278 – 15 January 2022*

New colors in the UI! We're still making changes to the design of the UI, but this release includes a new theme engine that makes it possible to select interface colors from a set of options, as well as more control over choosing your own schemes. Check out Tools → Theme Selector for the magic.

We've also moved to Java 17, fixed lots of bugs, added a new default color scheme, and implemented better support for multiple windows with OpenGL.

We even updated the loading screen to include 2022 in the copyright notice.


## Fixing the bugs that won't fix themselves

* Added an option to “Disable HiDPI Scaling” on Windows for users that were having trouble with the editor caret (cursor) showing up in the wrong position, or text in the interface (on the tabs in particular) looking oddly jagged. [\#226](https://github.com/processing/processing4/issues/226), [\#231](https://github.com/processing/processing4/issues/231)

* The “Disable HiDPI Scaling” option is a temporary workaround, with ongoing development taking place at [\#342](https://github.com/processing/processing4/issues/342). There's also a console warning if Processing detects that your display may have issues, however it's nearly impossible to do this with certainty. As a result, we can't enable the option by default, because it would make things worse for users who don't need it. Fixing this issue requires spending a lot of time testing different Windows systems, resolutions, monitor setups, etc.

* Too much writing to the console (from both System.out and System.err) causing the software to lock up completely. [\#338](https://github.com/processing/processing4/issues/338)

* Got rollovers working again for the Toolbar buttons. Somewhat comically, these seem to have broken at some point during the 3.x development process, and nobody noticed.

* Fix a problem where the default font would misbehave after `textSize()` was called. Turns out the problem was that the wrong font was being used in the first place. But also added a warning for users when unsupported characters are used with the default font. [\#303](https://github.com/processing/processing4/issues/303), [\#4956](https://github.com/processing/processing/issues/4956)

* `listFiles()` and `listPaths()` with an extension specified were not properly matching directories.

* Fix for `disableStyle()` with 2D shapes in `P3D`.

* Allow `GEOMETRY` (not just `PATH`) with `contains()` in PShape. The `contains()` method is still imperfect, but it's just as bad with polygon shapes as path shapes.

* The Open/Save dialog box was crashing on Linux. Can't reproduce with this release, which uses Adoptium OpenJDK 17, so fingers crossed that it's resolved. [\#306](https://github.com/processing/processing4/issues/306)


## Known Issues, the thorns that remain in our side

* The included version of the documentation is for 3.0, not the newer 4.0.

* The display scaling issues on Windows need to be resolved without folks having to use Preferences. [\#342](https://github.com/processing/processing4/issues/342)

* …aaaand pretty much anything [here](https://github.com/processing/processing4/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc) and [here](https://github.com/processing/processing/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc). Please help!


## Major internal work to support UI themes

* As mentioned above, several new themes are available from the “Theme Selector” option in the Tools menu. Selecting a theme will create a `theme.txt` file in your sketchbook that sets the interface colors.

* If you make changes to the `theme.txt` file in your sketchbook, you can select Tools → Update Theme to see those changes reflected in the interface. If there's no `theme.txt` file in your sketchbook, it will create a `theme.txt` file in your sketchbook, which you can edit. After you've saved your changes, select “Update Theme” again to see the updates.

* Selecting a new theme will resave your `theme.txt` file under a new name (`theme.001`, `theme.002`, etc.)

* Now using custom scrollbar widgets in the Editor so that it can better match the rest of the interface.

* Auto-generating toolbar and tab icons from SVG files based on theme colors.

* Implement automatic update for changes to `theme.txt` in the sketchbook.

* More documentation about all of this soon, which will live [here](https://github.com/processing/processing4/wiki/Themes).


## Hark, a sound from the West, and it is Sam

* Error when calling `smooth()` on `PGraphics` [\#272](https://github.com/processing/processing4/issues/272)

* Detect if calling special methods on `PApplet` or not (and restore unit tests) [\#288](https://github.com/processing/processing4/pull/288)

* Move Mockito to a new version. [\#287](https://github.com/processing/processing4/issues/287)


## Behold, a sound from the East, and it is Andrés

* Finalizing support for multiple windows with OpenGL. [\#312](https://github.com/processing/processing4/issues/312), [\#313](https://github.com/processing/processing4/pull/313)

* Implement buffer object streaming for `P2D` and `P3D` and finalize attribute API in `PShape`. [\#314](https://github.com/processing/processing4/pull/314)

* Add support to `PATH` shapes in `P2D` renderer. [\#6009](https://github.com/processing/processing/issues/6009), [\#316](https://github.com/processing/processing4/pull/316)


## The community continues pitching in

* Update Ukrainian language strings. [\#301](https://github.com/processing/processing4/pull/301)

* Splash screen has default OpenJDK icon. [\#297](https://github.com/processing/processing4/issues/297), [\#329](https://github.com/processing/processing4/pull/329)


## Maintaining this 20~~0~~-year-old house

* Use UTF-8 for `readString()` and `write()` in net client. Avoids platform-specific behavior; Java 18 also making UTF-8 the default. [\#336](https://github.com/processing/processing4/issues/336)

* Cleaning up the Create Font dialog while tracking down [\#278](https://github.com/processing/processing4/issues/278). Removed `Serif`, `SansSerif`, `Monospaced`, `Dialog`, `DialogInput` from Create Font. Also sorting the list of font names, and skipping fonts that start with `.` or `#` because they're supposed to be hidden from users.


## Internal build and development changes

* Update [appbundler](https://github.com/TheInfiniteKind/appbundler) with the latest from upstream.

* Replace JDK 11 and JavaFX 16 with JDK 17 and JavaFX 17. [\#285](https://github.com/processing/processing4/issues/285)

* Move up from JavaFX 17.0.0.1 to 17.0.1.

* Get rid of version numbers in the name of the `batik.jar` file.

* `ffmpeg` not downloading correctly on M1 machines. [\#319](https://github.com/processing/processing4/issues/319), [\#5775](https://github.com/processing/processing/issues/5775), [\#5714](https://github.com/processing/processing/issues/5714), [\#6230](https://github.com/processing/processing/issues/6230)


# Processing 4.0 beta 2

*Revision 1277 – 4 October 2021*

Had to put things down for a while after the big push to get beta 1 out the door. But now we're back with an update mostly focused on fixing a few things that were broken.


## Lots of fixes

* Export to Application no longer broken when using P2D or P3D on macOS. (Though if you're a macOS developer, we could [use some help](https://github.com/processing/processing4/issues/284)) [\#249](https://github.com/processing/processing4/issues/249)

* Allow imports with `color` in the name. This gets everyone's favorite toxiclibs working again. Got a fix from Sam, fingers crossed that it doesn't introduce new quirks. [\#240](https://github.com/processing/processing4/issues/240), [\#246](https://github.com/processing/processing4/pull/246)

* Movie Maker no longer broken when there are spaces in the path to Processing. [\#268](https://github.com/processing/processing4/issues/268)

* Debugger was not working when selecting Debug from the menu, now fixed. [\#282](https://github.com/processing/processing4/issues/282)

* "Massachusetts Institue of Technology" typo in loading screen. Not actually a big fix, but lots of reports about this one. [\#254](https://github.com/processing/processing4/issues/254)


## …but it's still a beta

Not everything is fixed yet. Here are a few of the more obvious:

* The colors, theme, buttons, interface, icons, etc are not yet complete. We have the necessary help, we just need more time. [\#48](https://github.com/processing/processing4/issues/48)

* Windows: Editor cursor position is offset to the right when display scaling >100% [\#226](https://github.com/processing/processing4/issues/226)

* macOS: IDE cursor position is wrong if font size is changed in Preferences. This may be specific to multiple displays, when one is retina and the other is not. [\#194](https://github.com/processing/processing4/issues/194)

* Export to Application not working with the current video library. The video library isn't handled by me, but we keep hearing about it. Updates will be posted [here](https://github.com/processing/processing-video/issues/188).


## Contributed Fixes

I love getting help from the community. Thank you!

* The welcome screen was persistent, even if you told it to go away. Oops. [\#48](https://github.com/processing/processing4/issues/48), [\#253](https://github.com/processing/processing4/issues/253)

* Fix minor typos in the PWM code for the IO library. [\#266](https://github.com/processing/processing4/pull/266)

* French translation: fixed typos and added missing items. [\#258](https://github.com/processing/processing4/pull/258)

* Fixed for JSSC library layout and upstream updates. [\#119](https://github.com/processing/processing4/issues/119), [\#229](https://github.com/processing/processing4/pull/229)


## Other Changes

Things you may not actally notice.

* Added `-Dpython.console.encoding=UTF-8` for Python Mode at the [request of jdf](https://github.com/jdf/Processing.py-Bugs/issues/322).

* Removed translated URLs that are not actually translated. Some of the Help menu translations were pointed at ancient links. [\#250](https://github.com/processing/processing4/issues/250)

* Switch to getModifiersEx() in `processing.app` code. [\#67](https://github.com/processing/processing4/issues/67)

* Deal with getFontMetrics() deprecation warning in EditorToolbar.


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

* It's now possible to bundle sketches into a single file to be loaded inside the PDE. This is done with `.pdez` files, which is a sketch folder saved as a `.zip` but with the extension changed to `.pdez`. This means you can post a sketch on the web as a `.pdez`, and someone with Processing can click the link and have it load directly in the PDE. [\#73](https://github.com/processing/processing/issues/73), [\#3987](https://github.com/processing/processing/issues/3987)

* Similar to `.pdez` files, you can install Libraries, Modes, Tools, and Example sets (things that would normally be installed with the Contribution Manager) by renaming their `.zip` file to `.pdez`. Double-clicking a `.pdez` file will open it with Processing, and ask the user whether they'd like to install it.

* It's now possible to do code completion and refactoring even when `.java` tabs are included in a sketch. Thanks Sam! [\#157](https://github.com/processing/processing4/issues/157), [\#230](https://github.com/processing/processing4/pull/230)

* Moved the preferences to `~/.config/processing` on Linux instead of `~/.processing`. This means your settings will be reset, but for most, that will be more of a plus with 4.x. [\#203]( https://github.com/processing/processing4/issues/203)

* Initial update of the splash screen and icons. These still need some work: the icons are too muddy at smaller sizes, for instance.

* The Welcome screen has been reset, so folks will see it again. We haven't made a decision on the Welcome screen for the final 4.0, but plan for it to be more useful than what's there now.

* “Show Sketch Folder”, “Add File”, and “Export to Application” now require Untitled or Read-Only sketches to be saved first, which avoids a weird situation where the user is dealing with files in hidden temporary folders. [\#2459](https://github.com/processing/processing/issues/2459)

* The reference for Java Mode is now served up from a web server that lives inside the PDE. This means that the download has just a single file for the reference, instead of thousands of tiny `.html` files. Far fewer things to copy makes the installation process much smoother.


### Bug Fixes

* Really chatty console messages for longtime users who had older (like 2.x) settings files still on their machine.

* IDE cursor position on Windows was going weird if display scaling used. [\#226](https://github.com/processing/processing4/issues/226)

* Only call `errorTable.updateTheme()` if the Mode is using an Error Table (Python was not).

* `PShape.scale()` not working with `PShape.resetMatrix()` when P2D renderer is used. [\#217](https://github.com/processing/processing4/issues/217), [\#225](https://github.com/processing/processing4/pull/225)


### Should Be Fixed

Several things that should no longer be a problem based on updates we've done in 4.x, but not necessarily verified 100%.

* Undo feature may have undesired results (Fixed in 4.0a4) [\#4775](https://github.com/processing/processing/issues/4775)

* HiDPI support for GNOME desktop. [\#6059](https://github.com/processing/processing/issues/6059)

* AppKit errors from P2D/P3D. [\#5880](https://github.com/processing/processing/issues/5880)

* Export Application broken in Processing 3.5.4 when using P2D or P3D renderers. [\#5983](https://github.com/processing/processing/issues/5983)

* Cannot run `rotateZ()` within the `PShape` class. [\#5770](https://github.com/processing/processing/issues/5770)

* `Profile GL4bc is not available on X11GraphicsDevice` error fixed with new JOGL release. [\#6160](https://github.com/processing/processing/issues/6160), [\#6154](https://github.com/processing/processing/issues/6154).

* `Profile GL3bc is not available on X11GraphicsDevice` should also be fixed. [\#5476](https://github.com/processing/processing/issues/5476)


### Internal Changes

Things you're unlikely to notice, but in case something seems off, it's better to make note of them in case you see a related problem pop up.

* Now using JDK 11.0.12+7.

* Cleaning up `suggestions.txt` handling and related code.

* Removed code for pulling fonts from `JAVA_HOME/lib/fonts`, because it no longer exists in Java 11.

* Update `EditorFooter.updateMode()` to `EditorFooter.updateTheme()` and add it to the code called by `Editor.updateTheme()`

* Removed the JRE Downloader, because it's no longer necessary. [\#155](https://github.com/processing/processing4/issues/155)

* Changed `Messages.loge()` to `Messages.err()`.


### Known Issues

* Support for `.pdez` and `.pdex` is not yet complete on Linux. Please help! [\#239](https://github.com/processing/processing4/issues/239)

* Have I mentioned that the design of the icons, theme, layout, etc aren't finished?



# Processing 4.0 alpha 6

*Revision 1275 – 10 July 2021*

Code completion is back!

Added a new Movie Maker that creates MP4s and Animated GIFs!

And a new color scheme sure to bring out the cranks!


### New Features

* Movie Maker has been rewritten and much improved! It can now directly create high quality MPEG-4 videos and Animated GIFs. Due to Apple [removing support for most video codecs](https://support.apple.com/en-us/HT202884) after macOS Mojave, we could no longer export QuickTime videos. The new Tool uses [FFmpeg](https://ffmpeg.org/) behind the scenes. The Tool also supports [Apple ProRes 4444](https://support.apple.com/en-us/HT202410), which is very high quality, and incidentally, the format that Apple's “QTMovieModernizer” formerly used to re-encode “legacy” video formats. [\#6110](https://github.com/processing/processing/issues/6110)


### Design and Themes

* We've started work on refreshing the design. This round has a new set of colors. If you like them, great! If not, please hold your complaints. The internet doesn't need more negativity! We know some people won't like it, and we're [still working on it](https://twitter.com/ben_fry/status/1409968426093735941). We think you'll be happy with the final version—we have some exciting updates that we aren't yet ready to share, and it will all make more sense as the system comes together. [\#48](https://github.com/processing/processing4/issues/48)

* In the meantime, if you'd like to customize the colors, instructions are [here](https://github.com/processing/processing4/wiki/Themes).

* The "4" icon and the current "About" screen are only placeholders. I was tired of looking at the 3.x design, and also needed to be able to tell the versions apart from one another. It's also necessary to start the replacement process: figuring out what things need to be updated when we incorporate the real versions.

* All that said, major work underway for improvements to how the visual theme is handled. We hope to ship with a dark mode option as well. Stay tuned!

* The splash screen handler has been rewritten because the Windows version in launch4j was too brittle. The downside is that it's a fraction of a second slower to show up, but the upside is that hi-res Linux and Windows displays get a nice 2x version instead of the crunchy low-fi (not in a good way) version.

* Add `ui.font.family` and `ui.font.size` as preferences.

* Added support for `0x` numbers to support alpha colors in `theme.txt`.


### Bug Fixes

* Code completion is fixed. Thanks [Sam](https://github.com/sampottinger)! [\#177](https://github.com/processing/processing4/issues/177), [\#219](https://github.com/processing/processing4/pull/219)

* `mouseButton` was not set correctly on `mouseReleased()` with Java2D. [\#181](https://github.com/processing/processing4/issues/181)

* A more useful message for the `NoClassDefError: processing/core/PApplet` startup error on Windows was in the alpha 5 source, but may not have made it into the actual release. [\#154](https://github.com/processing/processing4/issues/154)

* Fix `Module javafx.base not found` on Linux. Thanks [letorbi](https://github.com/letorbi). [\#214](https://github.com/processing/processing4/issues/214), [\#215](https://github.com/processing/processing4/pull/215)

* After selecting a font other than Source Code Pro, font went to a default. [\#216](https://github.com/processing/processing4/pull/216)

* `unregisterMethod()` was broken. [\#223](https://github.com/processing/processing4/issues/223)

* Fixed "No library found for org.w3c.dom" message when using that (built-in) package.


### Changes

* When changing to an incompatible Mode, just open a new window instead of giving the user a potentially confusing error message about it. [\#189](https://github.com/processing/processing4/issues/189)


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

* Moved doclet to separate repo. Thanks DSI! [\#218](https://github.com/processing/processing4/issues/218), [\#222](https://github.com/processing/processing4/pull/222)



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

* JavaFX is back and working again! This also fixes some Tools that relied on it, and Export to Application should be working as well. [\#110](https://github.com/processing/processing4/issues/110), [\#112](https://github.com/processing/processing4/pull/112), [\#3288](https://github.com/processing/processing/issues/3288), [\#209](https://github.com/processing/processing4/issues/209), [\#210](https://github.com/processing/processing4/issues/210)


### Other Fixes and Changes

* Major font cleanup inside Preferences. It appears that the “Monospace” font may be [missing or broken](https://www.oracle.com/java/technologies/javase/11-relnote-issues.html#JDK-8191522) with OpenJDK 11. Does it show a random sans serif for you? Let us know if it does. But aside from that, the Editor and Console font should be behaving a little more reliably.

* The Windows splash screen should no longer be tiny. Still sorting out some hidpi issues on Windows, but this one might make things a bit less quirky. [\#4896](https://github.com/processing/processing/issues/4896), [\#145](https://github.com/processing/processing4/issues/145)

* Better handling of `NoClassDefError: processing/core/PApplet` on startup with Windows 10. [\#154](https://github.com/processing/processing4/issues/154)

* No longer using `JFileChooser` on macOS; it's too awful. This means a Copy/Paste issue comes back, but the cure was worse than the disease. [\#1035](https://github.com/processing/processing/issues/1035), [\#77](https://github.com/processing/processing4/issues/77)


### Internal Changes

* The minimum system version for macOS (for the PDE and exported applications) is now set to 10.14.6 (the last update of Mojave). 10.13 (High Sierra) is no longer supported by Apple as of September or December 2020 (depending on what you read), and for our sanity, we're dropping it as well.

* Updated JNA from 5.7.0 to 5.8.0.

* Removed the `ant` binary from the repo, and updated the version we're using from 1.8.2 to 1.10.10.

* Rebuilt the `appbundler` tool for macOS to handle some recent changes, and disabled some logging chatter as well.

* Update to launch4j 3.14, fixing Export to Application on Windows.


# Processing 4.0 alpha 4

*Revision 1273 - 15 June 2021*

Happy birthday to my goddaughter Kelsey! Let's celebrate with another alpha release.

This should be a bit more stable than the last round. I've rolled back some of the more aggressive anti-AWT changes (bad for longevity, good for compatibility) so images in particular are now behaving better.

But enough of that, let's go to the phone lines:


### What bugs have been fixed; why should I care?

* Sketch window location is saved once again: re-running a sketch will open the window in the same location. This was broken for a while! [\#158](https://github.com/processing/processing4/issues/158), [\#5843](https://github.com/processing/processing/issues/5843), [\#5781](https://github.com/processing/processing/issues/5781)

* When using multiple monitors, new Editor windows will open on the same display as the most recently opened Editor window. [\#205](https://github.com/processing/processing4/issues/205), formerly [\#1566](https://github.com/processing/processing/issues/1566)

* A major Undo fix, this may even be [the big one](https://github.com/processing/processing/issues/4775), but it's not confirmed. (Please help confirm!) [\#175](https://github.com/processing/processing4/pull/175)


### Were you too hasty with exorcising AWT?

* `cursor(PImage)` broken everywhere because `PImage.getNative()` returns `null` [\#180](https://github.com/processing/processing4/issues/180)

* `PImage.resize()` not working properly. [\#200](https://github.com/processing/processing4/issues/200)

* `copy()` not working correctly. [\#169](https://github.com/processing/processing4/issues/169)


### Did you find any particularly niggling, but small issues?

* Catch `NoClassDefError` in `Platform.deleteFile()` (still unclear of its cause) on Big Sur. [\#159](https://github.com/processing/processing4/issues/159), [\#6185](https://github.com/processing/processing/issues/6185)

* Fixed `Exception in thread "Contribution Uninstaller" NullPointerException` when removing an installed contribution. [\#174](https://github.com/processing/processing4/issues/174)

* If the default display is selected in the Preferences window, store that, rather than its number. It was discovered that plugging in a second display could bump the “default” display to number 2, even while it was still selected. Yay!

* Sort out calling `unregisterMethod()` for `dispose` from `dispose()` makes for bad state situation. [\#199](https://github.com/processing/processing4/pull/199)


### How about contributions from the community?

+ Don't sort user's charset array when calling `createFont()`. [\#197](https://github.com/processing/processing4/issues/197), [\#198](https://github.com/processing/processing4/pull/198)

* Some exciting things are on the way for the documentation and web site. [\#191](https://github.com/processing/processing4/pull/191)

* Update Batik to 1.14. [\#179](https://github.com/processing/processing4/issues/179), [\#192](https://github.com/processing/processing4/issues/192), [\#183](https://github.com/processing/processing4/pull/183)

* Tweak the circle for number of updates based on Akarshit's initial attempt. [\#201](https://github.com/processing/processing4/issues/201), [\#4097](https://github.com/processing/processing/pull/4097)

* Make `parseJSONObject()` and `parseJSONArray()` return `null` when parsing fails. [\#165](https://github.com/processing/processing4/issues/165), [\#166](https://github.com/processing/processing4/pull/166)


### Is there anything new?

+ Added `PVector.setHeading()` for parity with p5.js. [\#193](https://github.com/processing/processing4/issues/193)

* The default font (what you get if `textFont()` isnot used) has been changed to Source Sans instead of Lucida Sans. I just couldn't take Lucida any longer.


### Were there any internal changes I probably won't notice?

* Updated to JDK 11.0.11+9

* Update from JNA 5.2.0 to 5.7.0

* Modernize the RegisteredMethods code to use collections classes w/ concurrency. [\#199](https://github.com/processing/processing4/pull/199)

* Set closed issues to [automatically lock](https://github.com/dessant/lock-threads) after they've been closed for 30 days. (This has no effect on open issues, only closed ones.) Actually this one you may have noticed if you had a lot of notifications turned on.

* Slowly transitioning some of the older code to newer syntax (lambda functions, etc). This is not a priority for anyone else: it's being done slowly, and as a chance to do code review on some very old work.


* Fix `textMode(SHAPE) is not supported by this renderer` message with SVG Export. [\#202](https://github.com/processing/processing4/issues/202), [\#6169](https://github.com/processing/processing/issues/6169)


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