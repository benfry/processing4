Native code to go with the `ThinkDifferent` class which handles macOS-specific API calls.

Currently only handles hiding the menu bar, and the basic structure of the native libraries evolved from the [jAppleMenuBar project](https://github.com/kritzikratzi/jAppleMenuBar) by Hansi Raber and was rewritten for Processing 4 by Ben Fry.

Helpful reference used for the rewrite:

* JNI Example (Mac OS)
    * <https://gist.github.com/DmitrySoshnikov/8b1599a5197b5469c8cc07025f600fdb>
* Building a Universal macOS Binary
    * <https://developer.apple.com/documentation/apple-silicon/building-a-universal-macos-binary>
