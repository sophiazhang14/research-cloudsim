/**

this file runs algorithms that work on the full vm list, but do not make sense to run on the filtered vm list.

defs:
waste ($): money wasted from unused VM cpu cores (from 1 simulation)
carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).

 */

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;

import java.io.FileOutputStream;

public class Sim_full {

    // file path/name constants

    //input paths
    private static final String vm_path = "vmtable_preprocessed_short.csv",
            moer_path = "CASIO_NORTH_2019_APRIL.csv";
    //output paths
    private static final String
            svmlist_with_shutdown_path = "simulated_vms_with_shutdown.csv",
            sim_with_shutdown_path = "sim_with_shudown",
            sim_path = "sim.csv",
            svmlist_path = "simulated_vms.csv";

    // each of the following are updated once
    // these contain the carbon and waste produced by their corresponding simulation, indicated by their name.
    private static double[] shutDown_dat;
    private static double[] noAlg_dat;

    private static final int numVMs = 1000;
    private static final double shutdown_acceptance = 0.65;

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

        noAlg_dat = algRunner.runCycle("No Algorithm (do nothing)", () -> {}, (String[] vm_dat) -> {}, sim_path, svmlist_path);
        shutDown_dat = algRunner.runCycle("VM Shutdown Strategy", () -> {}, Sim_full::runShutDown, sim_with_shutdown_path, svmlist_with_shutdown_path);
    }

    /**
     * Here, we apply the shutdown strategy on *one* given VM.
     * Shutdown Policy:
     *      if (maximum cpu utilization) / (average cpu utilization) > 5, then recommend a shutdown
     * @param vm_dat the VM to be adjusted (represented as a list of strings)
     */

    private static void runShutDown(String[] vm_dat)
    {
        double max_util = Double.parseDouble(vm_dat[2]), avg_util = Double.parseDouble(vm_dat[3]);
        int t_created = (int) Double.parseDouble(vm_dat[0]), t_deleted = (int) Double.parseDouble(vm_dat[1]), t_l = t_deleted - t_created;

        // check criteria
        if(max_util / avg_util <= 5) return;

        // not all users will accept the recommendation.
        if(Math.random() > shutdown_acceptance) return;

        //simulate shutting down the vm by reducing the runtime length here.
        int t_new_l = (int) (avg_util / max_util * t_l);
        int t_new_deleted = t_created + t_new_l;
        vm_dat[1] = String.valueOf(t_new_deleted); vm_dat[8] = String.valueOf(t_new_l);
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
    private static void runWR(String[] vm_dat)
    {
        //TODO implement waste reduction

    }
}
