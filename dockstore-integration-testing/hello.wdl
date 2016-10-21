task hello {
  command {echo hello world}
}
workflow wf {
  call hello
}
