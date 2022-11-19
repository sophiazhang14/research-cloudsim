/**

this file runs algorithms that work on the filtered vm shortlist, but do not make sense to run on the full vm list.

defs:
waste ($): money wasted from unused VM cpu cores (from 1 simulation)
carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).

 */




import org.cloudbus.cloudsim.*;

import java.io.*;
import java.util.*;

@SuppressWarnings("SpellCheckingInspection")
public class Sim_filtered {

    // file path/name constants

    //input paths
    private static final String vm_path = "cloudsim_filtered_VM_Table_SHORTENED.csv",
            moer_path = "cloudsim_moer_data.csv";
    // output paths
    private static final String sim_path = "sim.csv",
            sim_with_AUI_path = "sim_with_AUI.csv",
            sim_with_AUMA_path = "sim_with_AUMA.csv",
            svmlist_path = "simulated_vms.csv",
            svmlist_with_AUI_path = "simulated_vms_after_AUI.csv",
            svmlist_with_AUMA_path = "simulated_vms_after_AUMA.csv";

    // each of the following are updated once
    // these contain the carbon and waste produced by their corresponding simulation, indicated by their name.
    private static double[] noAlg_dat, AUI_dat, AUMA_dat;

    private static final int numVMs = 1000;


    //MAIN FUNCTION
    public static void main(String[] args) {
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
        AUI_dat = AlgRunner.runCycle("Approach Using Intersections (AUI)", Algorithms::runAUI, (String[] vm_dat) -> vm_dat, sim_with_AUI_path, svmlist_with_AUI_path);
        AUMA_dat = AlgRunner.runCycle("Approach Using Moving Averages (AUMA)", Algorithms::runAUMA, (String[] vm_dat) -> vm_dat, sim_with_AUMA_path, svmlist_with_AUMA_path);
    }

}
