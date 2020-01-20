Processing 4
==============================
4.0 is an exciting next step for Processing in which the internals of the software will see important updates, helping prepare the platform for its future. This includes the move to JDK 11 and support for new Java language features. The changes should be transparent to most users, but because of the massive shift behind the scenes, this will be 4.0.

<br>

Note from Ben
------------------------------
I'm working with [Sam Pottinger](https://github.com/sampottinger) to incorporate his changes to see if that can be the basis for this new release. Getting things moved to OpenJDK 11 will help the longevity of the project.

It's not clear if we'll ship an actual Processing 4.0, because I have less free time than ever, and very little development help. If you'd like to help, contribute bug fixes.

Ben Fry, 4 October 2019

<br>

API changes
------------------------------
As with all releases, I'll do everything possible to avoid breaking API. However, there will still be tweaks that have to be made. We'll try to keep them minor.

### alpha 2

* The `static` versions of `selectInput()`, `selectOutput()`, and `selectFolder() in `PApplet` have been removed. These were not documented, hopefully were not in use anywhere.
* The `frame` object has been removed from `PApplet`. We've been warning folks to use `surface` since 2015, but we still should [warn users](https://github.com/processing/processing4/issues/59)
* `PImage.checkAlpha()` is now `public` instead of `protected`
* All AWT calls have been moved out of `PImage`, which may be a problem for anything that was relying on those internals
* Removed `MouseEvent.getClickCount()` and `MouseEvent.getAmount()`. These had been deprecated, not clear they were used anywhere.


### alpha 1 

* `Base.defaultFileMenu` is now `protected` instead of `static public`
