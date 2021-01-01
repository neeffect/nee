#!/bin/bash
. gojava11
./gradlew --stop
./gradlew clean build publishToMavenLocal
