#!/bin/bash

rm -rf out
mkdir -p out

javac -cp 'lib/*' -d out $(find src/main/java -name '*.java')