# Fenster

This is code to detect the current settings for Windows scaling (Display Settings → Scale and layout → Change the size of text, apps, and other items.)

Generally speaking, this is a nightmare to deal with. The solution, starting in Processing 4.0 beta 6 has two parts:

* When running from the PDE, pass the `--ui-scale` parameter to `PApplet` (see the code for `processing.mode.java.Runner`) based on what comes back from `Toolkit.getScreenResolution()`.
* When running independently, check for that parameter, and if it's not set, launch a tiny helper app that just returns the DPI value. 

With those values in hand, the sketch sets the `sun.java2d.uiScale` to either 1 or 2. Using fractional values produces [ugly results](https://github.com/processing/processing4/issues/378). Similarly, we do not set uiScale to 3 when scaling is at 300%. If you want larger sketches, use `scale()` in your code.


## Using AWT

The [`Toolkit.getScreenResolution()`](https://docs.oracle.com/javase/8/docs/api/java/awt/Toolkit.html#getScreenResolution--) method does what we want, but it's not possible to set the property for sun.java2d.uiScale once AWT calls have been made.


## Use a helper application

This is the solution we're using, which feels a little fragile, but it's working. No need to extract files, adds only 8 Kb, if it fails it doesn't bring down the entire app. (See below for downsides with the other approaches.)

This was done by first doing the JNI setup with MSYS2, and then adding a line to the `Makefile` to just create the tiny `.exe`.


## Use JNI

This was the intended approach, however the result works 3 times in 5, has an immediate `segmentation fault` another 20% of the time, and the rest of the time just hangs completely until a force quit. If anyone can fix it, [let us know](https://github.com/processing/processing4/issues).

* Used this really helpful tutorial for JNI
    * <https://www.baeldung.com/jni>


### Building the JNI code

* Install MSYS2 from <https://www.msys2.org/> via <https://www.mingw-w64.org/downloads/#msys2>

* Within an MSYS shell, run updates and install `gcc`

        pacman -Syu
        pacman -S base-devel gcc

* To get your Windows `PATH` to work (i.e. to find Java), you'll probably also need:

        MSYS2_PATH_TYPE=inherit

* Had to edit `$JAVA_HOME/include/win32/jni_md.h` to modify the definition of `jlong`
    * <https://stackoverflow.com/a/51775636>
    * other approaches for it <https://gist.github.com/ssfang/e52c00cd446081cd72a982a2dd8634d4#file-readme-md> (section under “jni with cygwin gcc”)

* To cross compile on macOS, use:

        brew install mingw-w64

    This installs `g++` as `x86_64-w64-mingw32-g++`. 
    
    Did not use this approach because it would require Windows testing anyway, so it has limited utility.
    
    A link to the formula: <https://formulae.brew.sh/formula/mingw-w64>


### Windows Reference

Resources for the necessary API calls on Windows

* GetDeviceCaps function (wingdi.h)
    * <https://docs.microsoft.com/en-us/windows/win32/api/wingdi/nf-wingdi-getdevicecaps>
* DPI-related APIs and registry settings
    * <https://docs.microsoft.com/en-us/windows-hardware/manufacture/desktop/dpi-related-apis-and-registry-settings>
* Browse code samples for “dpi”
    * <https://docs.microsoft.com/en-us/samples/browse/?redirectedfrom=TechNet-Gallery&terms=dpi>

The code boils down to:

```c
hdc = GetDC(NULL);
horizontalDPI = GetDeviceCaps(hdc, LOGPIXELSX);
verticalDPI = GetDeviceCaps(hdc, LOGPIXELSY);
```


## Use JNA

This is a fairly clean approach with a couple major downsides. One is adding 3 MB of JARs to each application. That is larger than all of `processing.core`, but you could make an argument that core is already large because of JOGL. 

The more serious problems are:

* Unpacking the native libraries for JNA on Windows is notoriously finicky. It can kick off the virus checker with unpredictable results—either refusing to run, or delaying startup by a full 60 seconds, or other confusing behaviors.

* JNA version conflicts are a nightmare. If JNA is used by the sketch, or a library it depends on, you'll have a major headache on your hands.

That said, this approach is possible. Here's working code:

```java
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

WinDef.HDC desktopDc = User32.INSTANCE.GetDC(null);

// if we want to add error handling later
//if (desktopDc == null) {
//  throw new Win32Exception(Native.getLastError());

//INT horizontalDPI = GetDeviceCaps(desktopDc, LOGPIXELSX);
//INT verticalDPI = GetDeviceCaps(desktopDc, LOGPIXELSY);
int x = GDI32.INSTANCE.GetDeviceCaps(desktopDc, 88);
int y = GDI32.INSTANCE.GetDeviceCaps(desktopDc, 90);
println(x, y);
println("scaling: " + x / 96f);
```

Constants were pulled from <https://github.com/tpn/winsdk-7/blob/master/v7.1A/Include/WinGDI.h>

    #define LOGPIXELSX    88    /* Logical pixels/inch in X                 */
    #define LOGPIXELSY    90    /* Logical pixels/inch in Y                 */

Based in part on [this gist](https://gist.github.com/tresf/00a8ed7c9860e3bd73cebf764a49789f), but rewritten to use the default device rather than first creating a device.


## Use a Registry Key

This would be a simple method to make a single command line call to `reg query` or similar, but was unable to find a suitable property that was reliable enough. 

It's also possible that calling `reg query` would kick off User Access Control headaches, but that was not confirmed.

* `reg query` reference
    * <https://docs.microsoft.com/en-us/windows-server/administration/windows-commands/reg-query>

* returns `0xC0` (192, or 200%) on my machine even though it's set at 225%
    * `Reg Query "HKCU\Control Panel\Desktop" /v LogPixels`
    * though looking at this now, if this is gonna return 1x or 2x, it could be an option

* iterating through monitors
    * `HKEY_LOCAL_MACHINE\System\CurrentControlSet\Control\GraphicsDrivers\ScaleFactors\%MonitorID%`
    * `HKEY_CURRENT_USER\Control Panel\Desktop\PerMonitorSettings\%MonitorID%`

* how to *set* the dpi scale (includes iterating through monitors)
    * <https://gist.github.com/itsho/cc4f0c66d3283a6b54582fde31b70a26>

* DPI-related APIs and registry settings
    * <https://docs.microsoft.com/en-us/windows-hardware/manufacture/desktop/dpi-related-apis-and-registry-settings?view=windows-11>


## Other Resources

* A longer explanation of some of the issues in play that might be helpful for someone, though I didn't use it:
    * <https://mariusbancila.ro/blog/2021/05/19/how-to-build-high-dpi-aware-native-desktop-applications/>
