#!/usr/bin/env bash

# Support for pde browser protocol
# Processing executable path
EXEC=<BINARY_LOCATION>

if [ -z $1 ]; then
    "$EXEC"
    exit 0
fi

if [[ "$1" == "pde:"* ]]; then
    # Extracting *.pde file path from url
    file_path=$(echo "$1" | sed 's|pde://||')
else
    file_path="$1"
fi

# Calling execution
"$EXEC" "/${file_path}"
