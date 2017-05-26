## Dockstore File Provisioning Plugins 

File provisioning plugins can be used to extend the Dockstore CLI to allow for input and output files to be 
downloaded or uploaded to new and/or proprietary services. For a given input or output file
the Dockstore CLI will try to copy/hard-link files from the local filesystem, then it will look at whether 
any installed plugins can handle them, and then finally it will use [commons-vfs2](https://commons.apache.org/proper/commons-vfs/filesystems.html) to handle input files. 

## Usage

The Dockstore CLI downloads a default set of file provisioning plugins when it first installs.
By default, the default plugins will be specified in `~/.dockstore/plugins.json` file which is created the first time you run the command 'dockstore plugins download'.
This location can be overridden by using the key 'plugins-json-location' in your Dockstore config.


This will download them to your plugin directory, by default `~/.dockstore/plugins` , and unpackage them. 
When you develop or use new plugins, you will need to place the zip file for the plugin in this directory. 
The location can be overridden by using the key `file-plugins-location` in your Dockstore config. 

Use the following command to list currently installed plugins
```
dockstore plugin list
```

This will list the plugins along with their versions, their install locations, and the schemes that they handle (ex: `s3:\\test.txt` , `icgc:\\123-456` , `syn:\\syn12345` ). 

Note that dockstore will automatically delete old versions of plugins when you install new plugins. 

Configuration for plugins can be added to your ~/.dockstore/config and is indexed by id.
For example:

```
[dockstore-file-synapse-plugin]
synapse-api-key = dummy-key 
synapse-user-name = my.user.name 

[dockstore-file-s3-plugin]

[dockstore-file-icgc-storage-client-plugin]
client = /media/large_volume/icgc-storage-client-1.0.23/bin/icgc-storage-client
```


## Plugin Developers

When developing new plugins, we recommend using the [s3-plugin](https://github.com/dockstore/s3-plugin) as a model for 
plugins where [a Java library is available](https://aws.amazon.com/sdk-for-java/).
We recommend using the [icgc-storage-client-plugin](https://github.com/dockstore/icgc-storage-client-plugin) as a model for 
plugins where a Java library is not available and the plugin needs to call out to an external binary. 

This was developed in an environment with Java 8 and Maven 3.3.9. 

The steps for implementing a new plugin are as follows:

1. Fork the one of the above repos. 
2. Rename the project in the [pom.xml](https://github.com/dockstore/s3-plugin/blob/master/pom.xml#L6) by changing the artifactId, the name, and the plugin class in properties. If you wish to share your project, you may also wish to modify the repository locations. 
3. Remove the dependency on the AWS S3 library and add a library for your file transfer system [here](https://github.com/dockstore/s3-plugin/blob/master/pom.xml#L200). 
4. Rename the Java class to match the plugin class entered earlier in the pom.xml. 
5. Implement the downloadFrom and uploadTo methods from  [ProvisionInterface](https://github.com/ga4gh/dockstore/blob/develop/dockstore-file-plugin-parent/src/main/java/io/dockstore/provision/ProvisionInterface.java) Note that if your file provisioning system is input-only or output-only, you can throw an OperationNotSupportedException or similar. 
6. We recommend using [ProgressPrinter](https://github.com/ga4gh/dockstore/blob/develop/dockstore-file-plugin-parent/src/main/java/io/dockstore/provision/ProgressPrinter.java) to give your users an indication of file upload/download progress. 
7. If applicable, for file transfer systems that include metadata or require preparation or finalize steps, you can override the default methods listed in the ProvisionInterface. Note that the Base64 encoded metadata will be decoded by the time it reaches your plugin. It is up to you what kind of format the metadata should be in (for example, the s3 plugin uses a JSON map). 
8. Build the plugin with `mvn clean install` and copy the result zip file to the plugin directory. 
9. Test with a simple tool such as [md5sum](https://github.com/briandoconnor/dockstore-tool-md5sum). 

You should see something similar to the following 

```
$ cat test.s3.json 
{
  "input_file": {
        "class": "File",
        "path": "s3://oicr.temp/bamstats_report.zip"
    },
    "output_file": {
        "class": "File",
        "metadata": "eyJvbmUiOiJ3b24iLCJ0d28iOiJ0d28ifQ==",
        "path": "s3://oicr.temp/bamstats_report.zip"
    }
}

$ dockstore tool launch --entry  quay.io/briandoconnor/dockstore-tool-md5sum  --json test.s3.json
Creating directories for run of Dockstore launcher at: ./datastore//launcher-a246f1b6-21fd-468e-8780-b064d311dda5
Provisioning your input files to your local machine
Downloading: #input_file from s3://oicr.temp/bamstats_report.zip into directory: /media/large_volume/dockstore_tools/dockstore-tool-md5sum/./datastore/launcher-a246f1b6-21fd-468e-8780-b064d311dda5/inputs/73b70f11
-1711-40b7-bfea-9ee4543a8226
Found file s3://oicr.temp/bamstats_report.zip in cache, hard-linking
Calling on plugin io.dockstore.provision.S3Plugin$S3Provision to provision s3://oicr.temp/bamstats_report.zip
Calling out to cwltool to run your tool
Executing: cwltool --enable-dev --non-strict --outdir /media/large_volume/dockstore_tools/dockstore-tool-md5sum/./datastore/launcher-a246f1b6-21fd-468e-8780-b064d311dda5/outputs/ --tmpdir-prefix /media/large_volu
me/dockstore_tools/dockstore-tool-md5sum/./datastore/launcher-a246f1b6-21fd-468e-8780-b064d311dda5/tmp/ --tmp-outdir-prefix /media/large_volume/dockstore_tools/dockstore-tool-md5sum/./datastore/launcher-a246f1b6-
21fd-468e-8780-b064d311dda5/working/ /tmp/1488407859906-0/temp3047430238970788171.cwl /media/large_volume/dockstore_tools/dockstore-tool-md5sum/./datastore/launcher-a246f1b6-21fd-468e-8780-b064d311dda5/workflow_p
arams.json
/usr/local/bin/cwltool 1.0.20170217172322
...
```

