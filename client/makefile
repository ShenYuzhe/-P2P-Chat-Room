JAVAC = javac
JAVA = java
PACKAGE = client/

sources = ${PACKAGE}clientHeartBeat.java ${PACKAGE}clientMain.java \
	${PACKAGE}clientReceiver.java ${PACKAGE}clientSender.java
CLASSPATH = ".:lib/org.json.jar"
CLASSES = $(sources:.java=.class)


all: $(CLASSES)

clean :
	rm -f ${PACKAGE}*.class

ifeq (run,$(firstword $(MAKECMDGOALS)))

  ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
  
  $(eval $(ARGS):;@:)
endif
run: 
	$(JAVA) -cp $(CLASSPATH) ${PACKAGE}clientMain ${ARGS}

 %.class : %.java
	$(JAVAC)  -cp $(CLASSPATH) $<
