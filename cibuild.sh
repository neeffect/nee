#!/usr/bin/env sh
export CODACY_PROJECT_TOKEN=$1
./gradlew build jacocoTestReport generateMergedReport
codacy/codacy-coverage-reporter report -l Kotlin -r build/reports/jacoco/generateMergedReport/generateMergedReport.xml
