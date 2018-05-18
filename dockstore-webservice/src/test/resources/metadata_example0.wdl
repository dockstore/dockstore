task snowmanvcf2dRangerForBP {

    #Inputs and constants defined here
    String PAIRID
    File snowman_sv_VCF
    File refdata1
    String minscoreforbp
    String minspan
    String maxpon
    String maxnorm
    String mintumorSR
    String mintumorRP
    String mintumor

    String output_disk_gb = "100"
    String boot_disk_gb = "10"
    String ram_gb = "2"
    String cpu_cores = "1"
    String preemption = "4"


    command {
cat <<EOF > pyscript.py

import subprocess,os
def run(cmd):
    print('about to run')
    print(cmd)
    print('')
    subprocess.check_call(cmd,shell=True)

run('ln -sT `pwd` /opt/execution')
run('ln -sT `pwd`/../inputs /opt/inputs')
run('/opt/src/algutil/monitor_start.py')

# start task-specific calls
##########################



refdata1='${refdata1}'
cwd = os.getcwd()
REFDIR = os.path.join(cwd,'refdata')
if not os.path.exists(REFDIR):
    os.mkdir(REFDIR)
    # unpack reference files
    run('tar xvf %s -C %s'%(refdata1,REFDIR))


PAIRID = '${PAIRID}'
VCF0 = '${snowman_sv_VCF}'
run('cp ' + VCF0 + '  .')
vcf_path, VCF = os.path.split(VCF0)
VCF_filename, VCF_file_extension = os.path.splitext(VCF)
if VCF_file_extension == '.gz':
    run('gunzip ' + VCF )
    VCF=os.path.join(cwd,VCF_filename)

run('ls -latr ')
run('ls -latr '+ REFDIR + '/public')

minscoreforbp='${minscoreforbp}'
minspan='${minspan}'
maxpon='${maxpon}'
maxnorm='${maxnorm}'
mintumorSR='${mintumorSR}'
mintumorRP='${mintumorRP}'
mintumor='${mintumor}'


PONDIR = REFDIR + '/public/dRanger_PoN_JH'
RMAT = REFDIR + '/public/R.mat'


cmd = 'python /opt/src/algutil/firehose_module_adaptor/run_module.py --module_libdir /opt/src/snowmanvcf2dRangerForBP \
--VCF %s \
--normpaneldb %s \
--use_PoN_JH true \
--minscoreforbp  %s \
--build_Rmat  %s \
--min.span %s \
--max.pon %s \
--max.norm %s \
--min.tum.SR %s \
--min.tum.RP %s \
--min.tum %s '% (VCF,PONDIR,minscoreforbp,RMAT,minspan,maxpon,maxnorm,mintumorSR,mintumorRP,mintumor)

run(cmd)

run('ls -latr ')

import time
#time.sleep(999999999)


#########################
# end task-specific calls
run('/opt/src/algutil/monitor_stop.py')
EOF

        cat pyscript.py
        python pyscript.py

    }

    output {
        File forBP_txt="${PAIRID}.snowman_results.forBP.txt"
        File dstat_log="dstat.log"
    }

    runtime {
        docker : "docker.io/chipstewart/snowmanvcf2drangerforbp:1"
        memory: "${ram_gb}GB"
        cpu: "${cpu_cores}"
        disks: "local-disk ${output_disk_gb} HDD"
        bootDiskSizeGb: "${boot_disk_gb}"
        preemptible: "${preemption}"
    }


    meta {
        author : "Chip Stewart"
        email : "stewart@broadinstitute.org"
    }

}

workflow snowmanvcf2dRangerForBP_workflow {
    call snowmanvcf2dRangerForBP
}

