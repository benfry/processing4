JAVA_HOME=/c/Program\ Files/Eclipse\ Adoptium/jdk-17.0.1.12-hotspot


# Create the .exe version, now the default because of JNI crashing.
# -s also strips the binary so the output is 8 instead of 64 Kb.
app:
	g++ fenster.cpp -o fenster -lgdi32 -s


# Create the JNI library and test code.
jni:
	${JAVA_HOME}/bin/javac -h . -d . Fenster.java
	g++ -c \
		-I${JAVA_HOME}/include \
	    -I${JAVA_HOME}/include/win32 \
	    processing_core_platform_Fenster.cpp \
	    -o processing_core_platform_Fenster.o
	g++ -shared -o fenster.dll \
		processing_core_platform_Fenster.o \
		-lgdi32 \
        -Wl,--add-stdcall-alias


# Run the JNI version using its main() method.
run:
	${JAVA_HOME}/bin/java -cp . processing.core.platform.Fenster
