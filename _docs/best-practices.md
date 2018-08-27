---
title: Best Practices
permalink: /docs/publisher-tutorials/best-practices/
---
# Best Practices

Here, we document some best practices for creating tools as we understand them. Our intention is that this document will evolve as the descriptor languages evolves so feel free to provide suggestions and/or improvements.  

## Recommended CWL Fields

Tools and workflows can take *File* types as input and produce them as output. We recommend indicating the format for *File* types. This helps document for others how to use your tool while allowing you to do some simple type-checking when creating parameter files.

For file formats, we recommend referencing existing ontologies (like EDAM in our example), reference a local ontology for your institution, or do not add a file format initially for quick development before sharing your tool with others.

Note that as a value-add `cwltool` can do some basic reasoning based on file formats and warn you if there seem to be some obvious mismatches.

*metadata_example1.cwl*
```
#!/usr/bin/env cwl-runner

class: CommandLineTool
id: Example tool
label: Example tool
cwlVersion: v1.0
doc: |
    An example tool demonstrating metadata.

requirements:
  - class: ShellCommandRequirement

inputs:
  bam_input:
    type: File
    doc: The BAM file used as input
    format: http://edamontology.org/format_2572
    inputBinding:
      position: 1

stdout: output.txt

outputs:
  report:
    type: File
    format: http://edamontology.org/format_1964
    outputBinding:
      glob: "*.txt"
    doc: A text file that contains a line count

baseCommand: ["wc", "-l"]

$schemas:
- http://dublincore.org/2012/06/14/dcterms.rdf
- http://xmlns.com/foaf/spec/20140114.rdf
```

### Sample Parameter Files

Below is an example of a parameter file for the example above. We encourage
checking in working examples of parameter files for your tool. This allows
others to quickly work with your tool, starting from a "known good"
parameterization.

*sample.json*
```
{
  "bam_input": {
        "class": "File",
	"format": "http://edamontology.org/format_2572",
        "path": "rna.SRR948778.bam"
    },
    "bamstats_report": {
        "class": "File",
        "path": "/tmp/bamstats_report.zip"
    }
}
```

Now invoke `dockstore` with the tool wrapper and the input object on the command line:

```
$ dockstore tool launch --local-entry  metadata_example.cwl --json sample.json
  ...
  Executing: cwltool --enable-dev --non-strict --outdir /home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/outputs/ --tmpdir-prefix /home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/tmp/ --tmp-outdir-prefix /home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/working/ /home/dyuen/temp/metadata_example.cwl /home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/workflow_params.json
  /usr/local/bin/cwltool 1.0.20170217172322
  Resolved '/home/dyuen/temp/metadata_example.cwl' to 'file:///home/dyuen/temp/metadata_example.cwl'
  [job metadata_example.cwl] /home/dyuen/temp/datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/working/RdrrBY$ /bin/sh \
      -c \
      'wc' '-l' '/tmp/tmpl05Efw/stge93eb613-048c-4402-b003-6b7bc144f1b9/rna.SRR948778.bam' > /home/dyuen/temp/datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/working/RdrrBY/output.txt
  [job metadata_example.cwl] completed success
  {
      "report": {
          "format": "http://edamontology.org/format_1964",
          "checksum": "sha1$68c01583e2355a6a38d814ac7d7d8e46705df56d",
          "basename": "output.txt",
          "location": "file:///home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/outputs/output.txt",
          "path": "/home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/outputs/output.txt",
          "class": "File",
          "size": 80
      }
  }
  Final process status is success
  cwltool stdout:
  	{
  	    "report": {
  	        "format": "http://edamontology.org/format_1964",
  	        "checksum": "sha1$68c01583e2355a6a38d814ac7d7d8e46705df56d",
  	        "basename": "output.txt",
  	        "location": "file:///home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/outputs/output.txt",
  	        "path": "/home/dyuen/temp/./datastore/launcher-f88ae8e3-6b6f-49f8-a622-46a61a9541d7/outputs/output.txt",
  	        "class": "File",
  	        "size": 80
  	    }
  	}

  ...
```

## Metadata and Authorship

Dockstore parses metadata and displays it in the tool/workflow's "Info" tab.  Metadata must be present in the descriptor or else the highlighted sections below will not appear.

![info-tab-metadata](/assets/images/docs/best_practices/info-tab-metadata.png)

Additionally, metadata such as the "Author" can be used in Dockstore search.

![search-metadata](/assets/images/docs/best_practices/search-metadata.png)

For all developers, we highly recommend the following minimal metadata for your tool and workflows.  

### CWL 
This example includes metadata allowing others to cite your tool.

*metadata_example2.cwl*
```
#!/usr/bin/env cwl-runner

class: CommandLineTool
id: Example tool
label: Example tool
cwlVersion: v1.0
doc: |
    An example tool demonstrating metadata. Note that this is an example and the metadata is not necessarily consistent.  

requirements:
  - class: ShellCommandRequirement

inputs:
  bam_input:
    type: File
    doc: The BAM file used as input
    format: http://edamontology.org/format_2572
    inputBinding:
      position: 1

stdout: output.txt

outputs:
  report:
    type: File
    format: http://edamontology.org/format_1964
    outputBinding:
      glob: "*.txt"
    doc: A text file that contains a line count

baseCommand: ["wc", "-l"]

$namespaces:
  s: https://schema.org/

$schemas:
- http://dublincore.org/2012/06/14/dcterms.rdf
- http://xmlns.com/foaf/spec/20140114.rdf
- https://schema.org/docs/schema_org_rdfa.html

s:author:
  - class: s:Person
    s:id: https://orcid.org/0000-0002-6130-1021
    s:email: dyuen@not-oicr.on.ca
    s:name: Denis Yuen

s:contributor:
  - class: s:Person
    s:id: http://orcid.org/0000-0002-7681-6415
    s:email: briandoconnor@not-ucsc.org
    s:name: Brian O'Connor
  - class: s:Person
    s:id: https://orcid.org/0000-0002-6130-1021
    s:email: dyuen@not-oicr.on.ca
    s:name: Denis Yuen


s:citation: https://figshare.com/articles/Common_Workflow_Language_draft_3/3115156/2
s:codeRepository: https://github.com/common-workflow-language/common-workflow-language
s:dateCreated: "2016-12-13"
s:license: https://www.apache.org/licenses/LICENSE-2.0
```

### WDL

For WDL descriptors, see the [WDL documentation](https://software.broadinstitute.org/wdl/documentation/spec#metadata-section) for how to define metadata.  

Additionally, this following example includes author, email, and description metadata:
```
task runtime_meta {
  String memory_mb
  String sample_id
  String param

  command {
    java -Xmx${memory_mb}M -jar task.jar -id ${sample_id} -param ${param} -out ${sample_id}.out
  }
  output {
    File results = "${sample_id}.out"
  }
  runtime {
    docker: "broadinstitute/baseimg"
  }
  parameter_meta {
    memory_mb: "Amount of memory to allocate to the JVM"
    param: "Some arbitrary parameter"
    sample_id: "The ID of the sample in format foo_bar_baz"
  }
  meta {
    author: "Denis Yuen"
    email: "dyuen@not-oicr.on.ca"
    description: "An example tool demonstrating metadata. Note that this is an example and the metadata is not necessarily consistent."
  }
}
```

### Extended Example

For those that are highly motivated, it is also possible to annotate your tool with a much larger amount of metadata. This example includes EDAM ontology tags as keywords (allowing the grouping of related tools), hints at hardware requirements in order to use the tool, and a few more metadata fields.

*metadata_example3.cwl*
```
#!/usr/bin/env cwl-runner

class: CommandLineTool
id: Example tool
label: Example tool
cwlVersion: v1.0
doc: |
    An example tool demonstrating metadata. Note that this is an example and the metadata is not necessarily consistent.  

requirements:
  - class: ShellCommandRequirement

hints:
  - class: ResourceRequirement
    coresMin: 4

inputs:
  bam_input:
    type: File
    doc: The BAM file used as input
    format: http://edamontology.org/format_2572
    inputBinding:
      position: 1

stdout: output.txt

outputs:
  report:
    type: File
    format: http://edamontology.org/format_1964
    outputBinding:
      glob: "*.txt"
    doc: A text file that contains a line count

baseCommand: ["wc", "-l"]

$namespaces:
  s: https://schema.org/

$schemas:
- http://dublincore.org/2012/06/14/dcterms.rdf
- http://xmlns.com/foaf/spec/20140114.rdf
- https://schema.org/docs/schema_org_rdfa.html

s:author:
  - class: s:Person
    s:id: https://orcid.org/0000-0002-6130-1021
    s:email: dyuen@not-oicr.on.ca
    s:name: Denis Yuen

s:contributor:
  - class: s:Person
    s:id: http://orcid.org/0000-0002-7681-6415
    s:email: briandoconnor@not-ucsc.org
    s:name: Brian O'Connor
  - class: s:Person
    s:id: https://orcid.org/0000-0002-6130-1021
    s:email: dyuen@not-oicr.on.ca
    s:name: Denis Yuen

s:citation: https://figshare.com/articles/Common_Workflow_Language_draft_3/3115156/2
s:codeRepository: https://github.com/common-workflow-language/common-workflow-language
s:dateCreated: "2016-12-13"
s:license: https://www.apache.org/licenses/LICENSE-2.0

s:keywords: http://edamontology.org/topic_0091 , http://edamontology.org/topic_0622
s:programmingLanguage: C
```
