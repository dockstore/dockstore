version 1.0

import "Module05_06.wdl" as module
import "Module05_06Metrics.wdl" as metrics
import "TestUtils.wdl" as utils

workflow Module05_06Test {
  input {
    String test_name
    Array[File] samplelist_files
    String base_metrics
  }

  scatter (samplelist in samplelist_files) {
    Array[String] samplelist_file = read_lines(samplelist)
  }
  Array[String] samples = flatten(samplelist_file)

  call module.Module05_06 {
    input:
      samplelist_files = samplelist_files
  }

  call metrics.Module05_06Metrics {
    input:
      name = test_name,
      samples = samples,
      final_vcf = Module05_06.final_04b_vcf,
      cleaned_vcf = Module05_06.cleaned_vcf
  }

  call utils.PlotMetrics {
    input:
      name = test_name,
      samples = samples,
      test_metrics = Module05_06Metrics.metrics_file,
      base_metrics = base_metrics
  }

  output {
    File metrics = Module05_06Metrics.metrics_file
    File metrics_plot_pdf = PlotMetrics.metrics_plot_pdf
    File metrics_plot_tsv = PlotMetrics.metrics_plot_tsv
  }
}
