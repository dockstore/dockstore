version 1.0

import "first-import.wdl" as first
task hello {
  input {
    String name
  }

  command {
    echo 'Hello ${name}!'
  }
  output {
    File response = stdout()
  }
}

workflow test {
  call hello
  call first.hello2

}
