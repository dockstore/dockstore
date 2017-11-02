#!/usr/bin/env cwl-runner

cwlVersion: v1.0

class: CommandLineTool
requirements:
  - class: DockerRequirement
    dockerPull: quay.io/ncigdc/awscli:1
  - class: EnvVarRequirement
    envDef:
      - envName: "AWS_CONFIG_FILE"
        envValue: $(inputs.aws_config.path)
      - envName: "AWS_SHARED_CREDENTIALS_FILE"
        envValue: $(inputs.aws_shared_credentials.path)
  - class: InlineJavascriptRequirement

inputs:
  - id: aws_config
    type: File

  - id: aws_shared_credentials
    type: File

  - id: endpoint_json
    type: File
    inputBinding:
      loadContents: true
      valueFrom: null

  - id: signpost_json
    type: File
    inputBinding:
      loadContents: true
      valueFrom: null

outputs:
  - id: output
    type: File
    outputBinding:
      glob: |
        ${
        var signpost_json = JSON.parse(inputs.signpost_json.contents);
        var signpost_url = String(signpost_json.urls.slice(0));
        var file_name = signpost_url.split('/').slice(-1)[0].slice(0,-4) + ".bai";
        return file_name
        }

arguments:
  - valueFrom: |
      ${
      function include(arr,obj) {
        return (arr.indexOf(obj) != -1)
      }

      var signpost_json = JSON.parse(inputs.signpost_json.contents);
      var signpost_url = String(signpost_json.urls.slice(0));
      var signpost_path = signpost_url.slice(5);
      var signpost_array = signpost_path.split('/');
      var signpost_root = signpost_array[0];

      if (include(signpost_root,"ceph")) {
        var profile = "ceph";
      } else if (include(signpost_root,"cleversafe")) {
        var profile = "cleversafe";
      } else {
        var profile = signpost_root.split('.')[0];
      }

      var endpoint_json = JSON.parse(inputs.endpoint_json.contents);
      var endpoint_url = String(endpoint_json[profile]);
      return endpoint_url
      }
    prefix: --endpoint-url
    position: 0

  - valueFrom: |
      ${
      function include(arr,obj) {
        return (arr.indexOf(obj) != -1)
      }

      var signpost_json = JSON.parse(inputs.signpost_json.contents);
      var signpost_url = String(signpost_json.urls.slice(0));
      var signpost_path = signpost_url.slice(5);
      var signpost_array = signpost_path.split('/');
      var signpost_root = signpost_array[0];

      if (include(signpost_root,"ceph")) {
        return "ceph"
      } else if (include(signpost_root,"cleversafe")) {
        return "cleversafe"
      } else {
        var profile = signpost_root.split('.')[0];
        return profile
      }
      }
    prefix: --profile
    position: 1

  - valueFrom: |
      ${
      function include(arr,obj) {
        return (arr.indexOf(obj) != -1)
      }
      var signpost_json = JSON.parse(inputs.signpost_json.contents);

      var obj_path = [];
      for (var i = 0; i < signpost_json.urls.length; i++) {
        if (include(signpost_json.urls[i],"cleversafe")) {
          obj_path.push(signpost_json.urls[i]);
          break;
          }
      }
      if (obj_path.length == 0) {
        obj_path.push(signpost_json.urls[0]);
      }
      var signpost_path = obj_path[0].substring(5).split('/').slice(1).join('/');
      var s3_url = "s3://" + signpost_path.slice(0,-4) + ".bai";
      return s3_url
      }
    position: 98

  - valueFrom: .
    position: 99

baseCommand: [aws, s3, cp]
