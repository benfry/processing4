Processing 4
==============================
Processing 4 is an exciting next step for Processing in which the internals of the software will see important updates, helping prepare the platform for its future. This includes the move to JDK 11 and support for new Java language features. There are likely going to be major API-breaking changes in addition to Java 11 so this release would be Processing 4.

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

* `Base.defaultFileMenu` is now `protected` instead of `static public`
