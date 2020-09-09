Writing a custom doclet. The problem is including a custom jar (for json). 

I'm running ant in the command line (ant compile) to build the doclet first and there are 2 options for running the doclet. 

1. by running the processingrefBuild.sh 

	```
	./processingrefBuild.sh 
	```

	this gives "javadoc: error - In doclet class ProcessingWeblet,  method start has thrown an exception java.lang.reflect.InvocationTargetException
	java.lang.NoClassDefFoundError: org/json/JSONObject..." which I think means that the json jar needs to be included in the classpath also when running the javadoc comamand in the processingrefBuild.sh and I tried adding -classpath lib/ to the javadoc command and adding the lib to CLASSPATH in the terminal but didn't work. But maybe something else is a problem and not adding the lib to the classpath (this post https://javarevisited.blogspot.com/2011/06/noclassdeffounderror-exception-in.html has a bunch of reasons why this might be happening but nothing seems to be applicable or i tried it in a wrong way). 

2. through ant by running the command 

	```
	ant rundoc
	```

	this gives an error "javadoc: error - invalid flag: -d" so I'm guessing that the javadoc and doclet is not written correctly. If I remove the destdir from the javadoc tag it "works" in a sense that it doesn't give the flag error but then obviously there is no destdir so nothing happens 

	(this would also be the prefered option to do this in order to have everything in one place)


Additional info:
- Using JAVA 1.8 and Ant 1.10.8
