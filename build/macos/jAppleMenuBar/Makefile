#!/usr/bin/make

CC=gcc
JAVA_HOME="`/usr/libexec/java_home -v 11`"
JNI=-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/
# JNI11=-I`/usr/libexec/java_home -v 11`/include -I`/usr/libexec/java_home -v 11`/include/darwin/
# JNI17=-I`/usr/libexec/java_home -v 17`/include -I`/usr/libexec/java_home -v 17`/include/darwin/

x64_lib: jAppleMenuBar.m
	$(CC) ${JNI} jAppleMenuBar.m -o x64_lib -arch x86_64 -target x86_64-apple-macos10.12

arm_lib: jAppleMenuBar.m
	$(CC) ${JNI} jAppleMenuBar.m -o arm_lib -arch arm64 -target arm64-apple-macos11

universal_lib: x64_lib arm_lib
	lipo -create -output libjAppleMenuBar.jnilib x64_lib arm_lib

default: universal_lib

# 	JAVA_HOME="`/usr/libexec/java_home -v 11`"
# 	gcc -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/ \
# 		-arch x86_64 -arch arm64 \
# 	    -o libjAppleMenuBar.jnilib -shared jAppleMenuBar.m
