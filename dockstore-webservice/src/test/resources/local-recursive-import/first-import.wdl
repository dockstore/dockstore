version 1.0
import "localrecursive.wdl" as second

task hello2 {
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
