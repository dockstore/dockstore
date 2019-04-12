# Docker Registries

There are a number of alternative Docker Registries aside from Quay.io, Docker Hub and GitLab. These registries are a combination of public registries and private registries.

## Public vs Private Docker Registries

A `public registry` is a Docker registry where Docker images are available to all users through a public website. They may also include private images, although this is not mandatory. Docker Hub is a good example of a public registry. You can browse a list of public Docker images, and also store and view private Docker images.

We currently support the following public registries:
* Docker Hub
* Quay.io
* GitLab

A `private registry` is a Docker registry where access to Docker images are restricted to authenticated users. These registries do not have public websites to view the Docker images. Amazon ECR is a good example of a private registry. These registries are useful when you want to restrict access of a tool to authorized users only.

We currently support the following private registries:
* Amazon ECR

## Registries with Custom Docker Paths
Many Docker registries that you may be familiar with use standard paths. For example, Docker Hub uses `registry.hub.docker.com`. These are used to uniquely identify a registry. While standard paths are common for major public registries, there are some registries which require a custom Docker registry path.

This is common for services like Amazon ECR which allow users to create their own registries instead of just using one big registry. Having your own registry makes it easy to restrict access to your Docker images.

For registries that require a custom path, Dockstore lets you set these paths during manual registration of a tool.


## Private Docker Registry Best Practices

### Private tools only

One thing to note is that all Dockstore tool entries that reference an image from a private Docker registry must be set as private. At registration, you will have to set the tool as private or else you will not be able to register it.

This is because private Docker registry images require authorization to access, so they have to be specified as private. For a user to gain access, they will have to select the request access button on the Dockstore tool page and give the username they have for the given Docker registry. Then it will be the responsibility of the tool maintainer to add the user to have pull access to the Docker image.

### Amazon ECR tools

Amazon ECR images are treated in Dockstore as a custom Docker registry path and an empty namespace.

The following images demonstrate registering a tool with a registry-id and an empty namespace, represented as `_`.

![Add ECR tool](/assets/images/docs/ecr.png)

Amazon ECR images have an associated file containing the `Repository Policies`. When a tool user requests access to an Amazon ECR image, the tool maintainer should add them to the list of users with pull access. More information can be found on this [Amazon ECR](http://docs.aws.amazon.com/AmazonECR/latest/userguide/RepositoryPolicyExamples.html#IAM_allow_other_accounts) page.

The user would then need to ensure that they have the AWS client installed on their machine. They then need to retrieve the Docker login command using the following command:
`aws ecr get-login --region <region> --registry-ids <registry-id>`

In this case, the `<registry-id>` is the number prefix for the docker registry path, representing the AWS ID of the user that created the registry. For example, if the entry ID on Dockstore is `312767926603.dkr.ecr.us-west-2.amazonaws.com/test_namespace/test_image`, then the registry-id would be `312767926603`.

Now if the user runs the Docker login command returned by the get-login call, they should now be able to pull the Docker image.

## Private Docker Registry Common Errors

### CWLTool can't find a Docker image
If you are trying to launch a private docker tool and are getting errors like `image not found` or `CalledProcessError: Command '['docker', 'pull', 'registrypath/namespace/name']' returned non-zero exit status 1`, then you likely do not have access to the Docker image. You'll have to request access to the Docker image by clicking the `Request Access` button on the tool's Dockstore page. If you have already done so, then you may need to contact the tool maintainer again to confirm that you have been authorized to pull the image.


## Docker Registry Request

If you would like to request support for a registry that is not currently supported, then please create an issue on our [Github](https://github.com/ga4gh/dockstore/issues) page.