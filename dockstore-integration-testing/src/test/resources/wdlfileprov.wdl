task catS3 {
  File in_file
  command {
    cat ${in_file}
  }
  output {
    File procs = stdout()
  }
}

task catHTTP {
  File in_file
  command {
    cat ${in_file}
  }
  output {
    File procs = stdout()
  }
}


workflow two_steps {
  call catS3
  call catHTTP
}
