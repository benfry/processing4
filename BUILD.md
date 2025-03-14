# How to Build Processing

Great to see you are interested in contributing to Processing. To get started you will need to have an IDE to build and develop Processing. Our recommendation and what we use ourselves is Intellij IDEA.

## IntelliJ IDEA

First, [download the IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/). Make sure to select the "Community Edition", not "Ultimate". The Community Edition is free and built on open-source software. You may need to scroll down to find the download link. Then:

1. Clone the Processing4 repository to your machine locally
1. Open the cloned repository in IntelliJ IDEA CE
1. In the main menu, go to File > Project Structure > Project Settings > Project.
1. In the SDK Dropdown option, select a JDK version 17 or Download the jdk
1. Click the green Run Icon in the top right of the window. This is also where you can find the option to debug Processing. 
1. Logs can be found in the `Build` or `Debug` pane on the bottom left of the window


## VSCode
1. Clone the Processing4 repository to your machine locally
1. Open the cloned repository in VScode
1. Wait for Gradle to set up the repository
1. (If you want debugging install [Debugger for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug) and [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)) 
1. Go to the Gradle Tab and click app -> Tasks -> compose desktop -> run

Instructions for other editors are welcome and feel free to contribute the documentation for those [here](#other-editors)


## Architecture
Processing is made of three distinct parts, the `Core`, `Java` and the `App`. The `Core` currently stands alone and `Java` and `App` depend on it. `Java` and `App` are currently interdependent but we are working on decoupling those two.

`Core`: The part of the code that gets bundled with your sketches, so the functionality like `ellipse(25,25,50,50);` The inner workings of that function can be found here.

`Java`: This is the pipeline that will take your `.pde` file and compile and run it. The PDE understands different _modes_ with `Java` being the primary one.

`App`: This is the PDE, the visual part of the editor that you see and work within when you use Processing.

### Examples

- You want to fix a bug with one of the argument of a function that you use in a sketch. The `Core` is probably where you would find the implementation of the function that you would like to modify.
- A feature/bug of the PDE editor has been driving you nuts, and  you can no longer stand it. You would probably find your bug in the `App` section of this project.
- You've written a large sketch and Processing has become slow to compile, a place to improve this code can probably be found in the `Java` section.

## User interface
Traditionally Processing has been written in Java swing and Flatlaf (and some html & css). Since 2025 we have switched to include Jetpack Compose, for a variety of reasons but mostly for it's inter-compatibility. There were ideas to switch to a React based editor, but this approach allows us to slowly replace Java swing components to Jetpack Compose, Ship of Theseus style.   

## Build system

We use `Gradle` as the build system for Processing. This used to be `Ant` but we have switched to be more in line with modern standards and to hopefully switch the internal build system in the `Java` mode to `Gradle` as well, unifying both systems for simplicity.

## Kotlin vs Java
Since introducing the Gradle build system we also support Kotlin within the repository. Refactors from Java to Kotlin are not really necessary at this stage, but all new functionality should be written in Kotlin.

Any classes that end in `..Kt.Java` are there for backwards compatibility with the `Ant` build system and can be removed when that is no longer necessary. 

### Running Processing

The main task to run or debug the PDE is `app:run` this run the application with `compose desktop`

If your main concern is with the `Core` you don't need to start the whole PDE to test your changes. In IntelliJ IDEA you can select any of the sketches in `core/examples/src/.../` to run by click on the green arrow next to their main functions. This will just compile core and the example sketch. Feel free to also new examples for your newly added functionality.

## Other editors
