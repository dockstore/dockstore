import "c.wdl" as f1

workflow b {
  File inputFile

  call f1.thing as thing {
      input: inputFile = inputFile
  }
  call f2.thing as thing {
      input: inputFile = inputFile
  }
}
