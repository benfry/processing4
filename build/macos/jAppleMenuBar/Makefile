#!/usr/bin/make

# Helpful information used to create this code
# https://gist.github.com/DmitrySoshnikov/8b1599a5197b5469c8cc07025f600fdb
# https://developer.apple.com/documentation/apple-silicon/building-a-universal-macos-binary

CC=gcc
JAVA_HOME="`/usr/libexec/java_home -v 11`"
JNI=-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/

default: universal_lib

x64_lib: jAppleMenuBar.m
	$(CC) ${JNI} jAppleMenuBar.m -o libjAppleMenuBar_x86_64 -target x86_64-apple-macos10.12 -framework Cocoa -shared

arm_lib: jAppleMenuBar.m
	$(CC) ${JNI} jAppleMenuBar.m -o libjAppleMenuBar_aarch64 -target arm64-apple-macos11 -framework Cocoa -shared

universal_lib: x64_lib arm_lib
	lipo -create -output libjAppleMenuBar.jnilib libjAppleMenuBar_x86_64 libjAppleMenuBar_aarch64
