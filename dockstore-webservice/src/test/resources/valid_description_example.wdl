workflow myWorkflow {
    call myTask
  meta {
          author : "Mr. Foo"
          email : "foo@foo.com"
          description: "This is a cool workflow trying another line \n## This is a header\n* First Bullet\n* Second bullet"
      }
}

task myTask {
    command {
        echo "hello world"
    }
    output {
        String out = read_string(stdout())
    }
}