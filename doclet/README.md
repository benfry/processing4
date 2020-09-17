# Doclet

This is a custom Doclet that generates JSON files based on Javadoc comments in java files. These JSON files have all the information necessary for building the reference pages on [processing.org](https://processing.org). The Doclet will generate JSON reference files for all libraries that come with Processing as well as the external sound and video libraries.

## How to use

The Doclet will run through the `.java` files in the `processing` repo and output `.json` files in the `processing-website` repo inside the `content/references/translations/en/` folder. In order for that to work, you must first have the following four repositories in the same root folder:

- [`processing/processing4`](https://github.com/processing/processing4) (this repo)
- [`processing/processing-website`](https://github.com/processing/processing-website) (this currently lives in the designsystemsinternational GitHub account)
- [`processing/processing-sound`](https://github.com/processing/processing-sound)
- [`processing/processing-video`](https://github.com/processing/processing-video)

In order to run the Doclet, you need to have Java JDK 11 installed and set the `JAVA_HOME` environment variable to point to it. The name of the JDK file may vary depending on your exact version.

```
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.8.jdk/Contents/Home/
```

You also need to have [Apache Ant](https://ant.apache.org/manual/install.html) installed in version 1.8 or above.

Now run the Doclet:

1. First move into the `processing4/doclet/ReferenceGenerator` folder
1. Run `ant compile`
1. Run `./processingrefBuild.sh`

If you are only updating the processing reference and not the sound or video libraries, you can comment out the part related to those libraries in the `processingrefBuild.sh` file. Please remember to not commit these changes to the repo.

If you just want to test the Doclet without the `processing-website` repo, you can create the following folder structure in the root folder and see the files:

```
processing-website/content/references/translations/en/
```
