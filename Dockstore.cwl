#!/usr/bin/env cwl-runner
#
# Authors: Denis Yuen 

#!/usr/bin/env cwl-runner
class: CommandLineTool

description: |
    Dockstore

requirements:
  - class: ExpressionEngineRequirement
    requirements:
      - class: DockerRequirement
        dockerPull: commonworkflowlanguage/nodejs-engine
  - class: DockerRequirement
    dockerPull: dockstore 

inputs:
  - id: "#dummy"
    type: string
    default: ""

outputs:
  - id: "#dummy"
    type: string
    default: ""

baseCommand: ["/docker-dockstore-entrypoint.sh"]
