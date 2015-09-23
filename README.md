# dockstore-descriptor

Example descriptor for Docker containers

When the command is constructed for this HelloWorld tool:

    docker run -v $ref_file_1:$ref_file_1.destination -v $ref_file_2:$ref_file_2.destination dockerId '$cmd'

So the command is from the JSON, in this case:

    cat $hello-input > $hello-output && ls $ref_file_2 >> $hello-output && head -20 $ref_file_2 >> $hello-output

And the various `$` field are filled in for local values relative to the CWD.

## Other Examples

Here are some example Docker-based workflows in CWL (with Dockerfiles) from 7Bridges

* https://github.com/ntijanic/gatk-cocleaning-tool
* https://github.com/ntijanic/muse-tool
