



INSTRUCTION_FILE=macos_instructions.json
OUTPUT_FILE_TEXT=instructions.md
OUTPUT_FILE_CODE=instructions.sh

# Initalize output files
> "$OUTPUT_FILE_TEXT"
> "OUTPUT_FILE_CODE"

NUMBER_OF_INSTRUCTIONS=$(jq '. | length' macos_instructions.json)


format_string () {
  # Remove outer quotations, for example "blue" would become blue
  STRING_TO_FORMAT=$(sed 's+^\"++;s+\"$++' <<< "$STRING_TO_FORMAT")
  # Remove \ from all \"
  STRING_TO_FORMAT=$(sed 's+\\"+"+g' <<< "$STRING_TO_FORMAT")
}

for (( i=0; i<"$NUMBER_OF_INSTRUCTIONS"; i++ ))
do
   # Get key
   key=$(jq --argjson index "${i}" '.[$index] | keys' macos_instructions.json | tr -cd '[:alnum:]')
   if [ "$key" == "text" ]; then
      jq --argjson index "${i}" '.[$index].text' macos_instructions.json  | sed 's/^.//;s/.$//' >> $OUTPUT_FILE_TEXT
   elif [ "$key" == "newline" ]; then
      echo "" >> $OUTPUT_FILE_TEXT
   elif [ "$key" == "code" ]; then
     STRING_TO_FORMAT=$(jq --argjson index "${i}" '.[$index].code.displayBoth' macos_instructions.json)
     format_string
     {
      echo "\`\`\`"
      echo "$STRING_TO_FORMAT"
      echo "\`\`\`"
     } >> "$OUTPUT_FILE_TEXT"
   else
       echo "$key"
       echo "Strings are not equal."
   fi

done

# Delete current instructions from README.md
sed -n '1,/DO_NOT_DELETE_START_MACOS_INSTRUCTIONS/p;/DO_NOT_DELETE_END_MACOS_INSTRUCTIONS/,$p' README.md
# Add new instructions to file
> temp.txt
sed '/DO_NOT_DELETE_END_MACOS_INSTRUCTIONS/e cat instructions.md' README.md > temp.txt
cat temp.txt > README.md