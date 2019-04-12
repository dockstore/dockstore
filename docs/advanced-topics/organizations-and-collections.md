# Organizations and Collections

## Organizations
Organizations are landing pages for collaborations, institutions, consortiums, companies, etc. that allow users to showcase tools and workflows. 
This is achieved through the creation of collections, which are groupings of related tools and workflows. 
The users of an organization do not need to own the tools or workflows in any way; the tools and workflows just have to be published. 
Collections can be thought of as a playlist on a music streaming service where tools and workflows are analogous to individual songs. 
They can be shared publicly, and the user does not need to own them.

### Creating an organization
To create an organization request, go to the [organizations](https://dockstore.org/organizations) page and select `Create Organization Request`. 
Any user can request to create an organization by filling out the following form. For now, the request must be approved by a Dockstore curator in order to be public. 
Until it is approved, you are still able to edit it, add collections, add members, etc.

![Create Organization Request](/assets/images/docs/CreateOrganizationRequest.png)

The fields for name, display name, and topic are all required. These can be changed later. 
The organization name and display name must be unique across all of Dockstore.
* **Name** - the name used in URLs and as an identifier
* **Display Name** - the pretty name used anywhere the organization is mentioned that allows for other characters such as spaces
* **Topic** - a short description of the organization (1-2 sentences)

Optional Fields:
* **Website** - a link to the organization’s external webpage
* **Location**  - where an organization is located, for example a city or university   
* **Email** - a general contact email address for users to direct queries
* **Avatar** - a link to the organization's logo. Link must end in .jpg, .jpeg, .png, or .gif

Once a user creates an organization request, they will be redirected to the organization page. 
Here they can make edits to the organization, add collections, even add members. 
The organization will require approval from a Dockstore curator before it can be viewed publicly.

![Pending Organization](/assets/images/docs/PendingOrganization.png)

### Updating the metadata
All of the information that was defined in the organization registration form can be updated after the organization is created.

Additionally, you can add a freeform markdown description to an organization, however it is entirely optional. 
It is recommended that organizations have at least a basic description.

### Viewing organization requests
Organization requests can be viewed on the `requests` tab of the [accounts](https://dockstore.org/accounts) page. 
Currently this is the only way to track your unapproved organizations. 
Once your organization is approved, it will disappear from this page.

![Pending Organization Request](/assets/images/docs/PendingRequests.png)

If your organization was rejected, it will move to the rejected section of the requests tab. 
Once you’ve made changes to the organization, you can request a re-review.

![Rejected Organization Request](/assets/images/docs/RejectedRequests.png)

### Organization membership
Anyone can see an approved organization, though only members and maintainers of the organization can perform actions on the organizations. 
This includes creating and adding to collections, updating metadata, and adding new members.

There are two types of roles available:
* **Maintainer** - can update organization, collections, and membership
* **Member** - can only update organization and collections

Membership can be updated on the membership tab of the organization page. A maintainer cannot delete their own membership.

When a user is requested to join an organization, they will receive an invite. 
Pending invitatons are displayed on the `requests` tab of the [accounts](https://dockstore.org/accounts) page and here a user can either accept or reject the request.

## Collections
Collections are a way of gathering related tools and workflows in an easily accessible location. 
They can be used for grouping tools and workflows for a specific grant, theme, field, etc. 
A collection is only publicly visible if the organization that it belongs to is approved.

### Creating a collection
To create a collection, go to the collections tab on the organization page and select `Create collection`.
![Create Collection](/assets/images/docs/CreateCollection.png)

Collections have the same core information as organizations. The name, display name, and topic are all **required**. They can be changed later. 
The collection name and display name must be unique across all collections within the organization.
* **Name** - the name used in URLs and as an identifier
* **Display Name** - the pretty name used anywhere the collection is mentioned
* **Topic** - a short description of the collection (1-2 sentences)

**Note**: Tools and workflows are added to a collection after it is created.

### Updating the metadata
All of the information that was defined in the add collection form can be updated after the collection is created.

A freeform markdown description can be added to a collection, however it is entirely optional. It is recommended that collections have at least a basic description.

![Collection](/assets/images/docs/CollectionView.png)

### Adding tools and workflows
Only published tools and workflows can be added to a collection. If a tool/workflow belonging to a collection is unpublished, it will be hidden on the collection page until the tool/workflow is published again. 
To add a tool or a workflow to a collection, go to the public page for the tool/workflow and click `Add to collection` on the right-hand side.

![Add to Collection](/assets/images/docs/AddToCollection.png)

This will open a dialog where you can select a collection to add the tool/workflow.

![Add to Collection Dialog](/assets/images/docs/AddToCollectionModal.png)


The `Current Collections` section will now link to the PCAWG collection.

![Add to Collection PCAWG](/assets/images/docs/CurrentCollectionsWithPCAWG.png)

Once added, the tool/workflow will appear on the collection page. If a user wants to remove a tool/workflow from a collection, they can do so from the collections page.

## Events
We keep track of events occurring related to the activity on the organization page and display the important ones in the `events` tab of the organization.
Details are displayed when hovering over the individual events.

![Events](/assets/images/docs/Events.png)
