import "https://raw.githubusercontent.com/ga4gh/dockstore/e8eb05ec0dff7bd1585eebcb32c3f10f2d8ea681/dockstore-webservice/src/test/resources/recursive.wdl" as f1

# TODO: Change the above import to a tagged version

workflow RecursiveWorkflow {
  File inputFile

  call f1.thing as thing {
      input: inputFile = inputFile
  }
}
