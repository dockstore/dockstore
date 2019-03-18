---
title: Organizations and Collections
permalink: /docs/publisher-tutorials/organizations-and-collections/
---

# Organizations and Collections
Dockstore 1.6.0 adds the ability to create organizations and associated collections in order to organize tools and workflows in a public facing way.

## Organizations
Any user can request for an organization to be created by filling out the following form. Note that the request must be accepted by a Dockstore curator in order to be public. To create an organization request, go to the [organizations](https://dockstore.org/organizations) page and select `Create Organization Request`.

![Create Organization Request](/assets/images/docs/CreateOrganizationRequest.png)

The name, display name, and topic are all **required**. Note that they can be changed later.
* **name** - the name used in URLs and as an identifier (unique)
* **display name** - the pretty name used anywhere the organization is mentioned (unique)
* **topic** - a short description of the organization (1-2 sentences)

Once a user creates an organization request, they will be redirected to the organization page. Here they can make edits to the organization, add collections, even add members. The organization is not public though, and will require acceptance by a Dockstore curator before it can be viewed publicly.

![Pending Organization](/assets/images/docs/PendingOrganization.png)

### Updating the metadata
All of the information that was defined in the register organization form can be updated after the organization is created.

### Updating the description
A freeform markdown description can be added to an organization, however it is entirely optional. It is recommended that organizations have at least a basic description.

### Viewing organization requests
Organization requests can be viewed on the `requests` tab of the [accounts](https://dockstore.org/accounts) page. Currently this is the only way to find your unapproved organizations.
![Pending Organization Request](/assets/images/docs/PendingRequests.png)

### Handling organization membership
Anyone can see an approved organization, though only members and maintainers of the organization can perform actions on the organizations. This includes adding collections, updating metadata, and adding new members.

There are two types of roles available:
* **maintainer** - can update organization, collections and membership
* **member** - can only update organization and collections

Membership can be updated on the membership tab of the organization page. Note that a maintainer cannot delete their own membership.

When a user is requested to join an organization, they will get a request which is available at the `requests` tab of the [accounts](https://dockstore.org/accounts) page. Here they can either accept or reject the invitation.

## Collections
Collections are a way of organizing related tools and workflows in an easily accessible location. They can be used for grouping tools and workflows for a specific grant, theme, field, etc. A collection is only publicly visible to users if the organization that it belongs to is approved.

### Creating a collection
To create a collection, go to the collections tab on the organization page and select `Create collection`.
![Create Collection](/assets/images/docs/CreateCollection.png)

Collections have the same core information as organizations. The name, display name, and topic are all **required**. Note that they can be changed later.
* **name** - the name used in URLs and as an identifier (unique within organization)
* **display name** - the pretty name used anywhere the collection is mentioned (unique within organization)
* **topic** - a short description of the collection (1-2 sentences)

**Note**: Tools and workflows are added to the collection after it is created.

![Collection](/assets/images/docs/CollectionView.png)

### Updating the metadata
All of the information that was defined in the add collection form can be updated after the collection is created.

### Updating the description
A freeform markdown description can be added to a collection, however it is entirely optional. It is recommended that collections have at least a basic description.

### Adding tools and workflows
Only published tools and workflows can be added to a collection. To add a tool or a workflow to a collection, go to the public page for the entry and click `Add to collection` on the right-hand side.

![Add to Collection](/assets/images/docs/AddToCollection.png)

This will open a dialog where you can select which collection to add the entry to.
![Add to Collection Dialog](/assets/images/docs/AddToCollectionModal.png)

Once added, the entry will appear on the collection page. If a user wants to remove an entry from a collection, they can do so from the collections page.

The `Current Collections` seciton will now link to the PCAWG collection.
![Add to Collection PCAWG](/assets/images/docs/CurrentCollectionsWithPCAWG.png)

## Events
We keep track of events occurring related to organizations and collections and display the important ones in the `events` tab of the organization. Only a subset of all events are shown.
![Events](/assets/images/docs/Events.png)
