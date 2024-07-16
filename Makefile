.PHONY: all
all: build

.PHONY: build
build:
	mvn clean compile

.PHONY: test
test:
	_JAVA_OPTIONS="--add-opens=java.base/java.nio=ALL-UNNAMED" mvn test

.PHONY: install
install:
	mvn clean install -DskipTests=true -Dgpg.skip

.PHONY: publish
publish:
	mvn --settings settings.xml -Dmaven.test.skip=true deploy
