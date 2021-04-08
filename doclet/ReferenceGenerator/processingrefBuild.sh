#!/bin/sh

echo "[REFERENCE GENERATOR] Booting up..."

# PROCESSING_SRC_PATH=./test
PROCESSING_SRC_PATH=../../core/src
PROCESSING_LIB_PATH=../../java/libraries
REFERENCES_OUT_PATH=../../../processing-website/content/references/translations/en
# GENERATE REFERENCE ENTRIES AND INDEX THROUGH JAVADOC - BY DAVID WICKS

echo "[REFERENCE GENERATOR] Source Path :: $PROCESSING_SRC_PATH"
echo "[REFERENCE GENERATOR] Library Path :: $PROCESSING_LIB_PATH"

echo "[REFERENCE GENERATOR] Generating new javadocs..."
javadoc -doclet ProcessingWeblet \
        -docletpath "bin/:lib/org.json.jar" \
        -public \
	-webref ../../reference \
	-localref ../../distribution \
	-templatedir ../templates \
	-examplesdir ../../content/api_en \
	-includedir ../../content/api_en/include \
	-imagedir images \
	-encoding UTF-8 \
	$PROCESSING_SRC_PATH/processing/core/*.java \
	$PROCESSING_SRC_PATH/processing/data/*.java \
	$PROCESSING_SRC_PATH/processing/event/*.java \
	$PROCESSING_SRC_PATH/processing/opengl/*.java \
	$PROCESSING_LIB_PATH/io/src/processing/io/*.java \
	$PROCESSING_LIB_PATH/net/src/processing/net/*.java \
	$PROCESSING_LIB_PATH/serial/src/processing/serial/*.java \
	$PROCESSING_LIB_PATH/../../../processing-video/src/processing/video/*.java \
	$PROCESSING_LIB_PATH/../../../processing-sound/src/processing/sound/*.java \
	-noisy