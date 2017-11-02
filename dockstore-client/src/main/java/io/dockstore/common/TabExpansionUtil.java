/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.common;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * A series of utility methods that produce human friendly output from tabbed output.
 *
 * Not the most efficient, since we cannot stream Strings. Borrowed from SeqWare
 *
 * @author dyuen
 */
public final class TabExpansionUtil {

    private TabExpansionUtil() {
        // disable constructor for utility classes
    }

    /**
     * Produce output resembling Postgres with one "table" per record
     *
     * @param tabSeparated
     * @return
     */
    public static String expansion(String tabSeparated) {
        String[] lines = tabSeparated.split("\n");
        // get headers
        String[] header = lines[0].split("\t");
        // determine maximum header length and other formatting
        int maxHeader = 0;
        int maxContent = 0;
        for (String h : header) {
            maxHeader = Math.max(maxHeader, h.length());
        }
        List<String[]> records = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String[] record = lines[i].split("\t");
            for (String col : record) {
                maxContent = Math.max(col.length(), maxContent);
            }
            records.add(record);
        }
        maxContent++;
        // do output
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            String head = "-[ RECORD " + i + " ]";
            buff.append(head);
            buff.append(StringUtils.repeat("-", maxHeader - head.length()));
            buff.append("+");
            buff.append(StringUtils.repeat("-", maxContent));
            buff.append("\n");
            int j = 0;
            for (String col : records.get(i)) {
                buff.append(header[j]);
                buff.append(StringUtils.repeat(" ", maxHeader - header[j].length()));
                buff.append("| ");
                buff.append(col);
                buff.append(StringUtils.repeat(" ", maxContent - col.length()));
                buff.append("\n");
                j++;
            }
        }
        return buff.toString();
    }

    /**
     * Produce aligned output that lines up properly in the terminal
     *
     * @param tabSeparated
     * @return
     */
    public static String aligned(String tabSeparated) {
        String[] lines = tabSeparated.split("\n");
        // get headers
        String[] header = lines[0].split("\t");
        // determine maximum header length and other formatting
        int[] maxContent = new int[header.length];
        List<String[]> records = new ArrayList<>();
        for (String line : lines) {
            String[] record = line.split("\t");
            int j = 0;
            for (String col : record) {
                maxContent[j] = Math.max(col.length(), maxContent[j]);
                j++;
            }
            records.add(record);
        }
        // do output
        StringBuilder buff = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            int j = 0;
            for (String col : records.get(i)) {
                buff.append(col);
                buff.append(StringUtils.repeat(" ", Math.max(0, (maxContent[j] - col.length()))));
                buff.append("|");
                j++;
            }
            if (i == 0) {
                buff.append("\n");
                for (int c : maxContent) {
                    buff.append(StringUtils.repeat("-", c));
                    buff.append("+");
                }
            }
            buff.append("\n");
        }
        return buff.toString();
    }

    public static void main(String[] args) {
        String input = "Workflow\tWorkflow Run SWID\tWorkflow Run Status\tWorkflow Run Create Timestamp\tWorkflow Run Host\tWorkflow Run Status Command\tLibrary Sample Names\tLibrary Sample SWIDs\tIdentity Sample Names\tIdentity Sample SWIDs\tInput File Meta-Types\tInput File SWIDs\tInput File Paths\tOutput File Meta-Types\tOutput File SWIDs\tOutput File Paths\tWorkflow Run Time\t\n"
                + "BamQC 1.0\t408213\tcompleted\t2012-12-18 06:06:24.842\tsqwprod.hpc.oicr.on.ca\tpegasus-status -l /u/seqware/pegasus-dax/sqwprod/seqware/pegasus/BamQC/run1269\tCPCG_0198\t305613\tCPCG_0198_Pr_P_PE_354_WG\t218446\tapplication/bam,chemical/seq-na-fastq-gzip,chemical/seq-na-fastq-gzip\t319329,219987,219988\t/oicr/data/archive/seqware/seqware_analysis_6/sqwprod/results/seqware-0.10.0_GenomicAlignmentNovoalign-0.10.1/40527172/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R1_001.fastq.gz.annotated.bam,/oicr/data/archive/seqware/seqware_analysis_3/sqwprod/results/seqware-0.10.0_IlluminaBaseCalling-1.8.2-1/86088819/Unaligned_120530_SN1068_0090_AD0V1UACXX_2/Project_na/Sample_SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R1_001.fastq.gz,/oicr/data/archive/seqware/seqware_analysis_3/sqwprod/results/seqware-0.10.0_IlluminaBaseCalling-1.8.2-1/86088819/Unaligned_120530_SN1068_0090_AD0V1UACXX_2/Project_na/Sample_SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R2_001.fastq.gz\ttext/json\t409937\t/oicr/data/archive/seqware/seqware_analysis_6/sqwprod/seqware-results/seqware-0.12.5_Workflow_Bundle_BamQC/1.0/93145099/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R1_001.fastq.gz.annotated.bam.BamQC.json\t1h 50m\n"
                + "BamQC 1.0\t408213\tcompleted\t2012-12-18 06:06:24.842\tsqwprod.hpc.oicr.on.ca\tpegasus-status -l /u/seqware/pegasus-dax/sqwprod/seqware/pegasus/BamQC/run1269\tCPCG_0198\t305613\tCPCG_0198_Pr_P_PE_354_WG\t218446\tapplication/bam,chemical/seq-na-fastq-gzip,chemical/seq-na-fastq-gzip\t319329,219987,219988\t/oicr/data/archive/seqware/seqware_analysis_6/sqwprod/results/seqware-0.10.0_GenomicAlignmentNovoalign-0.10.1/40527172/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R1_001.fastq.gz.annotated.bam,/oicr/data/archive/seqware/seqware_analysis_3/sqwprod/results/seqware-0.10.0_IlluminaBaseCalling-1.8.2-1/86088819/Unaligned_120530_SN1068_0090_AD0V1UACXX_2/Project_na/Sample_SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R1_001.fastq.gz,/oicr/data/archive/seqware/seqware_analysis_3/sqwprod/results/seqware-0.10.0_IlluminaBaseCalling-1.8.2-1/86088819/Unaligned_120530_SN1068_0090_AD0V1UACXX_2/Project_na/Sample_SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R2_001.fastq.gz\ttext/json\t409937\t/oicr/data/archive/seqware/seqware_analysis_6/sqwprod/seqware-results/seqware-0.12.5_Workflow_Bundle_BamQC/1.0/93145099/SWID_218737_CPCG_0198_Pr_P_PE_354_WG_120530_SN1068_0090_AD0V1UACXX_NoIndex_L002_R1_001.fastq.gz.annotated.bam.BamQC.json\t1h 50m ";

        System.out.println(TabExpansionUtil.expansion(input));
        System.out.println();
        System.out.println(TabExpansionUtil.aligned(input));

    }
}

