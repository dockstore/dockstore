task hello_world {
    String hello_input
    command {echo '${hello_input}' > output_file.txt
}

    output {
      File salutation = 'output_file.txt'
    }
}

workflow wf {
    call hello_world

    output {
        File outfile = "output_file.txt"
    }
}