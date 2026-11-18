#!/bin/bash
# Builds a runnable jar: build/libs/flashcard-app-1.0.0.jar
cd "$(dirname "$0")" && ./gradlew bootJar
