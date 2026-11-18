#!/bin/bash
# Starts the web app at http://localhost:8080 (run from the project folder).
cd "$(dirname "$0")" && ./gradlew bootRun
