#!/usr/bin/env bash

# Support for pde browser protocol
# Processing executable path
EXEC=<BINARY_LOCATION>

if [ -z "${1}" ]; then
    "$EXEC"
    exit 0
fi

if [[ "$1" == "pde:"* ]]; then
    # Extracting *.pde file path from url
    url=$(echo "$1" | sed 's|pde://||')
    file_path=$(python3 -c "import urllib.parse; print(urllib.parse.unquote('$url'))")
else
    file_path="$1"
fi

# Calling execution
"$EXEC" "/${file_path}"
