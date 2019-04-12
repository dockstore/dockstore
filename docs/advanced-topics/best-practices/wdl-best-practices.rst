WDL Best Practices
==================

.. include:: best-practices-intro.rst

Authorship Metadata
-------------------

.. include:: authorship-metadata-intro.rst

This example includes author, email, and description metadata:

*wdl/bamqc.wdl*

::

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
            author: "Muhammad Lee"
            email: "Muhammad.Lee@oicr.on.ca"
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

This results in the workflow's Info Tab being populated like:

.. figure:: /assets/images/docs/best_practices/wdl-info-tab-metadata.png
   :alt: wdl-info-tab-metadata

   wdl-info-tab-metadata

.. include:: sample-parameter-files-intro.rst

*local\_bamqc\_inputs.json*

::

    {
      "BamQC.flagstat.flagstat_to_json":"flagstat2json.sh",
      "BamQC.bamqc.bamqc_pl": "bamqc/bamqc.pl",
      "BamQC.BAMFILE": "bamqc/t/test/neat_5x_EX_hg19_chr21.bam",
      "BamQC.bamqc.bedfile": "bamqc/t/test/SureSelect_All_Exon_V4_Covered_Sorted_chr21.bed",
      "BamQC.bamqc.outjson": "bamqc/t/test/neat_5x_EX_hg19_chr21.json",
      "BamQC.SAMTOOLS": "samtools"
    }

Now invoke ``dockstore`` with the workflow wrapper and the input object
on the command line and ensure that it succeeds:

::

    $ dockstore workflow launch --local-entry wdl/bamqc.wdl --json wdl/local_bamqc_inputs.json

    ...
    Calling out to Cromwell to run your workflow
    java -jar /home/gluu/.dockstore/libraries/cromwell-29.jar run /home/gluu/one-workflow-many-ways/wdl/bamqc.wdl --inputs /tmp/foo7808763695019000464json
    Cromwell exit code: 0
    Cromwell stdout:
    ...

Next Steps
----------

.. include:: authorship-metadata-outro.rst
