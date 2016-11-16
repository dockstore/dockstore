task stat {
  File dir
  File file
  command {
    stat ${dir} ${file}
  }
  output {
    String outf = read_string(stdout())
  }
}
workflow dir_check {
  call stat
}
