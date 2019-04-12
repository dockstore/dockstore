# Public and Private Dockstore Tools
Most tool creators want public access to their tools, however some might want to restrict access to running the tool.

A `public Dockstore tool` is a tool which does not require authentication to view on the Docker registry's website or to pull the Docker image. It is freely available for anyone to use.

A `private Dockstore tool` is a tool which requires authentication to view on the Docker registry's website (if it exists) and to pull the Docker image. A user interested in using a private tool must select the `Request Access` button found on the tool's Dockstore entry. Doing so will create an email to the tool maintainer that will be sent from the user's email client. **The user must include their username for the Docker registry of interest**. It is then the responsibility of the tool maintainer to give the user permission to view/pull the docker image.

It is important to note that descriptor files and tool metadata will be visible to other users when the tool is published. This is in order to make others aware of your tool. However, tool creators will be responsible for managing sharing of Docker images, and they will not be available to others without authentication.