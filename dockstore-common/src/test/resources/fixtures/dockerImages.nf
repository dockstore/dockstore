#!/usr/bin/env nextflow

params.container = 'quay.io/ucsc_cgl/verifybamid:1.3.0'

process parameterizedDocker {
    container '$params.container'

    """
    """
}

process latestDocker {
    container 'quay.io/ucsc_cgl/verifybamid:latest'

    """
    """
}

process versionedDocker {
    container 'quay.io/ucsc_cgl/verifybamid:1.3.0'

    """
    """
}

process taglessDocker {
    container 'quay.io/ucsc_cgl/verifybamid'

    """
    """
}

process digestDocker {
    container 'quay.io/ucsc_cgl/verifybamid@sha256:05442f015018fb6a315b666f80d205e9ac066b3c53a217d5f791d22831ae98ac'

    """
    """
}