version 1.0

import "./md5sum.wdl" as importedMapTask
import "https://raw.githubusercontent.com/DockstoreTestUser2/wdl-1.0-workflow/master/hello.wdl" as importedHttpTask

task test {
  runtime {
    docker: "ubuntu:latest"
  }

    #This comment will not be included within the command
    command <<<
        #This comment WILL be included within the command after it has been parsed
        echo 'Hello World'
    >>>

    output {
        String result = read_string(stdout())
    }
}


workflow wf {
  input {
    Int number  #This comment comes after a variable declaration
  }

  #You can have comments anywhere in the workflow
  call test
  call importedMapTask.md5
  call importedHttpTask.hello

  output { #You can also put comments after braces
    String result = test.result
  }


  meta {
    author: "Mr foobar"
    description: "This is the coolest workflow around"
    email: "foobar@foo.com"
  }
}