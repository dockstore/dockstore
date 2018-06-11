#!/bin/bash

# Using https://mermade.org.uk/openapi-converter, generate openapi schema sources.
curl -F "filename=@src/main/resources/swagger.json" https://mermade.org.uk/api/v1/convert -o src/main/resources/open_api.yaml

# Parse out position variable not compatible with open-api schemas
sed '/position/d;s/\<html><body><pre>//;w src/main/resources/openapi1.yaml' src/main/resources/open_api.yaml

# Aggregate correct url for dockstore api. Using pipe instead of forward slash, avoid dealing with scape chars
sed 's|url: /|url: https://dockstore.org:8443|; w src/main/resources/openapi2.yaml' src/main/resources/openapi1.yaml

# Additional tag for smart-api compliance.
sed '/tags:/a \
\ \ - name: NIHdatacommons
' src/main/resources/openapi2.yaml > src/main/resources/openapi.yaml

# Clean directory from excess files.
rm src/main/resources/swagger.json src/main/resources/open_api.yaml src/main/resources/openapi1.yaml src/main/resources/openapi2.yaml

exit 0