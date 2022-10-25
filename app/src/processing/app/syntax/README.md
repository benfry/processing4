# 🐉 Fixing this code: here be dragons. 🐉

Every few years, we've looked at replacing this package with [RSyntaxArea](https://github.com/bobbylight/RSyntaxTextArea), most recently with two attempts during the course of developing [Processing 4](https://github.com/processing/processing4/wiki/Processing-4), but probably dating back to the mid-2000s.

The bottom line is that the time is better spent [pursuing a different approach](https://github.com/processing/processing4/blob/master/CONTRIBUTING.md#editor)—a Language Server implementation and probably a lightweight (HTML/JS) GUI on top of it. But so that I don't attempt this again, some reminders as to why it's not worth the effort:

* At a minimum, replacing this text component would break all Modes, because of how they're invoked. That places significant burden on those authors, so making the switch means there must be major, demonstrable improvements. (The code being “cleaner” or “better” does not qualify, though other improvements might.)
* The token coloring uses a completely different system, which would also need to be expanded across Modes. 
* Everything that lives in `PdeTextAreaPainter`,  the error squiggles, popups, etc would need to be rewritten. 
* Similarly, line numbering is supported by default in `RSyntaxArea` so we'd need to carefully remove all our “left gutter” hacking.
* Most methods throw `BadLocationException`, which we'd either need to include, and break more existing code, or hide, and have undefined behavior. Not a good investment.
* The current `Editor` object evolved from a time when we didn't even support individual tabs. As a result, there's a lot of “current tab” state that still lives inside `Editor`, and other state that lives in `SketchCode`. 
* Most of those `Editor` methods should instead be talking to `SketchCode` objects, however that kind of change is likely to introduce small regressions with *major* effects. Again, just not worth it.
* More ways to introduce regressions when fixing: `SketchCode` currently syncs between `program`, `savedProgram`, and `Document` objects. This can obviously be collapsed to just one object (maaaybe two), but this is Regression City. The stuff of nightmares. 
* The text area object needs to be moved into the individual tabs (and out of `SketchCode`. This would be fantastic for cleaning things up (the Arduino folks moved ahead with this change a while back). But it's another breaking change for Modes, even though it would be necessary to cleanly separate all Undo behavior.
* So many small quirks, hard-learned lessons from over the years that may no longer be necessary, but the amount of testing necessary is too significant. For instance, inside File → Print, all the Document objects for the tabs are synched up. This might no longer be necessary if we do this properly—it's a gross hack—but we don't have time to find out. There are dozens of situations like this, creating a “refactored but not renewed” sort of situation that would likely take longer than the LS implementation.

I don't enjoy having the code in this state, but it's there and working, and has allowed a single primary maintainer to support millions of users over more than 20 years. A [larger-scale replacement](https://github.com/processing/processing4/blob/master/CONTRIBUTING.md#editor) is a better use of time.

— Ben Fry, 20 January 2022


# License

```
OLDSYNTAX PACKAGE README

I am placing the jEdit 2.2.1 syntax highlighting package in the public
domain. This means it can be integrated into commercial programs, etc.

This package requires at least Java 1.1 and Swing 1.1. Syntax
highlighting for the following file types is supported:

- C++, C
- CORBA IDL
- Eiffel
- HTML
- Java
- Java properties
- JavaScript
- MS-DOS INI
- MS-DOS batch files
- Makefile
- PHP
- Perl
- Python
- TeX
- Transact-SQL
- Unix patch/diff
- Unix shell script
- XML

This package is undocumented; read the source (start by taking a look at
JEditTextArea.java) to find out how to use it; it's really simple. Feel
free to e-mail questions, queries, etc. to me, but keep in mind that
this code is very old and I no longer maintain it. So if you find a bug,
don't bother me about it; fix it yourself.

* Copyright

The jEdit 2.2.1 syntax highlighting package contains code that is
Copyright 1998-1999 Slava Pestov, Artur Biesiadowski, Clancy Malcolm,
Jonathan Revusky, Juha Lindfors and Mike Dillon.

You may use and modify this package for any purpose. Redistribution is
permitted, in both source and binary form, provided that this notice
remains intact in all source distributions of this package.

-- Slava Pestov
25 September 2000
<sp@gjt.org>
```
