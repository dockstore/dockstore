Different Ways To Register Tools on Dockstore
=============================================

There are 3 major ways to register tools on Dockstore 

- The Dockstore website
- The Dockstore webservice
- The :doc:`Write API webservice and client <conversions/>`

There is no clear cut answer for determining which is the best way to
register tools on Dockstore. Many factors affect it. Below is merely our
a suggestion, so feel free to register tools on Dockstore whichever way
you prefer.

Registering many tools or only a few?

- Very Few

    - Use the Dockstore website. You just need to manually create the GitHub and Quay.io repository (if they don't exist). If you're using Quay.io as the image registry, you can simply "Refresh All Tools" on the Dockstore website. Otherwise, you can manually register the tool.

- Many

    - GitHub and image registry repositories already made for each tool?

        - Yes

            - Are you using Quay.io for your image registry?

                - Yes

                    - Use either the Dockstore webservice or website. You just need to refresh all tools. All of your Quay.io tools should automatically register on Dockstore.

                - No

                    - Use the Dockstore webservice so you can programmatically register and publish all tools.

        - No

            - Use the Write API webservice and client. After some setup time (getting GitHub and Quay.io tokens, setting up service, etc), it allows you to programmatically create GitHub and Quay.io repositories on the fly, and then register/publish them on Dockstore.

Generally, Write API webservice and client has the highest setup time
compared to the other methods of registering. But, as you register more
tools, the Write API tends to become the better choice (since it
performs many intermediary steps for you).