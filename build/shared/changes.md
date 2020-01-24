# Processing 4.0 alpha 1

*Revision 1270 - 18 January 2019*

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