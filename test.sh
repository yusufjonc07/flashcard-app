#!/bin/bash
# Compiles the app and the acceptance tests, then runs them (no GUI or network needed).
set -e
cd "$(dirname "$0")"
rm -rf out-test
mkdir -p out-test
javac -cp 'lib/*' -d out-test $(find src/main/java src/test/java -name '*.java')
java -cp 'out-test:lib/*' flashcards.AcceptanceTests
