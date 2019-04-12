# Sharing Workflows

Starting in Dockstore 1.5, a limited sharing functionality has been introduced. With sharing,
you no longer have to choose between making a workflow completely private or public.
You can share workflows with specific accounts, giving those other users either owner,
write, or read permissions. This allows you to collaborate with a smaller set of users
without making the workflow public to everyone.

## Requirements

In its first iteration, the sharing feature is of limited scope. It will expand over time,
but for now the following requirements must be met in order to share a workflow:

* You must be either logged in with Google, or have associated a Google account with
your Dockstore account
* Your Google account must be registered with [FireCloud](https://portal.firecloud.org/).
* The accounts that you share with must also be Google accounts registered with FireCloud
* The sharing feature is only enabled for Dockstore-hosted workflows. It is not enabled
for tools, nor is it enabled for workflows whose source code lives on external version
control system such as GitHub, Bitbucket, etc.

The account/email you are sharing with does not have to correspond to a single user. It
can also correspond to a user group.

## Permissions

When sharing a workflow, you can specify one of 3 permissions for each email
you share with:

* Reader -- the other user can only read the workflow
* Writer -- the other user can read and modify the workflow
* Owner -- the other user can read, modify and share the workflow

## Sharing Workflows

If you are viewing a hosted workflow on the My Workflows page, there is a Permissions tab.
If you are also logged in with Google account that is registered with FireCloud, the
UI will show you who the workflow is currently shared with. Type in the email
that you wish to share the workflow at the permission you want and press return.

In the screenshot below, jane@example.com is the owner of the workflow, and is
in the process of adding joe@example.com, giving him writer permissions to the workflow.

![Build Trigger](/assets/images/docs/workflow-sharing.png)

## Viewing Workflows Shared With You

On the My Workflows page, on the left-hand side, there is a `Shared with me` section
that shows all workflows that have been shared with you. You can select any of these 
workflows and, depending on the permission you have been granted, share, modify, and/or
read those workflows.

In the following screenshot, jsmith is sharing the workflow `test_cwl` with the
current user.

![Build Trigger](/assets/images/docs/shared-with.png)



 
