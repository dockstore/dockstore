task catS3 {
  File in_file
  command {
    cat ${in_file}
  }
  output {
    File procs = stdout()
  }
}
task catDCC {
  File in_file
  command {
    stat ${in_file}
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


workflow three_steps {
  call catS3
  call catDCC
  call catHTTP
}
