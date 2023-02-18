JAVA_HOME=/c/Program\ Files/Eclipse\ Adoptium/jdk-17.0.1.12-hotspot

#CPP=g++
# using mingw64 under Cygwin because msys2 requires msys-2.0.dll
CPP=x86_64-w64-mingw32-g++

# Create the .exe version, now the default because of JNI crashing.
# -s also strips the binary so the output is 8 instead of 64 Kb.
app:
	$(CPP) fenster.cpp -o fenster -lgdi32 -s


# Create the JNI library and test code.
jni:
	${JAVA_HOME}/bin/javac -h . -d . Fenster.java
	$(CPP) -c \
		-I${JAVA_HOME}/include \
	    -I${JAVA_HOME}/include/win32 \
	    processing_core_platform_Fenster.cpp \
	    -o processing_core_platform_Fenster.o
	$(CPP) -shared -o fenster.dll \
		processing_core_platform_Fenster.o \
		-lgdi32 \
        -Wl,--add-stdcall-alias


# Run the JNI version using its main() method.
run:
	${JAVA_HOME}/bin/java -cp . processing.core.platform.Fenster
