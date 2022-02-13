#include "processing_core_platform_Fenster.h"

// only for the std::cout debugging stuff / remove later
//#include <ostream>
//#include <iostream>

#include <windows.h>

// had to manually modify JDK_HOME/include/win32/jni_md.h to change
// typedef __int64 jlong -> __int64_t
// was also necessary to modify the permissions of the file in Windows


JNIEXPORT void JNICALL Java_processing_core_platform_Fenster_sayHello
  (JNIEnv *, jobject) {
    //std::cout << "Well at least this part is working" << std::endl;
  }

JNIEXPORT jint JNICALL Java_processing_core_platform_Fenster_getLogPixels
  (JNIEnv *, jobject) {
    // https://docs.microsoft.com/en-us/windows-hardware/manufacture/desktop/dpi-related-apis-and-registry-settings
    // also done in the JDK https://hg.openjdk.java.net/jdk/jdk/rev/b7a958df3992
    HDC hdc = GetDC(NULL);
    if (hdc) {
      INT horizontalDPI = GetDeviceCaps(hdc, LOGPIXELSX);
      ReleaseDC(NULL, hdc);
      // INT verticalDPI = GetDeviceCaps(desktopDc, LOGPIXELSY);
      // god help us if horizontal != vertical
      return (jint) horizontalDPI;
    }
    return 0;
  }
