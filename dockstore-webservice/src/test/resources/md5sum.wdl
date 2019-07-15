version 1.0

task md5 {
   input {
     File inputFile
   }

  command {
    /bin/my_md5sum ${inputFile}
  }

 output {
    File value = "md5sum.txt"
 }

 runtime {
   docker: "quay.io/agduncan94/my-md5sum"
 }
}

workflow ga4ghMd5 {
 input {
   File inputFile
 }
 call md5 { input: inputFile=inputFile }
}