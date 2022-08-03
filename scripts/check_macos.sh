#!/usr/bin/env bash

# Checks that  README.md and the macos github action are up to date with macos_instructions.yml

CURRENT_FILE_NAME_README=README.md
TEMP_FILE_NAME_README=README-TEMP.md
CURRENT_FILE_NAME_ACTION=.github/workflows/macos_installation_instructions.yml
TEMP_FILE_NAME_ACTION=macos_installation_instructions-TEMP.yml


cp "$CURRENT_FILE_NAME_README" "$TEMP_FILE_NAME_README"
mv "$CURRENT_FILE_NAME_ACTION" "$TEMP_FILE_NAME_ACTION"

./scripts/macos-instructions.sh
EXIT_CODE=0
cmp -s "$CURRENT_FILE_NAME_README" "$TEMP_FILE_NAME_README"
if [ $? -ne 0 ]; then
  echo "Your README is not up to date with macos_instructions.yml"
  echo "Please generate any instructions related to Mac installation with scripts/macos-instructions.sh"
  EXIT_CODE=1
fi

cmp -s "$CURRENT_FILE_NAME_ACTION" "$TEMP_FILE_NAME_ACTION"
if [ $? -ne 0 ]; then
  echo "Your MacOS GitHub Action is not up to date with macos_instructions.yml"
  echo "Please generate any instructions related to Mac installation with scripts/macos-instructions.sh"
  EXIT_CODE=1
fi

rm "$CURRENT_FILE_NAME_README"
rm "$CURRENT_FILE_NAME_ACTION"

mv "$TEMP_FILE_NAME_README" "$CURRENT_FILE_NAME_README"
mv "$TEMP_FILE_NAME_ACTION" "$CURRENT_FILE_NAME_ACTION"

exit "$EXIT_CODE"
