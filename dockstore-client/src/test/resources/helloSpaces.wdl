task hello {
  File inputFile

  command {
    cat ${inputFile}
  }
  output {
    File response = stdout()
  }
}

workflow test {
  File inputFile
  call hello { input: inputFile=inputFile }
}
