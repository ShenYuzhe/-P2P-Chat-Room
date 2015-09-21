JAVAC = javac
JAVA = java
PACKAGEROOM = clientRoom/
PACKAGESERV = serverManager/

sources = ${PACKAGEROOM}ClientManager.java ${PACKAGEROOM}ClientObj.java \
	${PACKAGEROOM}ClientTalk.java ${PACKAGEROOM}reqType.java \
	${PACKAGESERV}WelcomServer.java
CLASSPATH = ".:lib/org.json.jar"
CLASSES = $(sources:.java=.class)


all: $(CLASSES)

clean :
	rm -f ${PACKAGESERV}*.class ${PACKAGEROOM}*.class


ifeq (run,$(firstword $(MAKECMDGOALS)))
  ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
  $(eval $(ARGS):;@:)
endif
run: 
	$(JAVA) -cp $(CLASSPATH) ${PACKAGESERV}WelcomServer ${ARGS}

 %.class : %.java
	$(JAVAC)  -cp $(CLASSPATH) $<
