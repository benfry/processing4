# Processing 4.0 alpha 2

*Revision 1271 - 17 August 2020*

Several fixes for this round, plus working on the guts quite a bit to prepare for newer/faster/better rendering methods.


### Bug Fixes

* Break buildMenu() into populateMenu() method to delay Debugger init [73](https://github.com/processing/processing4/issues/73)
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