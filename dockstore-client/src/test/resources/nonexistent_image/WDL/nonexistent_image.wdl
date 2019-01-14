task hello {
  String name

  command {
    echo 'Hello ${name}!'
  }
  output {
    File response = stdout()
  }

  runtime {
    docker: "dockstore.org/bashwithbinbash:0118999881999119725...3"
  }
}

workflow test {
  call hello
}
