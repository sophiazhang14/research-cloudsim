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
    private static final String vm_path = "vmtable_preprocessed_short.csv",
            moer_path = "CASIO_NORTH_2019_APRIL.csv";
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
    // realizing that not all users will accept the suggestions, we add an 'acceptanct rate'
    private static final double AUI_acceptance = 0.225, AUMA_acceptance = 0.175;


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

        new algRunner(vm_path, moer_path, numVMs);

        noAlg_dat = algRunner.runCycle("No Algorithm (do nothing)", () -> 0.0, (String[] vm_dat) -> vm_dat, sim_path, svmlist_path);
        AUI_dat = algRunner.runCycle("Approach Using Intersections (AUI)", Sim_filtered::runAUI, (String[] vm_dat) -> vm_dat, sim_with_AUI_path, svmlist_with_AUI_path);
        AUMA_dat = algRunner.runCycle("Approach Using Moving Averages (AUMA)", Sim_filtered::runAUMA, (String[] vm_dat) -> vm_dat, sim_with_AUMA_path, svmlist_with_AUMA_path);
    }



    /**
     * Here, we apply first carbon-saving algorithm (which shows a faster runtime than the second method) mentioned in paper: approach using intersections (AUI).
     *      For refrence: https://www.overleaf.com/project/631366e0dd56804a99c1de8a
     * This algorithm will adjust the start and end times of each vm such that they are moer-efficient (running at time with lower MOER).
     *
     * @todo may develop a hybrid algorithm between AUI & AUMA to find a compromise between runtime and moer-efficiency (later).
     * @return the average delay of vms (over all vms including vms that were not adjusted) in hrs
     */
    private static double runAUI()
    {
        //TODO: find suitibal moer threshold
        //todo: return average delay
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


        double sumDelay = 0.0;
        // simulate the adjustments of the vm start/end times
        for(Vm vm : algRunner.vmlist)
        {
            // not all users will accept the suggestion. this will be simulated with 'acceptance'.
            if(Math.random() > AUI_acceptance) continue;


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

            // search for a suitable time window
            for(; i < recWindows.size() && recWindows.get(i).start < vstart + day; i++)
            {
                mwindow currWindow = recWindows.get(i);

                // Ensure that window doesn't exceed how far the forecast can predict at that time.
                // (it is not possible to predict 24hrs/288index ahead of time with our current model).
                if (currWindow.end > vstart + day) break;

                // Ensure that window can fit the runtime.
                if (currWindow.length < runlength) continue;

                // Ensure that relocating here does save moer.
                if(currWindow.avgPMOER >= vm.getAveragePMOER()) continue;

                // TODO: change so that vm is moved to center of window
                // Adjust VM start & end!
                sumDelay += currWindow.start * 300 - vm.getTime()[0];
                vm.setTime(new int[]{currWindow.start * 300, (currWindow.start + runlength) * 300});
                break;
            }
        }

        double averageDelay = sumDelay / algRunner.vmlist.size(); // in seconds
        return averageDelay / 3600; // in hrs
    }

    /**
     * Here, we apply our second algorithm, Aproach Using Moving Averages (AUMA).
     * @return the average postponement of each vm (including vms that were not adjusted) in hrs.
     */
    private static double runAUMA()
    {
        //todo: return average delay
        ArrayList<Integer> prefPMOER = new ArrayList<>(); prefPMOER.add(algRunner.PMOER.get(0));
        for(int i = 1; i < algRunner.PMOER.size(); i++) prefPMOER.add(prefPMOER.get(i - 1) + algRunner.PMOER.get(i));

        double sumDelay = 0.0;
        for(Vm vm : algRunner.vmlist)
        {
            // account for the chance that user declines suggestion here
            if(Math.random() > AUMA_acceptance) continue;


            int vend = vm.getTime()[1] / 300,  vstart = vm.getTime()[0] / 300, runlength = vend - vstart;

            // find and save to window with both:
            // - the same time length as the vm runtime
            // - the minimum average MOER
            int ni = vstart, nj = vend; double minMOER = vm.getAverageMOER();
            for(int i = vstart + 1; i + runlength < Math.min(vstart + 288, algRunner.PMOER.size()); i++) //todo: check for 1-off errors
            {
                int j = i + runlength;
                int wsum = prefPMOER.get(j) - prefPMOER.get(i);
                double avgMOER = (double)wsum / (j - i);

                if(avgMOER < minMOER)
                {
                    minMOER = avgMOER;
                    ni = i; nj = j;
                }
            }

            // modify the start and end times of the vm according to the window found in loop above^
            int nstart = ni * 300, nend = nj * 300;
            sumDelay += nstart - vm.getTime()[0];
            vm.setTime(new int[]{nstart, nend});
        }

        double averageDelay = sumDelay / algRunner.vmlist.size(); // in seconds
        return averageDelay / 3600; // in hrs
    }
}
