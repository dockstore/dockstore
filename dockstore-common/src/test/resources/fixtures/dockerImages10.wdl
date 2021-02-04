version 1.0

workflow myWorkflow {
    String? docker_image   = "quay.io/ucsc_cgl/verifybamid:1.3.0"

    call taglessDocker
    call latestDocker
    call parmeterizedDocker
    call versionedDocker
    call digestDocker
}

task taglessDocker {
    command {
        echo "hello world"
    }
    output {
        String out = read_string(stdout())
    }
    runtime {
        docker: "pkrusche/hap.py"
    }
}

task parmeterizedDocker {
    input {
        String docker_image
    }
    command {
        echo "hello world"
    }
    runtime {
        docker: docker_image
    }
}

task latestDocker {
    command {
        echo "hello world"
    }
    runtime {
        docker: "pkrusche/hap.py:latest"
    }
}

task versionedDocker {
    command {
        echo "hello world"
    }
    runtime {
        docker: "pkrusche/hap.py:latest"
    }
}

task digestDocker {
    command {
        echo "hello world"
    }
    runtime {
        docker: "pkrusche/hap.py:latest"
    }
}
