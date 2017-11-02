#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InlineJavascriptRequirement

class: ExpressionTool

inputs:
  - id: filearray
    type:
      type: array
      items: File

  - id: filename
    type: string

outputs:
  - id: output
    type: File

expression: |
   ${
      function include(arr,obj) {
        return (arr.indexOf(obj) != -1)
      }

      function local_basename(path) {
        var basename = path.split(/[\\/]/).pop();
        return basename
      }

      // get filepath using filename
      for (var i = 0; i < inputs.filearray.length; i++) {
        var this_file = inputs.filearray[i];
        var this_basename = local_basename(this_file.location);
        if (include(inputs.filename, this_basename)) {
          return {'output' : this_file }
        }
      }
    }
