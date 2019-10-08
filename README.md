This is an experimental fork to attempt the move to JDK 11. Because that's a major, API-breaking change, it would be Processing 4.

I'm working with Sam Pottinger to incorporate [his changes](https://github.com/sampottinger/processing) to see if that can be the basis for this new release. Getting things moved to OpenJDK 11 will help the longevity of the project.

**It's not clear if we'll ship an actual Processing 4.0**, since I have less free time than ever, and very little development help. If you'd like to help, contribute bug fixes.

Ben Fry, 4 October 2019

---

## API changes

As with all releases, I'll do everything possible to avoid breaking API. However, there will still be tweaks that have to be made. We'll try to keep them minor.

* `Base.defaultFileMenu` is now `protected` instead of `static public`