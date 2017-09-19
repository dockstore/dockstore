#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: DockerRequirement
    dockerPull: ubuntu:xenial-20160809
  - class: InlineJavascriptRequirement

class: CommandLineTool

inputs:
  - id: readgroups
    type: File
    inputBinding:
      loadContents: true
      valueFrom: null

outputs:
  - id: library
    type: File
    outputBinding:
      glob: $(inputs.readgroups.basename + ".library")

arguments:
  - valueFrom: |
      ${
        function contains(array_in, item) {
          for (var i = 0; i < array_in.length; i++) {
            if(array_in[i] == item) return true;
          }
          return false
        }
      
        function get_unique_array(array_in) {
          var arr = []
          for (var i = 0; i < array_in.length; i++) {
            if (!contains(arr, array_in[i])) {
              arr.push(array_in[i]);
            }
          }
          return arr
        }
      
        var lines = inputs.readgroups.contents.split('\n');
        var library_name_array = [];
        for (var i = 0; i < lines.length; i++) {
          var items = lines[i].replace("@RG\t", "");
          var items_array = items.split("\t");
          for (var j = 0; j < items_array.length; j++) {
            var item = items_array[j];
            var key = item.split(":")[0];
            var value = item.split(":")[1];
            if (key == "LB") {
              library_name_array.push(value);
            }
          }
        }

        var uniq_arr = get_unique_array(library_name_array);
        if (uniq_arr.length == 1) {
          return uniq_arr[0]
        }
        else {
          exit
        }
      }

stdout: $(inputs.readgroups.basename + ".library")

baseCommand: [echo]
