JAVA_HOME=/c/Program\ Files/Eclipse\ Adoptium/jdk-17.0.1.12-hotspot

headers:
	${JAVA_HOME}/bin/javac -h . -d . Fenster.java
	#rm Fenster.class

default:
	#ls ${JAVA_HOME}
	g++ -c \
		-I${JAVA_HOME}/include \
	    -I${JAVA_HOME}/include/win32 \
	    processing_core_platform_Fenster.cpp \
	    -o processing_core_platform_Fenster.o
	#-mwindows -lmingw64 -lgdi64 \
	g++ -shared -o fenster.dll \
		processing_core_platform_Fenster.o \
		-lgdi32 \
        -Wl,--add-stdcall-alias

run:
	#${JAVA_HOME}/bin/javac -d . Fenster.java
	${JAVA_HOME}/bin/java -cp . processing.core.platform.Fenster
# 	java -cp . -Djava\.library\.path=/NATIVE_SHARED_LIB_FOLDER processing.core.platform.Fenster

fest:
	g++ fester.cpp -o fenster -lgdi32
