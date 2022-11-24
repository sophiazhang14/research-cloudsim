/**

    this file is the main file that calls the algRunner cycles. (simulates each type of algorithm)

    defs:
    waste ($): money wasted from unused VM cpu cores (from 1 simulation)
    carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).
    AUI: Approach Using Intersections
    AUMA: Approach Using Moving Averages
    CR: Core Reduction Strategy
    SD: Shutdown Strategy
    moer-based algorithm: algorithm that uses moer to decide what to do
    vm-based algorithm: algorithm that uses characteristics of vm to decide what to do


    outputs are files named:
     'sim*.csv' - detailed log of cloudlets,
     'svm*.csv' - list of all vms that were included in the simulation,
     and 'simulation_logs.csv' - log from cloudSim framework
 */

import org.cloudbus.cloudsim.Log;

import java.io.FileOutputStream;

public class SimMain {


    // file path/name constants

    // input paths
    private static final String vm_path = "cloudsim_vm_data_shortened.csv",
            moer_path = "cloudsim_moer_data.csv";
    // output paths
    private static final String
    sim = "sim.csv",

    sim_AUI = "sim_AUI.csv",
    sim_AUMA = "sim_AUMA.csv",

    sim_CR = "sim_CR.csv",
    sim_SD = "sim_SD.csv",

    sim_AUI_CR = "sim_AUI_CR.csv",
    sim_AUI_SD = "sim_AUI_SD.csv",

    sim_AUMA_CR = "sim_AUMA_CR.csv",
    sim_AUMA_SD = "sim_AUMA_SD.csv",



    svm = "svm.csv",

    svm_SD = "svm_SD.csv",
    svm_CR = "svm_CR.csv",

    svm_AUI = "svm_AUI.csv",
    svm_AUMA = "svm_AUMA.csv",

    svm_AUI_CR = "svm_AUI_CR.csv",
    svm_AUI_SD = "svm_AUI_SD.csv",

    svm_AUMA_CR = "svm_AUMA_CR.csv",
    svm_AUMA_SD = "svm_AUMA_SD.csv";

    // each of the following are updated once
    // these contain the carbon and waste produced by their corresponding simulation, indicated by their name.
    private static double[] noAlg_dat, shutdown_dat, core_reduction_dat, AUI_dat, AUMA_dat, AUI_CR_dat, AUMA_CR_dat, AUI_SD_dat, AUMA_SD_dat;

    private static final int numVMs = 10000;

    public static void main(String[] args)
    {
        FileOutputStream logStream;
        try{
            logStream = new FileOutputStream("simulation_logs.txt");
            Log.setOutput(logStream);
            Log.setDisabled(false);
        }catch(Exception ex){
            System.out.println("wtf");
            return;
        }

        new AlgRunner(vm_path, moer_path, numVMs);

        //run each algorithm or set of algorithms here

        // without any change (original run)
        noAlg_dat = AlgRunner.runCycle("No Algorithm (do nothing)", () -> new double[]{0, 0}, (String[] vm_dat) -> vm_dat, sim, svm);

        // moer-based algorithms
        AUI_dat = AlgRunner.runCycle("Approach Using Intersections (AUI)", Algorithms::runAUI, (String[] vm_dat) -> vm_dat, sim_AUI, svm_AUI);
        AUMA_dat = AlgRunner.runCycle("Approach Using Moving Averages (AUMA)", Algorithms::runAUMA, (String[] vm_dat) -> vm_dat, sim_AUMA, svm_AUMA);

        // vm-based algorithms
        core_reduction_dat = AlgRunner.runCycle("Core Reduction Strategy (CR)", () -> new double[]{0, 0}, Algorithms::runCR, sim_CR, svm_CR);
        shutdown_dat = AlgRunner.runCycle("VM Shutdown Strategy (SD)", () -> new double[]{0, 0}, Algorithms::runSD, sim_SD, svm_SD);

        // moer-based + core reduction
        AUI_CR_dat = AlgRunner.runCycle("AUI and CR", Algorithms::runAUI, Algorithms::runCR, sim_AUI_CR, svm_AUMA_CR);
        AUMA_CR_dat = AlgRunner.runCycle("AUMA and CR", Algorithms::runAUMA, Algorithms::runCR, sim_AUMA_CR, svm_AUMA_CR);
    }

}
