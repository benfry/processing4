This is a custom doclet that based on the comments in java files generate json files with all the information 
necessary for building the reference pages on the website. The references include the main processing references, 
references for the libraries that come with the processing code as well as external libraries sound and video.
In order for everything to be generated you need to have the following 4 repositories:

- processing
- processing-website
- sound
- video 

In order to build it you need to have java jdk 11 installed and set the JAVA_HOME variable to point to it:

	```
	export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.8.jdk/Contents/Home/
	```

jdk-11.0.8.jdk could be different depending on the exact version you have.

You also need to have [Apache Ant](https://ant.apache.org/manual/install.html) (version 1.8 or above).

Running the doclet:

1. in the processing/doclet/ReferenceGenerator folder run:

	```
	ant compile
	```

2. in the same folder run: 

	```
	./processingrefBuild.sh 
	```

If you are only changing processing references and not sound and video libraries you can remove the part related to 
sound and video in processingrefBuild.sh, save the script and run it. If you do that do not stage the 
processingrefBuild.sh script for commit.

If you do not have the processing-website repo and you just want to test the doclet create the following folder structure
in the same root where you have processing source

	```
	processing-website/content/references/translations/en/
	```

So you end up having this folder structure in the root:

	```
	processing/
	processing-website/content/references/translations/en/
	```