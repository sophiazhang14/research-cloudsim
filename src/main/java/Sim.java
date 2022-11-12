/*

defs:
waste ($): money wasted from unused VM cpu cores (from 1 simulation)
carbon/carbon emissions (lbs CO2): carbon emitted due to execution of all Cloudlets (from 1 simulation).

 */




import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("SpellCheckingInspection")
public class Sim {


    // file path/name constants
    //output files
    private static String sim_path = "sim.csv",
            sim_with_AUI_path = "sim_with_AUI.csv",
            sim_with_shutdown_path = "sim_with_shudown",
            svmlist_path = "simulated_vms.csv",
            svmlist_with_AUI_path = "simulated_vms_after_AUI.csv",
            svmlist_with_shutdown_path = "simulated_vms_after_AUI.csv";

    // each of the following are updated once
    // these contain the carbon and waste produced by their corresponding simulation, indicated by their name.
    private static double[] noAlg_dat;
    private static double[] AUI_dat;
    private static double[] shutDown_dat;


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

        noAlg_dat = algRunner.runCycle("No Algorithm", () -> {}, (Vm vm) -> {}, sim_path, svmlist_path);
        AUI_dat = algRunner.runCycle("Approach Using Intersections (AUI)", Sim::runAUI, Sim::runWR, sim_with_AUI_path, svmlist_with_AUI_path);
        shutDown_dat = algRunner.runCycle("VM Shutdown Strategy", () -> {}, Sim::runShutDown, sim_with_shutdown_path, svmlist_with_shutdown_path);
    }



    /**
     * Here, we apply first carbon-saving algorithm (which shows a faster runtime than the second method) mentioned in paper: approach using intersections (AUI).
     *      For refrence: https://www.overleaf.com/project/631366e0dd56804a99c1de8a
     * This algorithm will adjust the start and end times of each vm such that they are moer-efficient (running at time with lower MOER).
     *
     * @todo may develop a hybrid algorithm between AUI & AUMA to find a compromise between runtime and moer-efficiency (later).
     */
    private static void runAUI()
    {
        // TODO find suitibal moer threshold
        final int moer_thresh = 800;
        final int day = 288;

        class mwindow
        {
            // bounds of window
            // these times are in INDEXES (index = 5 min).
            final int start, end, length;

            // avg moer over window
            final double avgPMOER;

            mwindow(int start, int end, double avg){this.start = start; this.end = end; this.avgPMOER = avg; length = end - start;}
        }

        List<mwindow> recWindows = new ArrayList<>();

        int sign, wstart = 0; double wavg = 0.0;
        if (algRunner.PMOER.get(0) - moer_thresh >= 0) sign = 1;
        else sign = -1;
        wavg += algRunner.PMOER.get(0);

        // find possible windows
        for(int i = 1; i < algRunner.PMOER.size(); i++)
        {
            if (algRunner.PMOER.get(i) - moer_thresh >= 0 && sign == -1) // crossed threshold (below -> above)
            {
                wavg /= i - wstart;
                sign = 1;
                recWindows.add(new mwindow(wstart, i, wavg)); // add window to possible windows to recommend
            }
            else if (algRunner.PMOER.get(i) - moer_thresh < 0 && sign == 1) // crossed threshold (above -> below)
            {
                wstart = i;
                wavg = 0;
                sign = -1;
            }
            // we add at end of the loop instead of start
            // this is bc the moer value represents the avg moer for the *next* 5 min.
            wavg += algRunner.PMOER.get(i);
        }

        // simulate the adjustments of the vm start/end times
        for(Vm vm : algRunner.vmlist)
        {
            int vstart = vm.getTime()[0] / 300, vend = vm.getTime()[1] / 300;
            int runlength = vend - vstart;

            // use binary search to ensure that we start with a window that is not before the time that the vm runs.
            // (we don't want to consider windows that have already passed)
            int i = Collections.binarySearch(recWindows, new mwindow(vstart, vend, 0.0), (o1, o2) -> {
                if (o1.start < o2.start) return -1;
                else if (o1.start > o2.start) return 1;
                return 0;
            });
            if(i < 0)
                i = -i - 1;

            for(; i < recWindows.size() && recWindows.get(i).start < vstart + day; i++)
            {
                mwindow currWindow = recWindows.get(i);

                // Ensure that window doesn't exceed how far the forecast can predict at that time.
                // (it is not possible to predict 24hrs/288index ahead of time with out current model).
                if (currWindow.end > vstart + day) break;

                // Ensure that window can fit the runtime.
                if (currWindow.length < runlength) continue;

                // Ensure that relocating here does save moer.
                if(currWindow.avgPMOER >= vm.getAveragePMOER()) continue;

                // TODO change so that vm is moved to center of window
                // Adjust VM start & end!
                vm.setTime(new int[]{currWindow.start * 300, (currWindow.start + runlength) * 300});
                break;
            }
        }
    }


    private static void runAUMA()
    {

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
     * @param vm the VM to be adjusted.
     */
    private static void runWR(Vm vm)
    {
        //TODO implement waste reduction
    }

    /**
     * TO ALL VMS
     * Shutdown Policies:
     * -
     */

    private static void runShutDown(Vm vm)
    {
        //TODO
    }
}
