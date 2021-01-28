# Doclet

This is a custom Doclet that generates JSON files based on Javadoc comments in `.java` files. These JSON files have all the information necessary for building the reference pages on [processing.org](https://processing.org).

The Doclet will run through the `.java` file in the following repositories:

- [`processing/processing4`](https://github.com/processing/processing4) (this repo)
- [`processing/processing-sound`](https://github.com/processing/processing-sound)
- [`processing/processing-video`](https://github.com/processing/processing-video)

It will read the JavaDoc comments, create a series of `.json` files, and save them into the Processing website repository in the `content/references/translations/en/` folder:

- [`processing/processing-website`](https://github.com/processing/processing-website)

## How to use

First, make sure that you have the proper setup before running the script:

- Clone down whichever repository listed above that you need to have updated on the website. You will need at least one of `processing/processing4`, `processing/processing-sound` or `processing/processing-video` alongside the `processing/processing-website` repo. The repositories need to be alongside each other in the same folder.
- Make sure you have Java JDK 11 installed and the `JAVA_HOME` environment variable set to point to the installation. The name of the JDK file may vary depending on your exact version (e.g. `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.8.jdk/Contents/Home/`).
- Make sure you have [Apache Ant](https://ant.apache.org/manual/install.html) installed in version 1.8 or above.

Now you are ready to run the doclet

1. First `cd` into the `processing4/doclet/ReferenceGenerator` folder
2. Run `ant compile`
3. Run the script:

- If you are updating a single repository, run `./processingrefBuild.sh processing`, `./processingrefBuild.sh sound` or `./processingrefBuild.sh video`
- If you are updating all the repositories, run `./processingrefBuild.sh`

If you just want to test the Doclet without the `processing-website` repo, you can create the following folder structure in the root folder and see the files:

```
processing-website/content/references/translations/en/
```
