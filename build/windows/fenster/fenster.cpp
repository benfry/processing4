#include <stdio.h>
#include <windows.h>


int main() {
  // MinGW could not find this variant. There's probably a #define or
  // another arg to make it work, but the Vista-era version is working fine.
  //SetProcessDpiAwareness(PROCESS_SYSTEM_DPI_AWARE);  // Windows 8.1 and later

  // https://docs.microsoft.com/en-us/windows/win32/hidpi/setting-the-default-dpi-awareness-for-a-process
  SetProcessDPIAware();  // Windows Vista and later

  // https://docs.microsoft.com/en-us/windows-hardware/manufacture/desktop/dpi-related-apis-and-registry-settings
  // also done in the JDK https://hg.openjdk.java.net/jdk/jdk/rev/b7a958df3992
  HDC hdc = GetDC(NULL);
  if (hdc) {
    // https://learn.microsoft.com/en-us/windows/win32/api/wingdi/nf-wingdi-getdevicecaps
    INT horizontalDPI = GetDeviceCaps(hdc, LOGPIXELSX);
    INT verticalDPI = GetDeviceCaps(hdc, LOGPIXELSY);
    //printf("%d %d", horizontalDPI, verticalDPI);
    // god help us if horizontal != vertical
    printf("%d", horizontalDPI);
    // printf("\n%d", sizeof(INT));
    ReleaseDC(NULL, hdc);
    return 0;
  }
  return 1;
}
