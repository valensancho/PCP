.DEFAULT_GOAL := help

PROJECT_TMP := $(CURDIR)/.tmp
MAVEN_REPOSITORY := $(PROJECT_TMP)/maven-repository
MAVEN_WRAPPER_HOME := $(PROJECT_TMP)/maven-wrapper
MVNW := MAVEN_USER_HOME="$(MAVEN_WRAPPER_HOME)" ./mvnw -Dmaven.repo.local="$(MAVEN_REPOSITORY)"

.PHONY: help test benchmark clean

help:
	@printf '%s\n' \
		'Available commands:' \
		'  make test       Run all tests.' \
		'  make benchmark  Run the classroom performance benchmark.' \
		'  make clean      Remove build files and the project-local Maven cache.'

test:
	@$(MVNW) test

benchmark:
	@$(MVNW) compile
	@java -cp target/classes org.pcp.exactas.set.SetPerformanceBenchmark

clean:
	@rm -rf -- target "$(PROJECT_TMP)"
