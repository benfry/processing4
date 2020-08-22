# Processing 4.0

Processing 4 is an exciting next step for Processing in which the internals of the software will see important updates, helping prepare the platform for its future. This includes the move to JDK 11 and support for new Java language features. The changes should be transparent to most users, but because of the massive shift behind the scenes, this will be 4.0.


## API Changes

As with all releases, we'll do everything possible to avoid breaking API. However, there will still be tweaks that have to be made. We'll try to keep them minor. Our goal is stability, and keeping everyone's code running.


### alpha 2

* The `static` versions of `selectInput()`, `selectOutput()`, and `selectFolder()` in `PApplet` have been removed. These were not documented, hopefully were not in use anywhere.
* The `frame` object has been removed from `PApplet`. We've been warning folks to use `surface` since 2015, but we still should [warn users](https://github.com/processing/processing4/issues/59)
* `PImage.checkAlpha()` is now `public` instead of `protected`
* All AWT calls have been moved out of `PImage`, which may be a problem for anything that was relying on those internals
* Removed `MouseEvent.getClickCount()` and `MouseEvent.getAmount()`. These had been deprecated, not clear they were used anywhere.

### alpha 1

* `Base.defaultFileMenu` is now `protected` instead of `static public`


## Other Changes

### alpha 2

* The minimum system version for macOS (for the PDE and exported applications) is now set to 10.13.6 (the last update of High Sierra). Apple will likely be dropping support for High Sierra in late 2020, so we may make the minimum 10.14.x by the time 4.x ships.

### alpha 1

* Processing 4 will be 64-bit only. This is the overwhelming majority of users, and we don't have the necessary help to maintain and support 32-bit systems.
