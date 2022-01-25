#!/usr/bin/make


default:
	JAVA_HOME="`/usr/libexec/java_home -v 11`"
	gcc -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin/ \
		-arch x86_64 -arch arm64 \
	    -o libjAppleMenuBar.jnilib -shared jAppleMenuBar.m
