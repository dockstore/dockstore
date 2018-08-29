---
title: Best Practices
permalink: /docs/publisher-tutorials/best-practices/
---
# Best Practices

Here, we document some best practices for creating tools as we understand them. Our intention is that this document will evolve as descriptor languages evolve so feel free to provide suggestions and/or improvements.  

## Authorship Metadata

Dockstore parses metadata and allows it to be used in Dockstore search which helps others find your tool/workflow more easily. "Author" is one of the metadata fields that is searchable:

![search-metadata](/assets/images/docs/best_practices/search-metadata.png)

Additionally, metadata is displayed in the tool/workflow's "Info" tab.  The highlighted sections below will appear once metadata is added to the descriptor:

![info-tab-metadata](/assets/images/docs/best_practices/info-tab-metadata.png)

For all developers, we highly recommend the following minimal metadata for your tool/workflow.  

See the [WDL documentation](https://software.broadinstitute.org/wdl/documentation/spec#metadata-section) for how to define WDL metadata.  

This example includes author, email, and description metadata:

*wdl/bamqc.wdl*
```
workflow BamQC {
    String SAMTOOLS
    File BAMFILE

    call flagstat {
        input : samtools=SAMTOOLS, bamfile=BAMFILE
    }
    call bamqc { 
        input : samtools=SAMTOOLS, bamfile=BAMFILE, xtra_json=flagstat.flagstat_json
    }
    meta {
        author: "Morgan Taschuk"
        email: "Morgan.Taschuk@oicr.on.ca"
        description: "Implementing bamqc over and over again to get an idea of how easy or hard it is for a beginner to implement a basic workflow in different workflow systems."
    }
}

task flagstat {
    String samtools
    File flagstat_to_json
    File bamfile
    String dollar="$"
    String outfile="flagstat.json"

    command {
        ${samtools} flagstat ${bamfile} | ${flagstat_to_json} > ${outfile}
    }

    output {
        File flagstat_json = "${outfile}"
    }

}

task bamqc {
    String samtools
    File bamqc_pl
    File bamfile
    File bedfile
    String outjson
    String xtra_json

    command {
        eval '${samtools} view ${bamfile} | perl ${bamqc_pl} -r ${bedfile} -j "${xtra_json}" > ${outjson}'
    }
    output {
        File out = "${outjson}"
    }
}

```
This results in the workflow's Info Tab being populated like:

![wdl-info-tab-metadata](/assets/images/docs/best_practices/wdl-info-tab-metadata.png)

## Sample Parameter Files

Below is an example of a parameter file for the example above. We encourage checking in working examples of parameter files for your tool/workflow. This allows others to quickly work with your tool/workflow, starting from a "known good" parameterization.

*local_bamqc_inputs.json*
```
{
  "BamQC.flagstat.flagstat_to_json":"flagstat2json.sh",
  "BamQC.bamqc.bamqc_pl": "bamqc/bamqc.pl",
  "BamQC.BAMFILE": "bamqc/t/test/neat_5x_EX_hg19_chr21.bam",
  "BamQC.bamqc.bedfile": "bamqc/t/test/SureSelect_All_Exon_V4_Covered_Sorted_chr21.bed",
  "BamQC.bamqc.outjson": "bamqc/t/test/neat_5x_EX_hg19_chr21.json",
  "BamQC.SAMTOOLS": "samtools"
}
```

Now invoke `dockstore` with the workflow wrapper and the input object on the command line and make sure that it succeeds:

```
$ dockstore workflow launch --local-entry wdl/bamqc.wdl --json wdl/local_bamqc_inputs.json

...
Calling out to Cromwell to run your workflow
java -jar /home/gluu/.dockstore/libraries/cromwell-29.jar run /home/gluu/one-workflow-many-ways/wdl/bamqc.wdl --inputs /tmp/foo7808763695019000464json
Cromwell exit code: 0
Cromwell stdout:
...

```
