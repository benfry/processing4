#!/usr/bin/make

CC=gcc
JAVA_HOME="`/usr/libexec/java_home -v 17`"
JNI=-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/

default: universal_lib

x64_lib: different.m
	$(CC) ${JNI} different.m -o libDifferent_x86_64 -target x86_64-apple-macos10.12 -framework Cocoa -shared

arm_lib: different.m
	$(CC) ${JNI} different.m -o libDifferent_aarch64 -target arm64-apple-macos11 -framework Cocoa -shared

# 'strip' throws errors, but compresses down to 2 Kb, so no big deal
universal_lib: x64_lib arm_lib
	lipo -create -output ../src/processing/core/libDifferent.jnilib libDifferent_x86_64 libDifferent_aarch64
