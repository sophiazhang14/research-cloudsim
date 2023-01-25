/**

    this file is the main file that calls the algRunner cycles. (simulates each type of algorithm)

    defs:
    waste ($): money wasted from unused VM cpu cores (from 1 simulation)
    carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).
    RT: Approach Using Intersections
    RA: Approach Using Moving Averages
    CR: Core Reduction Strategy
    SD: Shutdown Strategy
    moer-based algorithm: algorithm that uses moer to decide what to do
    vm-based algorithm: algorithm that uses characteristics of vm to decide what to do


    outputs are files named:
     'sim*.csv' - detailed log of cloudlets,
     'svm*.csv' - list of all vms that were included in the simulation,
     and 'simulation_logs.csv' - log from cloudSim framework
 */

import org.cloudbus.cloudsim.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class SimMain {
    public static boolean fullOutput = true;

    // file path/name constants

    // input paths
    private static final String vm_path = "cloudsim_vm_data_all.csv",
            moer_path = "cloudsim_CASIO_NORTH_FULL.csv";
    // output paths
    private static final String
    sim = "sim.csv",

    sim_RT = "sim_RT.csv",
    sim_RA = "sim_RA.csv",

    sim_CR = "sim_CR.csv",
    sim_SD = "sim_SD.csv",

    sim_RT_CR = "sim_RT_CR.csv",
    sim_RT_SD = "sim_RT_SD.csv",

    sim_RA_CR = "sim_RA_CR.csv",
    sim_RA_SD = "sim_RA_SD.csv",



    svm = "svm.csv",

    svm_SD = "svm_SD.csv",
    svm_CR = "svm_CR.csv",

    svm_RT = "svm_RT.csv",
    svm_RA = "svm_RA.csv",

    svm_RT_CR = "svm_RT_CR.csv",
    svm_RT_SD = "svm_RT_SD.csv",

    svm_RA_CR = "svm_RA_CR.csv",
    svm_RA_SD = "svm_RA_SD.csv";

    // each of the following are updated once
    // these contain the carbon and waste produced by their corresponding simulation, indicated by their name.
    private static double[] noAlg_dat, shutdown_dat, core_reduction_dat, RT_dat, RA_dat, RT_CR_dat, RA_CR_dat, RT_SD_dat, RA_SD_dat;

    private static final int numVMs = 2_700_000;
    private static final boolean fast = !!!!!!(!!true) & false | !!true;

    public static void main(String[] args) throws IOException {
        FileOutputStream logStream;
        try{
            logStream = new FileOutputStream("simulation_logs.txt");
            Log.setOutput(logStream);
            //Log.setDisabled(true);
        }catch(Exception ex){
            System.out.println("wtf");
            return;
        }
        System.out.println("Reading from input files:\n\t" +
                "- VM data from Microsoft Azure (" + vm_path + ")\n\t- " +
                "MOER data from WattTime.com (" + moer_path + ")\n\n\n");

        new AlgRunner(vm_path, moer_path, numVMs, fast);

        // without any change (original run)
        noAlg_dat = AlgRunner.runCycle("No Algorithm (do nothing)", () -> new double[]{0, 0}, (Vm vm_dat) -> {
        }, sim, svm);
        AlgRunner.setBaseResult(noAlg_dat);

        System.out.println("Press 's' to simulate using default algorithm parameters\n" +
                            "Otherwise, press 'a' to enter adjustment mode. In adjustment mode, you can fine calibrate the parameters for optimal savings.");

        Scanner scan = new Scanner(System.in);

        char mode = scan.next().charAt(0);
        if(mode == 's') {
            Algorithms algos = new Algorithms();

            runFull(algos);
        }
        else if(mode == 'a')
        {
            fullOutput = false;
            int moer_thresh = 810, confidence_thresh = 50, day = 288;
            double u_idle = 0.01, p95Thresh = 0.8, wasteThresh = 6;
            Algorithms algos;

            while(true)
            {
                System.out.print("Which algorithm to adjust? ('1' : rt, '2' : ra, '3' : cr, '4' : sd, 'q' : quit adjuster, 'r' : run full simulation): ");

                try {
                    if (scan.hasNextInt())
                    {
                        switch (scan.nextInt())
                        {
                            case 1:
                                moer_thresh = scan.nextInt();
                                algos = new Algorithms(moer_thresh, confidence_thresh, p95Thresh, wasteThresh, u_idle);
                                RT_dat = AlgRunner.runCycle("Approach Using Intersections (RT)", algos::runRT, (Vm vm_dat) -> {
                                }, sim_RT, svm_RT);
                                break;
                            case 2:
                                confidence_thresh = scan.nextInt();
                                algos = new Algorithms(moer_thresh, confidence_thresh, p95Thresh, wasteThresh, u_idle);
                                RA_dat = AlgRunner.runCycle("Approach Using Moving Averages (RA)", algos::runRA, (Vm vm_dat) -> {
                                }, sim_RA, svm_RA);

                                break;
                            case 3:
                                p95Thresh = scan.nextDouble();
                                wasteThresh = scan.nextDouble();
                                algos = new Algorithms(moer_thresh, confidence_thresh, p95Thresh, wasteThresh, u_idle);
                                core_reduction_dat = AlgRunner.runCycle("Core Reduction Strategy (CR)", () -> new double[]{0, 0}, algos::runCR, sim_CR, svm_CR);
                                break;
                            case 4:
                                u_idle = scan.nextInt();
                                algos = new Algorithms(moer_thresh, confidence_thresh, p95Thresh, wasteThresh, u_idle);
                                shutdown_dat = AlgRunner.runCycle("VM Shutdown Strategy (SD)", () -> new double[]{0, 0}, algos::runSD, sim_SD, svm_SD);
                                break;
                            default:
                                System.out.println("Invalid flag, trying again...");
                                break;
                        }
                    }
                    else {
                        boolean br = false;
                        switch (scan.next().charAt(0)) {
                            case 'q':
                                br = true;
                                break;
                            case 'r':
                                algos = new Algorithms(moer_thresh, confidence_thresh, p95Thresh, wasteThresh, u_idle);
                                fullOutput = true;
                                runFull(algos);
                                fullOutput = false;
                                break;
                        }
                        if (br) break;
                    }
                }
                catch(Exception ex){
                    System.out.println("Invalid flags, trying again...");
                }
            }
        }
    }

    public static void runFull(Algorithms algos)
    {
        // moer-based algorithms
        RT_dat = AlgRunner.runCycle("Approach Using Intersections (RT)", algos::runRT, (Vm vm_dat) -> {
        }, sim_RT, svm_RT);
        RA_dat = AlgRunner.runCycle("Approach Using Moving Averages (RA)", algos::runRA, (Vm vm_dat) -> {
        }, sim_RA, svm_RA);

        // vm-based algorithms
        core_reduction_dat = AlgRunner.runCycle("Core Reduction Strategy (CR)", () -> new double[]{0, 0}, algos::runCR, sim_CR, svm_CR);
        shutdown_dat = AlgRunner.runCycle("VM Shutdown Strategy (SD)", () -> new double[]{0, 0}, algos::runSD, sim_SD, svm_SD);
        // moer-based + core reduction
        RT_CR_dat = AlgRunner.runCycle("RT and CR", algos::runRT, algos::runCR, sim_RT_CR, svm_RA_CR);
        RA_CR_dat = AlgRunner.runCycle("RA and CR", algos::runRA, algos::runCR, sim_RA_CR, svm_RA_CR);

        // moer-based + shutdown
        RT_SD_dat = AlgRunner.runCycle("RT and SD", algos::runRT, algos::runSD, sim_RT_SD, svm_RT_SD);
        RA_SD_dat = AlgRunner.runCycle("RA and SD", algos::runRA, algos::runSD, sim_RA_SD, svm_RA_SD);
    }
}
