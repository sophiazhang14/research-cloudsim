/**

this file runs algorithms that work on the full vm list, but do not make sense to run on the filtered vm list.

defs:
waste ($): money wasted from unused VM cpu cores (from 1 simulation)
carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).

 */

import org.cloudbus.cloudsim.Log;

import java.io.FileOutputStream;

public class Sim_full {


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

        new AlgRunner(vm_path, moer_path, numVMs);

        //run each algorithm or set of algorithms here
        noAlg_dat = AlgRunner.runCycle("No Algorithm (do nothing)", () -> 0.0, (String[] vm_dat) -> vm_dat, sim_path, svmlist_path);
        shutdown_dat = AlgRunner.runCycle("VM Shutdown Strategy", () -> 0.0, Algorithms::runShutdown, sim_with_shutdown_path, svmlist_with_shutdown_path);
        core_reduction_dat = AlgRunner.runCycle("Core Reduction Strategy", () -> 0.0, Algorithms::runCR, sim_with_core_reduction, svmlist_with_core_reduction);
    }

}
