#!/bin/sh

echo "[REFERENCE GENERATOR] Booting up..."

# PROCESSING_SRC_PATH=./test
PROCESSING_SRC_PATH=../../core/src
PROCESSING_LIB_PATH=../../java/libraries
REFERENCES_OUT_PATH=../../../processing-website/content/references/translations/en
# GENERATE REFERENCE ENTRIES AND INDEX THROUGH JAVADOC - BY DAVID WICKS

echo "[REFERENCE GENERATOR] Source Path :: $PROCESSING_SRC_PATH"
echo "[REFERENCE GENERATOR] Library Path :: $PROCESSING_LIB_PATH"

# You can pass one argument "sound" or "video" to generate those libraries separately
# if there is no argument it will generate everything
if [ $# -eq 0 ]
  then
    echo "No arguments supplied, generating everything"
    echo "[REFERENCE GENERATOR] Removing previous version of the ref..."
	  rm -rf $REFERENCES_OUT_PATH
	  mkdir $REFERENCES_OUT_PATH
	  mkdir $REFERENCES_OUT_PATH/processing
	  mkdir $REFERENCES_OUT_PATH/io
	  mkdir $REFERENCES_OUT_PATH/net
	  mkdir $REFERENCES_OUT_PATH/serial
	  mkdir $REFERENCES_OUT_PATH/sound
	  mkdir $REFERENCES_OUT_PATH/video
    FOLDERS="$PROCESSING_SRC_PATH/processing/core/*.java \
    			$PROCESSING_SRC_PATH/processing/data/*.java \
    			$PROCESSING_SRC_PATH/processing/event/*.java \
    			$PROCESSING_SRC_PATH/processing/opengl/*.java \
    			$PROCESSING_LIB_PATH/io/src/processing/io/*.java \
    			$PROCESSING_LIB_PATH/net/src/processing/net/*.java \
    			$PROCESSING_LIB_PATH/serial/src/processing/serial/*.java \
    			$PROCESSING_LIB_PATH/../../../processing-video/src/processing/video/*.java \
    			$PROCESSING_LIB_PATH/../../../processing-sound/src/processing/sound/*.java"
  elif [ $1 = "processing" ]
  then
    echo "Generating processing references"
    echo "[REFERENCE GENERATOR] Removing previous version of the ref..."
    rm -rf $REFERENCES_OUT_PATH/processing
    rm -rf $REFERENCES_OUT_PATH/io
    rm -rf $REFERENCES_OUT_PATH/net
    rm -rf $REFERENCES_OUT_PATH/serial
    mkdir $REFERENCES_OUT_PATH/processing
    mkdir $REFERENCES_OUT_PATH/io
    mkdir $REFERENCES_OUT_PATH/net
    mkdir $REFERENCES_OUT_PATH/serial
    FOLDERS="$PROCESSING_SRC_PATH/processing/core/*.java \
          $PROCESSING_SRC_PATH/processing/data/*.java \
          $PROCESSING_SRC_PATH/processing/event/*.java \
          $PROCESSING_SRC_PATH/processing/opengl/*.java \
          $PROCESSING_LIB_PATH/io/src/processing/io/*.java \
          $PROCESSING_LIB_PATH/net/src/processing/net/*.java \
          $PROCESSING_LIB_PATH/serial/src/processing/serial/*.java"
  else
  	echo "Generating $1 library"
  	echo "[REFERENCE GENERATOR] Removing previous version of the ref..."
  	rm -rf $REFERENCES_OUT_PATH/$1
  	mkdir $REFERENCES_OUT_PATH/$1
  	FOLDERS="$PROCESSING_LIB_PATH/../../../processing-$1/src/processing/$1/*.java"
fi

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
	$FOLDERS \
	-noisy
