# TRS With Checker Workflows
You can interact with checker workflows using TRS in the same way you interact with regular workflows.

The Swagger UI for the TRS endpoints can be found [here](https://dockstore.org/api/static/swagger-ui/index.html#/GA4GH).

For this section we will reference the tool and checker workflow mentioned in the [Checker workflows](checker-workflows/) tutorial.

### Distinguishing Between an Entry and a Checker Workflow
We can use TRS to find all entries on Dockstore, but how do we know which are checker workflows?

Run the following command to retrieve all entries in Dockstore (limit to 1000):
```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools?limit=1000" -H "accept: application/json"
```

There are two fields to look for in the results. The first is the `has_checker` field, which will tell you whether or not the entry has a checker workflow. If it does have a checker workflow, then the `checker_url` field contains a link to the checker workflow using TRS. This is how we can determine if an entry has a checker, and also interact with the associated checker.

Once we have the checker URL, we can start to interact with it like a regular workflow.

A simple way to find only checker workflows is to run the following command:

```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools?checker=true&limit=1000" -H  "accept: application/json"
```

This will get the first 1000 checker workflows in Dockstore.

### Retrieve the checker workflow
Let's use TRS to retrieve the checker workflow from the checker workflows tutorial mentioned above. The following command will retrieve the checker workflow object. Note that this is simply using the typical TRS endpoint
to grab an entry.

```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker" -H "accept: application/json"
```

### Retrieve descriptors
If we want the primary descriptor for our checker workflow, we can use the following command:

```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/descriptor" -H "accept: application/json"
```

Note that this command requires a version and descriptor type.

If we want to grab the secondary descriptor files, we must first get a list of secondary files:

```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/files" -H "accept: application/json"
```

This will return an array of files, including the primary descriptor, secondary descriptors, and test files.

We can grab a descriptor file using the following command:

```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/descriptor/checker%2Fmd5sum-checker.cwl" -H "accept: application/json"
```

### Retrieve the Test Parameter Files
We can retrieve the test parameter files for a checker workflow using the following command:
```
curl -X GET "https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2Fmd5sum-checker%2F_cwl_checker/versions/master/CWL/tests" -H "accept: application/json"
```

Again, we must specify the version and descriptor type in the command.

This will return an array of objects representing the test parameter files. Each object contains two fields, test and url.
- **test**: the test file
- **url**: the relative path

### Other Usage
This does not cover all of the uses of TRS for checker workflows. You can see the full list of commands  [here](https://dockstore.org/api/static/swagger-ui/index.html#/GA4GH). Note that any call that works for a normal tool or workflow will also work with a checker workflow.
