#!/usr/bin/env bash

# Using https://mermade.org.uk/openapi-converter, generate openapi schema sources.
HTTP_STATUS=`curl -F "filename=@src/main/resources/swagger.yaml" https://mermade.org.uk/api/v1/convert -o src/main/resources/open_api.yaml -w "%{http_code}"`
if [ "$?" -eq 0 ] && [ "$HTTP_STATUS" -eq 200  ]; then
  # Parse out position variable not compatible with open-api schemas
  sed --in-place '/position/d;s/<html><body><pre>//' src/main/resources/open_api.yaml
  # Aggregate correct url for dockstore api. Using pipe instead of forward slash, avoid dealing with scape chars
  sed --in-place 's|url: /|url: https://dockstore.org:8443|' src/main/resources/open_api.yaml

  # Additional tag for smart-api compliance.
  sed --in-place '/- name: GA4GHV1/i \
  - name: NIHdatacommons
  ' src/main/resources/open_api.yaml

  # Clean directory from excess files.
  mv src/main/resources/open_api.yaml src/main/resources/openapi.yaml
else
  echo "Error converting swagger to openapi, skipping openapi generation"
fi
exit 0
