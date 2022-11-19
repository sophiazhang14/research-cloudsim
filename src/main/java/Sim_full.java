/**

this file runs algorithms that work on the full vm list, but do not make sense to run on the filtered vm list.

defs:
waste ($): money wasted from unused VM cpu cores (from 1 simulation)
carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).

 */

import org.cloudbus.cloudsim.Log;

import java.io.FileOutputStream;
import java.util.Collections;
import java.util.function.Function;

public class Sim_full {

    // contains all possible core counts of vms
    private static final int[] CC_VALS = new int[]{2, 4, 8, 12, 24, 30};

    // stores the threshold for the max p95.
    // it is assumed that once the cpu utilization exceeds this percentage, the vm will experience some kind of performance degradation or lag.
    private static final double p95thresh = 80.0;

    // file path/name constants

    // input paths
    private static final String vm_path = "vmtable_preprocessed_short.csv",
            moer_path = "CASIO_NORTH_2019_APRIL.csv";
    // output paths
    private static final String
            sim_path = "sim.csv",
            svmlist_path = "simulated_vms.csv",
            sim_with_shutdown_path = "sim_with_shudown.csv",
            svmlist_with_shutdown_path = "simulated_vms_with_shutdown.csv",
            sim_with_core_reduction = "sim_with_core_reduction.csv",
            svmlist_with_core_reduction = "simulated_vms_with_core_reduction.csv";

    // each of the following are updated once
    // these contain the carbon and waste produced by their corresponding simulation, indicated by their name.
    private static double[] noAlg_dat, shutdown_dat, core_reduction_dat;

    private static final int numVMs = 1000;
    private static final double shutdown_acceptance = 0.65,
                                core_reduction_acceptance = 0.7;

    public static void main(String[] args)
    {
        FileOutputStream logStream;
        try{
            logStream = new FileOutputStream("simulation_logs.txt");
            Log.setOutput(logStream);
        }catch(Exception ex){
            System.out.println("wtf");
            return;
        }

        new algRunner(vm_path, moer_path, numVMs);

        noAlg_dat = algRunner.runCycle("No Algorithm (do nothing)", () -> 0.0, (String[] vm_dat) -> vm_dat, sim_path, svmlist_path);
        shutdown_dat = algRunner.runCycle("VM Shutdown Strategy", () -> 0.0, Sim_full::runShutdown, sim_with_shutdown_path, svmlist_with_shutdown_path);
        core_reduction_dat = algRunner.runCycle("Core Reduction Strategy", () -> 0.0, Sim_full::runCR, sim_with_core_reduction, svmlist_with_core_reduction);
    }

    /**
     * Here, we apply the shutdown strategy on *one* given VM.
     * Shutdown Policy:
     *      if (maximum cpu utilization) / (average cpu utilization) > 5, then recommend a shutdown
     * @param vm_dat the VM to be adjusted (represented as a list of strings)
     */

    private static String[] runShutdown(String[] vm_dat)
    {
        double max_util = Double.parseDouble(vm_dat[2]), avg_util = Double.parseDouble(vm_dat[3]);
        int t_created = (int) Double.parseDouble(vm_dat[0]), t_deleted = (int) Double.parseDouble(vm_dat[1]), t_l = t_deleted - t_created;
        System.out.println(t_created + " " + t_deleted);
        // check criteria
        if(max_util / avg_util <= 10) return vm_dat;

        // not all users will accept the recommendation.
        if(Math.random() > shutdown_acceptance) return vm_dat;

        //simulate shutting down the vm by reducing the runtime length here.
        int t_new_l = (int) (avg_util / max_util * t_l);
        int t_new_deleted = t_created + t_new_l;
        vm_dat[1] = String.valueOf(t_new_deleted); vm_dat[8] = String.valueOf(t_new_l);
        return vm_dat;
    }

    /**
     * Here, we apply core-reduction on *one* given VM.
     * Core-reduction Policies:
     * - cores > 2
     * X Avg. CPU utilization <= 0.10
     * - p95 < 0.80
     * - runtime length >= 3600 sec
     * X user has >= 100 VMs
     * - waste > $50
     * @param vm_dat the data of the VM to be adjusted.
     */
    private static String[] runCR(String[] vm_dat)
    {
        int cpuCores = (int)(Double.parseDouble(vm_dat[6]));
        double p95 = Double.parseDouble(vm_dat[4]);
        double max_util = Double.parseDouble(vm_dat[2]), avg_util = Double.parseDouble(vm_dat[3]);

        // filter un-reducable vms
        if(p95 >= p95thresh) return vm_dat;
        /*
        Code is subject to change bc users might have more options for number of cores other than those listed in the array 'CC_VALS'
        (e.g. core counts that are 1, 3, 5, 6, 7, etc.)

        Current code assumes that the vm user can only change their core count to one count listed in 'CC_VALS'.
         */
        int newCpuCores = (int)Math.ceil((double)cpuCores * p95 / p95thresh);
        for(int corecount : CC_VALS) if(corecount >= newCpuCores) {newCpuCores = corecount; break;};

        if(newCpuCores < cpuCores && Math.random() > core_reduction_acceptance) return vm_dat;

        vm_dat[6] = String.valueOf(newCpuCores);
        vm_dat[4] = String.valueOf(p95 * cpuCores / newCpuCores);
        vm_dat[3] = String.valueOf(max_util * cpuCores / newCpuCores);
        vm_dat[2] = String.valueOf(avg_util * cpuCores / newCpuCores);
        return vm_dat;
    }
}
