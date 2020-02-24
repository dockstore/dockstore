#!/bin/bash

yq r --tojson src/main/resources/openapi3/unsortedopenapi.yaml | yq r - -P > src/main/resources/openapi3/openapi.yaml
