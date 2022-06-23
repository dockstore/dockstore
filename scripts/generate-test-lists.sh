#!/bin/bash

#
# Copyright 2022 OICR and UCSC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This file generates a list of test files for each profile
# This file ensures that all tests are run by having a catch all statement at the end

set -o errexit
set -o nounset
set -o pipefail

# This prefix ensure that the test lists do not interfere with anything else
PREFIX=temp/test-lists



mkdir -p "$PREFIX"

# This function changes file names in $FILE_TO_CHANGE to fully qualified class paths. For example,
# ./dockstore-integration-testing/src/test/java/io/dockstore/client/cli/BitBucketGitHubWorkflowIT.java
# becomes,
# io.dockstore.client.cli.BitBucketGitHubWorkflowIT
function make_file_names_fully_qualified_class_paths {
  sed -i 's+.*java/++g; s+/+.+g; s+\.java$++g' "$FILE_TO_CHANGE"
}


# This functions consumes the REMAINING_TEST_FILE and determines which of those files have the listed Junit category (CATEGORY) in them
# such as BitBucketTest.class, it then removes those test from REMAINING_TEST_FILE and adds them to OUTPUT_TEST_FILE
function generate_test_list {
  # Reset or create temp file
  : > "$PREFIX"/temp.txt
  grep -l "[^(//)]$CATEGORY" $(cat "$PREFIX"/"$REMAINING_TEST_FILE") > "$PREFIX"/"$OUTPUT_TEST_FILE"
  grep -v -x -f "$PREFIX"/"$OUTPUT_TEST_FILE" "$PREFIX"/"$REMAINING_TEST_FILE" > "$PREFIX"/temp.txt
  cp "$PREFIX"/temp.txt "$PREFIX"/"$REMAINING_TEST_FILE"

  FILE_TO_CHANGE="$PREFIX"/"$OUTPUT_TEST_FILE"
  make_file_names_fully_qualified_class_paths
}


#####################################
# Get list of all Integration Tests #
#####################################
REMAINING_TEST_FILE=integration-tests.txt

# Using same wild card patterns the Failsafe Plugin uses
# https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html
find . -name "*IT\.java" -or -name "IT*\.java" -or -name "*ITCase\.java" > "$PREFIX"/"$REMAINING_TEST_FILE"


# Get BitBucket ITs
CATEGORY=BitBucketTest.class
OUTPUT_TEST_FILE=bitbucketIT.txt
generate_test_list

# Get Regression ITs
CATEGORY=RegressionTest.class
OUTPUT_TEST_FILE=regressionIT.txt
generate_test_list

# Get Workflow ITs
CATEGORY=WorkflowTest.class
OUTPUT_TEST_FILE=workflowIT.txt
generate_test_list

# Get Tool  ITs
CATEGORY=ToolTest.class
OUTPUT_TEST_FILE=toolIT.txt
generate_test_list

# Get non-confidential ITs
CATEGORY=NonConfidentialTest.class
OUTPUT_TEST_FILE=non-confidentialIT.txt
generate_test_list

# Get Language Parsing ITs
CATEGORY=LanguageParsingTest.class
OUTPUT_TEST_FILE=language-parsingIT.txt
generate_test_list

# Convert remaining list of ITs from file paths to java class paths.
FILE_TO_CHANGE="$PREFIX"/"$REMAINING_TEST_FILE"
make_file_names_fully_qualified_class_paths


##############################
# Get list of all Unit Tests #
##############################
REMAINING_TEST_FILE=unit-tests.txt

# Using same wild card patterns the Surefire Plugin uses
# https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html
find . -name "Test*\.java" -or -name "*Test\.java" -or -name "*Tests\.java" -or -name "*TestCase\.java" > "$PREFIX"/"$REMAINING_TEST_FILE"


# Get Language Parsing Tests
CATEGORY=LanguageParsingTest.class
OUTPUT_TEST_FILE=language-parsing-tests.txt
generate_test_list

# Get non-confidential Tests
CATEGORY=NonConfidentialTest.class
OUTPUT_TEST_FILE=non-confidential-tests.txt
generate_test_list

# Convert remaining list of Tests from file paths to java class paths.
FILE_TO_CHANGE="$PREFIX"/"$REMAINING_TEST_FILE"
make_file_names_fully_qualified_class_paths
