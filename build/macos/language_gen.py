#!/usr/bin/env python3

import os, re

HERE = os.path.dirname(os.path.realpath(__file__))


def supported_languages():
    outgoing = set()
    translations_path = os.path.join(HERE, '../shared/lib/languages')
    with os.scandir(translations_path) as it:
        for entry in it:
            if entry.name.startswith('PDE_') and entry.name.endswith('.properties'):
                outgoing.add(entry.name[4:-11])
    return outgoing


def lproj_directory(lang):
    path = "work/Processing.app/Contents/Resources/{}.lproj".format(lang)
    return os.path.join(HERE, path)


if __name__ == "__main__":
    for lang in supported_languages():
        path = lproj_directory(lang)
        if not os.path.exists(path):
            os.makedirs(path)
