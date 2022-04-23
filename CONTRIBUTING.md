## Welcome to Processing!

## Bug Report?

We have a page on [troubleshooting](https://github.com/processing/processing/wiki/Troubleshooting) common problems. Check there first!

We also host an [online forum](https://discourse.processing.org/) for coding questions, which is also helpful for general “getting started” queries.

If you don't find an answer, please let us know by [filing an issue](https://github.com/processing/processing4/issues). We can only fix the things we've heard about!

Also, please keep the tone polite. This project is volunteer work done in our free time. We give it away at no cost. We do this because we think it’s important for the community, and enjoy working on it. Complaints that things *suck*, or are *annoying*, or lectures about things that *must* be fixed are… weird to hear from total strangers (at best), and demotivating (at worst).


## Want to Help?

Great! The number of contributors on this project is *tiny*, especially relative to the number of users. There are [only one or two people](https://github.com/processing/processing4/graphs/contributors) who actively work on this repository, for instance. We need help!

How to start:

**Help Wanted** – Most [issues marked help wanted](https://github.com/processing/processing4/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22) are a good place to start. Issues are marked with this tag when:

* They are isolated enough that someone can jump into it without significant reworking of other code.
* Ben knows that it's unlikely that he'll have time to work on them.

**The Old Repository** – There are also many “help wanted” [issues in the 3.x repository](https://github.com/processing/processing4/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22). Some of these are very old, so it may be a good idea to check in about the priority before putting in too much work!

**JavaFX** – There are several [active issues](https://github.com/processing/processing4-javafx/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc) for the JavaFX renderer as well.

**The `todo.txt` File** – This is Ben's todo list, and dates back to the very start of the project over twenty years ago. It shouldn't be used as a guideline for work to be done, because there are lots of things there that are no longer relevant. Consider it the dusty attic of what's inside his head. If you see something of interest there, open an issue to see if it's still a priority, or how it should be approached. But also, there are *so many open issues*, which represent actual problems identified by community members. Those are by far the best place to start.

**Style Guidelines** – Keep the [style guidelines](https://github.com/processing/processing/wiki/Style-Guidelines) in mind when submitting pull requests. If you don't, someone else will have to reformat your code so that it fits everything else (or we'll have to reject it if it'll take us too long to clean it up).

**Larger Projects** – If you're looking for a larger project, check out the [Project List](https://github.com/processing/processing/wiki/Project-List#processing) for other ideas.


## New Features

Nearly all new features are first introduced as a Library or a Mode, or even as an example. The current [OpenGL renderer](http://glgraphics.sourceforge.net/) and Video library began as separate projects by Andrés Colubri, who needed a more performant, more sophisticated version of what we had in Processing for work that he was creating. The original `loadShape()` implementation came from the “Candy” library by Michael Chang (“mflux“).

Similarly, Tweak Mode began as a [separate project](http://galsasson.com/tweakmode/) by Gal Sasson before being incorporated. PDE X was a Google Summer of code [project](https://github.com/processing/processing-experimental) by Manindra Moharana that updated the PDE to include basic refactoring and better error checking.

Developing features separately from the main software has several benefits:

* It's easier for the contributor to develop the software without it needing to work for tens of thousands of Processing users.
* It provides a way to get feedback on that code independently of everything else, and iterating on it rapidly.
* This feedback process also helps gauge the level of interest for the community, and how it should be prioritized for the software.
* We can delay the process of “normalizing” the features so that they're consistent with the rest of Processing (function naming, structure, etc).

A major consideration for any new feature is the level of maintenance that it might require in the future. If the original maintainer loses interest over time (which is normal), any ongoing work usually falls to Ben, or it sits on the issues list unfixed, which isn't good for the community, or for Ben, who has plenty of issues of his own—whether Processing or otherwise.

Processing is a massive project that has existed for more than 20 years. Part of its longevity comes from the effort that's gone into keeping things as simple as we can, and making a lot of decisions about *what to leave out*. Adding a new feature always has to be weighed against the potential confusion of one more thing—whether it's a menu item, a dialog box, a function that needs to be added to the reference, etc. Adding a new graphics function means making it work across all the renderers that we ship (Java2D, OpenGL, JavaFX, PDF, etc) and across platforms (macOS, Windows, Linux).


## Refactoring

Refactoring is fun! There's always more cleaning to do. It's also often not very helpful.

Broadly speaking, the code is built the way it is for a reason. There are so many things that can be improved, but those improvements need to come from an understanding of what's been built so far. Changes that include refactoring are typically only accepted from contributors who have an established record of working on the code. With a better understanding of the software, the refactoring decisions come from a better, more useful place.


## Other Details

This document was hastily thrown together in an attempt to improve the bug reporting and development/contribution process. It doesn't yet include detail about our intent with the project, the community behind it, our values, and an explanation of how the code itself is designed.
