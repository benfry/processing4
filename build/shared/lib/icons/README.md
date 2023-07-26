This folder contains several icons, so we'll use that as an opportunity to outline where *all* the icons are located in the repo. 

Image2Icon.app on macOS is used to create the `.icns` and `.ico` files from the Illustrator artwork that's been exported to `.png`. It's also used to create `.iconset` folders that contain the icons at several sizes. These are (manually) renamed to the versions seen below.


## in this folder

Application icon for the PDE, used for the dock and windows:

    build/shared/lib/icons/app-16.png
    build/shared/lib/icons/app-32.png
    build/shared/lib/icons/app-48.png
    build/shared/lib/icons/app-64.png
    build/shared/lib/icons/app-128.png
    build/shared/lib/icons/app-256.png
    build/shared/lib/icons/app-512.png
    build/shared/lib/icons/app-1024.png

The 48x48 version is created from the 64x64 image using ImageMagick:

    convert -resize 48x app-64.png app-48.png
    
The document icon for `.pde` files. (Prior to 4.3, the `pde-NN.png` files were the app icon, causing some confusion.) These images are only used on Linux:

    build/shared/lib/icons/pde-16.png
    build/shared/lib/icons/pde-32.png
    build/shared/lib/icons/pde-48.png
    build/shared/lib/icons/pde-64.png
    build/shared/lib/icons/pde-128.png
    build/shared/lib/icons/pde-256.png
    build/shared/lib/icons/pde-512.png
    build/shared/lib/icons/pde-1024.png

And to create that 48x48 version:

    convert -resize 48x pde-64.png pde-48.png

Finally, there's the Foundation logo, which is used by the Contributions Manager:

    build/shared/lib/icons/foundation-16.png
    build/shared/lib/icons/foundation-32.png
    build/shared/lib/icons/foundation-64.png


## inside `core`

The exported application icon, used to set the dock icon when running a sketch, or in an exported application:

    core/src/icon/icon-16.png
    core/src/icon/icon-32.png
    core/src/icon/icon-48.png
    core/src/icon/icon-64.png
    core/src/icon/icon-128.png
    core/src/icon/icon-256.png
    core/src/icon/icon-512.png
    core/src/icon/icon-1024.png

And of course:

    convert -resize 48x icon-64.png icon-48.png


## exported applications

Exported application icon for macOS:

    java/application/application.icns

…and for Windows:

    java/application/application.ico

There's none in use for Linux, since we don't export `.desktop` files with applications; though maybe we should do that in the future.


## macOS build

Application icon:

    build/macos/processing.icns

Document icons, named by extension:

    build/macos/pde.icns
    build/macos/pdex.icns
    build/macos/pdez.icns


## Windows build

Application icon:

    build/windows/processing.ico

Document icons, named by extension:

    build/windows/pdex.ico
    build/windows/pdez.ico
    build/windows/pde.ico
