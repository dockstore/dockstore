#!/usr/bin/env cwl-runner

cwlVersion: v1.0

requirements:
  - class: InlineJavascriptRequirement

class: ExpressionTool

inputs:
  - id: readgroup_parts_1
    format: "edam:format_2572"
    type:
      type: array
      items: File

  - id: readgroup_parts_2
    format: "edam:format_2572"
    type:
      type: array
      items: File

outputs:
  - id: to_merge
    format: "edam:format_2572"
    type:
      items: array
      type: array
      items: File

  - id: not_merge
    format: "edam:format_2572"
    type:
      type: array
      items: File


expression: |
   ${
      function endsWith(str, suffix) {
        return str.indexOf(suffix, str.length - suffix.length) !== -1;
      }

      function include(arr,obj) {
        return (arr.indexOf(obj) != -1)
      }

      function getIntersection(a, b) {
        var matches = [];
        for ( var i = 0; i < a.length; i++ ) {
          if (include(b,a[i])) matches.push(a[i]);
        }
        return matches
      }

      // a - b
      function getDifference(a, b) {
        var absence = [];
        for (var i = 0; i < a.length; i++) {
          if (!include(b,a[i])) absence.push(a[i]);
        }
        return absence
      }

      var basename_parts_1 = [];
      for (var i = 0; i < inputs.readgroup_parts_1.length; i++) {
        basename_parts_1.push(inputs.readgroup_parts_1[i].basename);
      }
      var basename_parts_2 = [];
      for (var i = 0; i < inputs.readgroup_parts_2.length; i++) {
        basename_parts_2.push(inputs.readgroup_parts_2[i].basename);
      }

      var to_merge_basename_parts = getIntersection(basename_parts_1, basename_parts_2);
      var not_merge_basename_parts1 = getDifference(basename_parts_1, to_merge_basename_parts);
      var not_merge_basename_parts2 = getDifference(basename_parts_2, to_merge_basename_parts);

      var array_of_array = [];
      for (var i = 0; i < to_merge_basename_parts.length; i++) {
        var basename_part = to_merge_basename_parts[i];
        var merge_array = [];
        for (var j = 0; j < inputs.readgroup_parts_1.length; j++) {
          if (basename_part == inputs.readgroup_parts_1[j].basename) {
            merge_array.push(inputs.readgroup_parts_1[j]);
          }
        }
        for (var j = 0; j < inputs.readgroup_parts_2.length; j++) {
          if (basename_part == inputs.readgroup_parts_2[j].basename) {
            merge_array.push(inputs.readgroup_parts_2[j]);
          }
        }
        array_of_array.push(merge_array);
      }

      var not_merge = [];
      for (var i = 0; i < not_merge_basename_parts1.length; i++) {
        for (var j = 0; j < inputs.readgroup_parts_1.length; j++) {
          if (not_merge_basename_parts1[i] == inputs.readgroup_parts_1[j].basename) not_merge.push(inputs.readgroup_parts_1[j]);
        }
      }
      for (var i = 0; i < not_merge_basename_parts2.length; i++) {
        for (var j = 0; j < inputs.readgroup_parts_2.length; j++) {
          if (not_merge_basename_parts2[i] == inputs.readgroup_parts_2[j].basename) not_merge.push(inputs.readgroup_parts_2[j]);
        }
      }

      return {'to_merge': array_of_array, 'not_merge': not_merge}
    }
