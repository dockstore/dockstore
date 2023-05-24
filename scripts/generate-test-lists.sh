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

# This file generates a list of all test files

set -o errexit
set -o nounset
set -o pipefail

# This prefix ensure that the test lists do not interfere with anything else
BASE_PREFIX=temp/test-lists

# This function changes file names in $FILE_TO_CHANGE to fully qualified class paths. For example,
# ./dockstore-integration-testing/src/test/java/io/dockstore/client/cli/BitBucketGitHubWorkflowIT.java
# becomes,
# io.dockstore.client.cli.BitBucketGitHubWorkflowIT
function make_file_names_fully_qualified_class_paths {
  sed -i 's+.*java/++g; s+/+.+g; s+\.java$++g' "$FILE_TO_CHANGE"
}

#####################################
# Get list of all Unit Tests        #
#####################################

# Modify prefix for integration tests
PREFIX="$BASE_PREFIX""/unit"
mkdir -p "$PREFIX"


# Using same wild card patterns the Failsafe Plugin uses
# https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html
find . -name "*Test\.java" -or -name "Test*\.java" -or -name "*TestCase\.java" > "$PREFIX"/all.txt

FILE_TO_CHANGE="$PREFIX"/all.txt
make_file_names_fully_qualified_class_paths


#####################################
# Get list of all Integration Tests #
#####################################

# Modify prefix for integration tests
PREFIX="$BASE_PREFIX""/IT"
mkdir -p "$PREFIX"


# Using same wild card patterns the Failsafe Plugin uses
# https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html
find . -name "*IT\.java" -or -name "IT*\.java" -or -name "*ITCase\.java" > "$PREFIX"/all.txt


FILE_TO_CHANGE="$PREFIX"/all.txt
make_file_names_fully_qualified_class_paths
