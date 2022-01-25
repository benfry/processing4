#!/usr/bin/make

CC=gcc
JAVA_HOME="`/usr/libexec/java_home -v 11`"
JNI=-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/
# JNI11=-I`/usr/libexec/java_home -v 11`/include -I`/usr/libexec/java_home -v 11`/include/darwin/
# JNI17=-I`/usr/libexec/java_home -v 17`/include -I`/usr/libexec/java_home -v 17`/include/darwin/

default: universal_lib

x64_lib: jAppleMenuBar.m
	$(CC) ${JNI} jAppleMenuBar.m -o libjAppleMenuBar_x86_64 -target x86_64-apple-macos10.12 -framework Cocoa -shared

arm_lib: jAppleMenuBar.m
	$(CC) ${JNI} jAppleMenuBar.m -o libjAppleMenuBar_aarch64 -target arm64-apple-macos11 -framework Cocoa -shared

universal_lib: x64_lib arm_lib
	lipo -create -output libjAppleMenuBar.jnilib libjAppleMenuBar_x86_64 libjAppleMenuBar_aarch64

# 	JAVA_HOME="`/usr/libexec/java_home -v 11`"
# 	gcc -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/ \
# 		-arch x86_64 -arch arm64 \
# 	    -o libjAppleMenuBar.jnilib -shared jAppleMenuBar.m
