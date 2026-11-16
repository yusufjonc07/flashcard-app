#!/bin/bash

java \
  --module-path 'lib/javafx-base-25.0.1-mac-aarch64.jar:lib/javafx-graphics-25.0.1-mac-aarch64.jar:lib/javafx-controls-25.0.1-mac-aarch64.jar:lib/javafx-swing-25.0.1-mac-aarch64.jar' \
  --add-modules javafx.controls,javafx.swing \
  --enable-native-access=javafx.graphics \
  --enable-native-access=ALL-UNNAMED \
  -cp 'out:lib/*' \
  flashcards.Main