#!/bin/sh
#remove everything old
rm -rf ../tmp
#generate everything anew
javadoc -doclet ProcessingWeblet -docletpath bin/ -public \
	-webref ../tmp/web-android \
	-localref ../tmp/local-android \
	-includeXMLTag android \
	-templatedir ../templates \
	-examplesdir ../api_examples \
	-includedir ../api_examples/include \
	-imagedir images \
	-corepackage processing.xml \
	-rootclass PGraphics \
	-rootclass PConstants \
    ../../../processing/android/core/src/processing/android/core/*.java \
	../../../processing/android/core/src/processing/android/xml/*.java \
    ../../../processing/net/src/processing/net/*.java \
	../../../processing/serial/src/processing/serial/*.java
    # ../../../processing/video/src/processing/video/*.java \

#copy over the css and sample images
cp -r ../../css	 ../tmp/web-android
cp -r ../../css	 ../tmp/local-android
mkdir ../tmp/web-android/images
mkdir ../tmp/local-android/images
cp -r ../../content/api_media/*.jpg ../tmp/web-android/images/
cp -r ../../content/api_media/*.gif ../tmp/web-android/images/
cp -r ../../content/api_media/*.png ../tmp/web-android/images/
cp -r ../../content/api_media/*.jpg ../tmp/local-android/images/
cp -r ../../content/api_media/*.gif ../tmp/local-android/images/
cp -r ../../content/api_media/*.png ../tmp/local-android/images/