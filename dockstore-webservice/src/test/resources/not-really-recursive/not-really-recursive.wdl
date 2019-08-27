import "https://raw.githubusercontent.com/dockstore/dockstore/61757a7cfbe453c656dee0fbd395c2eed39e3949/dockstore-webservice/src/test/resources/not-really-recursive/a.wdl" as f1
import "https://raw.githubusercontent.com/dockstore/dockstore/61757a7cfbe453c656dee0fbd395c2eed39e3949/dockstore-webservice/src/test/resources/not-really-recursive/b.wdl" as f2

workflow RecursiveWorkflow {
  File inputFile

  call f1.thing as thing {
      input: inputFile = inputFile
  }
  call f2.thing as thing {
      input: inputFile = inputFile
  }
}
