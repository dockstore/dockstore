import "a.wdl" as f1
import "b.wdl" as f2

workflow c {
  File inputFile

  call f1.thing as thing {
      input: inputFile = inputFile
  }
  call f2.thing as thing {
      input: inputFile = inputFile
  }
}
