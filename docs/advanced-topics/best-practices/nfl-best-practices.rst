Nextflow Best Practices
=======================

.. include:: best-practices-intro.rst

Authorship Metadata
-------------------

.. include:: authorship-metadata-intro.rst

This example includes author and description metadata:

*nextflow-workflow/nextflow.config*

::

    manifest {
        description = 'Generate some stats on a BAM file'
        author = 'Andrew Duncan'
    }

    params {
        flagstat_to_json = 'flagstat2json.sh'
        bamqc_pl = 'bamqc/bamqc.pl'
        bamfile = 'bamqc/t/test/neat_5x_EX_hg19_chr21.bam'
        bedfile = 'bamqc/t/test/SureSelect_All_Exon_V4_Covered_Sorted_chr21.bed'
        outjson = 'bamqc/t/test/neat_5x_EX_hg19_chr21.json'
        samtools = 'samtools'
    }

This results in the workflow's Info Tab being populated like:

.. figure:: /assets/images/docs/best_practices/wdl-info-tab-metadata.png
   :alt: wdl-info-tab-metadata

   wdl-info-tab-metadata

Next Steps
----------

.. include:: authorship-metadata-outro.rst
