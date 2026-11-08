#!/usr/bin/env bash
# Compiles the core (no UI) plus the test runner and runs it.
set -e
cd "$(dirname "$0")"
OUT="$(mktemp -d)"
javac --release 17 -encoding UTF-8 -d "$OUT" src/model/*.java src/util/*.java src/service/*.java test/TestRunner.java
java -Djava.awt.headless=true -cp "$OUT" TestRunner
rm -rf "$OUT"
