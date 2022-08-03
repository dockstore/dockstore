



INSTRUCTION_FILE=macos_instructions.yaml
OUTPUT_FILE_TEXT=instructions.md
OUTPUT_FILE_CODE=instructions.sh

# Initalize output files
> "$OUTPUT_FILE_TEXT"
> "OUTPUT_FILE_CODE"

NUMBER_OF_INSTRUCTIONS=$(yq '.setupInformation | length' "$INSTRUCTION_FILE")


#format_string () {
#  # Remove outer quotations, for example "blue" would become blue
#  STRING_TO_FORMAT=$(sed 's+^\"++;s+\"$++' <<< "$STRING_TO_FORMAT")
#  # Remove \ from all \"
#  STRING_TO_FORMAT=$(sed 's+\\"+"+g' <<< "$STRING_TO_FORMAT")
#}
export i=0
for (( i=0; i<"$NUMBER_OF_INSTRUCTIONS"; i++ ))
do
   # Get key
   key=$(yq '.setupInformation.[env(i)] | keys' "$INSTRUCTION_FILE" | tr -cd '[:alnum:]')
   if [ "$key" == "text" ]; then
      yq  '.setupInformation.[env(i)].text' "$INSTRUCTION_FILE"  >> $OUTPUT_FILE_TEXT
   elif [ "$key" == "newLine" ]; then
      NUMBER_LINES_TO_PRINT=$(yq '.setupInformation[env(i)].newLine' "$INSTRUCTION_FILE")
      yes "" | head -n "$NUMBER_LINES_TO_PRINT" >> "$OUTPUT_FILE_TEXT"

   elif [ "$key" == "code" ]; then
     STRING_TO_PRINT=$(yq  '.setupInformation.[env(i)].code.run' "$INSTRUCTION_FILE")
     {
      echo "\`\`\`"
      echo -e "$STRING_TO_PRINT"
      echo "\`\`\`"
     } >> "$OUTPUT_FILE_TEXT"

   else
       echo "$key"
       echo "Strings are not equal."
   fi

done

# Delete current instructions from README.md
#sed -n '1,/DO_NOT_DELETE_START_MACOS_INSTRUCTIONS/p;/DO_NOT_DELETE_END_MACOS_INSTRUCTIONS/,$p' README.md
# Add new instructions to file
#> temp.txt
#sed '/DO_NOT_DELETE_END_MACOS_INSTRUCTIONS/e cat instructions.md' README.md > temp.txt
#cat temp.txt > README.md