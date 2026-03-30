#!/bin/bash
# Build and run the 8086 Simulator

SRC_DIR="src"
OUT_DIR="out"

echo "=== 8086 Assembly Simulator Build Script ==="

# Compile
mkdir -p $OUT_DIR
echo "Compiling..."
javac -d $OUT_DIR $(find $SRC_DIR -name "*.java")

if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Compilation successful!"
echo ""

# Run
if [ "$1" != "" ]; then
    echo "Running file: $1"
    java -cp $OUT_DIR BRU86.Simulator "$1"
else
    echo "Starting interactive mode..."
    java -cp $OUT_DIR BRU86.Simulator
fi
