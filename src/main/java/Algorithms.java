import org.cloudbus.cloudsim.Vm;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class Algorithms {

    // algorithm constants

    // realizing that not all users will accept the suggestions, we add an 'acceptance rate'
    /** note to dev: setting all to 1 for consistency when finding max possible emissions*/
    private static final double AUI_acceptance = 1, AUMA_acceptance = 1;
    private static final double shutdown_acceptance = 1, core_reduction_acceptance = 1;

    // adjustable moer threshold
    private static final int moer_thresh = 810;
    private static final int day = 288;

    private static final double u_idle = 0.01;

    // contains all possible core counts of vms
    private static final int[] CC_VALS = new int[]{2, 4, 8, 12, 24, 30};

    // stores the threshold for the max p95.
    // it is assumed that once the cpu utilization exceeds this percentage, the vm will experience some kind of performance degradation or lag.
    private static final double p95thresh = 0.8;


    private static long lastStart;
    private static final DecimalFormat dft = new DecimalFormat("##############0.########");


    /**---------------------------------------------- MOER BASED ALGORITHMS START HERE -------------------------------------------------*/

    /**
     * Here, we apply first carbon-saving algorithm (which shows a faster runtime than the second method) mentioned in paper: approach using intersections (AUI).
     *      For refrence: https://www.overleaf.com/project/631366e0dd56804a99c1de8a
     * This algorithm will adjust the start and end times of each vm such that they are moer-efficient (running at time with lower MOER).
     *
     * @todo may develop a hybrid algorithm between AUI & AUMA to find a compromise between runtime and moer-efficiency (later).
     * @return the average delay of vms (over all vms including vms that were not adjusted) in hrs
     */
    public static double[] runAUI()
    {
        startHere();

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
        if (AlgRunner.PMOER.get(0) - moer_thresh >= 0) sign = 1;
        else sign = -1;
        wavg += AlgRunner.PMOER.get(0);

        // find possible windows
        for(int i = 1; i < AlgRunner.PMOER.size(); i++)
        {
            if (AlgRunner.PMOER.get(i) - moer_thresh >= 0 && sign == -1) // crossed threshold (below -> above)
            {
                wavg /= i - wstart;
                sign = 1;
                recWindows.add(new mwindow(wstart, i, wavg)); // add window to possible windows to recommend
            }
            else if (AlgRunner.PMOER.get(i) - moer_thresh < 0 && sign == 1) // crossed threshold (above -> below)
            {
                wstart = i;
                wavg = 0;
                sign = -1;
            }
            // we add at end of the loop instead of start
            // this is bc the moer value represents the avg moer for the *next* 5 min.
            wavg += AlgRunner.PMOER.get(i);
        }


        double sumDelay = 0.0;
        int numAcc = 0;
        // simulate the adjustments of the vm start/end times
        for(Vm vm : AlgRunner.vmflist)
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
                numAcc++;
                vm.setTime(new int[]{currWindow.start * 300, (currWindow.start + runlength) * 300});
                break;
            }
        }

        printDuration("run AUI");
        double averageDelay = sumDelay / AlgRunner.vmlist.size(); // in seconds
        return new double[]{averageDelay / 3600, sumDelay / numAcc / 3600}; // in hrs
    }

    /**
     * Here, we apply our second algorithm, Aproach Using Moving Averages (AUMA).
     * @return the average postponement of each vm (including vms that were not adjusted) in hrs.
     */
    public static double[] runAUMA()
    {
        startHere();
        ArrayList<Integer> prefPMOER = new ArrayList<>(); prefPMOER.add(AlgRunner.PMOER.get(0));
        for(int i = 1; i < AlgRunner.PMOER.size(); i++)
            prefPMOER.add(prefPMOER.get(i - 1) + AlgRunner.PMOER.get(i));

        final Function<Integer[], Integer> rsum = (Integer[] i) -> (prefPMOER.get(i[1]) - prefPMOER.get(i[0]));

        double sumDelay = 0.0;
        int numAcc = 0;
        for(Vm vm : AlgRunner.vmflist)
        {
            // account for the chance that user declines suggestion here
            if(Math.random() > AUMA_acceptance) continue;


            int vend = vm.getTime()[1] / 300,  vstart = vm.getTime()[0] / 300, runlength = vend - vstart;

            // find and save to window with both:
            // - the same time length as the vm runtime
            // - the minimum average MOER
            int ni = vstart, nj = vend; double origMOER = vm.getAveragePMOER();
            for(int i = vstart + 1; i + runlength < Math.min(vstart + day, AlgRunner.PMOER.size()) - 1; i++)
            {
                int j = i + runlength;
                double
                        wavg = (double)rsum.apply(new Integer[]{i, j}) / runlength,
                        pwavg = (double)rsum.apply(new Integer[]{i - 1, j - 1}) / runlength,
                        navg = (double)rsum.apply(new Integer[]{i + 1, j + 1}) / runlength;

                if(wavg <= pwavg && wavg <= navg && origMOER > wavg)
                {
                    ni = i; nj = j;
                    break;
                }
            }

            //if there was no change:
            if(ni == vstart && nj == vend) continue;

            // modify the start and end times of the vm according to the window found in loop above^
            int nstart = ni * 300, nend = nj * 300;
            sumDelay += nstart - vm.getTime()[0];
            numAcc++;
            vm.setTime(new int[]{nstart, nend});
        }

        printDuration("run AUMA");
        double averageDelay = sumDelay / AlgRunner.vmlist.size(); // in seconds
        return new double[]{averageDelay / 3600, sumDelay / numAcc / 3600}; // in hrs
    }


    /**---------------------------------------------- VM BASED ALGORITHMS START HERE -------------------------------------------------*/


    /**
     * Here, we apply core-reduction on *one* given VM.
     * @param vm the data of the VM to be adjusted.
     */
    public static void runCR(Vm vm)
    {
        int cpuCores = vm.getNumberOfPes();
        double p95 = vm.getP95();
        double max_util = vm.getMax_util(), avg_util = vm.getAvg_util();

        // filter un-reducable vms
        if(p95 >= p95thresh) return;

        /*
        Code below is subject to change bc users might have more options for number of cores other than those listed in the array 'CC_VALS'
        (e.g. core counts that are 1, 3, 5, 6, 7, etc.)

        Current code assumes that the vm user can only change their core count to one count listed in 'CC_VALS'.
         */
        int newCpuCores = cpuCores; double new_p95 = p95, new_max_util = max_util, new_avg_util = avg_util;
        for(int i = 0; i < CC_VALS.length; i++)
        {
            if(CC_VALS[i] > cpuCores) break;
            new_p95 = p95 * cpuCores / CC_VALS[i];
            new_max_util = max_util * cpuCores / CC_VALS[i];
            new_avg_util = avg_util * cpuCores / CC_VALS[i];
            if(new_p95 < p95thresh && new_max_util <= 1)
            {
                newCpuCores = CC_VALS[i];
                break;
            }
        }

        if(newCpuCores >= cpuCores || Math.random() > core_reduction_acceptance) return;

        vm.setNumberOfPes(newCpuCores);
        vm.setP95(new_p95);
        vm.setMax_util(new_max_util);
        vm.setAvg_util(new_avg_util);
    }

    /**
     * Here, we apply the shutdown strategy on *one* given VM.
     * @param vm the VM to be adjusted (represented as a list of strings)
     */
    public static void runSD(Vm vm)
    {
        double u_max = vm.getMax_util(), u_avg = vm.getAvg_util();
        int t_created = vm.getTime()[0], t_deleted = vm.getTime()[1], t_full = t_deleted - t_created;

        // check criteria
        if((u_max - u_idle) / (u_avg - u_idle) <= 10) return;

        // not all users will accept the recommendation.
        if(Math.random() > shutdown_acceptance) return;

        //simulate shutting down the vm by reducing the runtime length here.
        int t_max = (int) (t_full * (u_avg - u_idle) / (u_max - u_idle));
        int t_new_deleted = t_created + t_max;
        vm.setAvg_util(u_max);
        vm.setP95(u_max);
        vm.setTime(new int[]{t_created, t_new_deleted});
    }


    /**-------------Below are diagnostic functions----------*/

    private static void startHere()
    {
        lastStart = System.nanoTime();
    }

    private static void printDuration(String s)
    {
        System.out.println("Took " + dft.format((double)(System.nanoTime() - lastStart) / 1e9) + "s to " + s);
    }
}
