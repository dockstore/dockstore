import "https://raw.githubusercontent.com/dockstore/dockstore/61757a7cfbe453c656dee0fbd395c2eed39e3949/dockstore-webservice/src/test/resources/not-really-recursive/c.wdl" as f1

workflow b {
  File inputFile

  call f1.thing as thing {
      input: inputFile = inputFile
  }
  call f2.thing as thing {
      input: inputFile = inputFile
  }
}
