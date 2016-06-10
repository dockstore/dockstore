task hello {
  String name

  output {
    File response = stdout()
  }
}

workflow test {
  call hello
}
