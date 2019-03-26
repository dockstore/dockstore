---
title: Nextflow Best Practices
permalink: /docs/publisher-tutorials/nfl-best-practices/
---
{% include_relative best-practices-intro.md %}

## Authorship Metadata

{% include_relative authorship-metadata-intro.md %}

This example includes author and description metadata:


*nextflow-workflow/nextflow.config*
```
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
```
This results in the workflow's Info Tab being populated like:

![wdl-info-tab-metadata](/assets/images/docs/best_practices/wdl-info-tab-metadata.png)

## Next Steps

{% include_relative authorship-metadata-outro.md %}
<!--stackedit_data:
eyJoaXN0b3J5IjpbLTEzMzk2NjQ0ODhdfQ==
-->