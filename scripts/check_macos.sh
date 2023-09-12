#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace


# Checks that  README.md and the macos github action are up to date with macos_instructions.yml

# This script must be run in the root of the directory where you cloned the dockstore/dockstore repo

CURRENT_FILE_NAME_README=README.md
TEMP_FILE_NAME_README=README-TEMP.md
CURRENT_FILE_NAME_ACTION=.github/workflows/macos_installation_instructions.yml
TEMP_FILE_NAME_ACTION=macos_installation_instructions-TEMP.yml


cp "$CURRENT_FILE_NAME_README" "$TEMP_FILE_NAME_README"
# Copy "$CURRENT_FILE_NAME_README" as ./scripts/macos-instructions.sh modifies "$CURRENT_FILE_NAME_README"
mv "$CURRENT_FILE_NAME_ACTION" "$TEMP_FILE_NAME_ACTION"

./scripts/macos-instructions.sh

EXIT_CODE=0

if ! cmp -s "$CURRENT_FILE_NAME_README" "$TEMP_FILE_NAME_README"
then
  echo "Your README is not up to date with macos_instructions.yml"
  echo "Please generate any instructions related to Mac installation with scripts/macos-instructions.sh"
  EXIT_CODE=1
fi

if ! cmp -s "$CURRENT_FILE_NAME_ACTION" "$TEMP_FILE_NAME_ACTION"
then
  echo "Your MacOS GitHub Action is not up to date with macos_instructions.yml"
  echo "Please generate any instructions related to Mac installation with scripts/macos-instructions.sh"
  EXIT_CODE=1
fi

rm "$CURRENT_FILE_NAME_README"
rm "$CURRENT_FILE_NAME_ACTION"

mv "$TEMP_FILE_NAME_README" "$CURRENT_FILE_NAME_README"
mv "$TEMP_FILE_NAME_ACTION" "$CURRENT_FILE_NAME_ACTION"

exit "$EXIT_CODE"
