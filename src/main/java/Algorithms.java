import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Algorithms {

    // algorithm constants

    // realizing that not all users will accept the suggestions, we add an 'acceptanct rate'
    private static final double AUI_acceptance = 0.225, AUMA_acceptance = 0.175;
    private static final double shutdown_acceptance = 0.65, core_reduction_acceptance = 0.7;

    // contains all possible core counts of vms
    private static final int[] CC_VALS = new int[]{2, 4, 8, 12, 24, 30};

    // stores the threshold for the max p95.
    // it is assumed that once the cpu utilization exceeds this percentage, the vm will experience some kind of performance degradation or lag.
    private static final double p95thresh = 80.0;


    /**---------------------------------------------- MOER BASED ALGORITHMS START HERE -------------------------------------------------*/

    /**
     * Here, we apply first carbon-saving algorithm (which shows a faster runtime than the second method) mentioned in paper: approach using intersections (AUI).
     *      For refrence: https://www.overleaf.com/project/631366e0dd56804a99c1de8a
     * This algorithm will adjust the start and end times of each vm such that they are moer-efficient (running at time with lower MOER).
     *
     * @todo may develop a hybrid algorithm between AUI & AUMA to find a compromise between runtime and moer-efficiency (later).
     * @return the average delay of vms (over all vms including vms that were not adjusted) in hrs
     */
    public static double runAUI()
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
        // simulate the adjustments of the vm start/end times
        for(Vm vm : AlgRunner.vmlist)
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

        double averageDelay = sumDelay / AlgRunner.vmlist.size(); // in seconds
        return averageDelay / 3600; // in hrs
    }

    /**
     * Here, we apply our second algorithm, Aproach Using Moving Averages (AUMA).
     * @return the average postponement of each vm (including vms that were not adjusted) in hrs.
     */
    public static double runAUMA()
    {
        //todo: return average delay
        ArrayList<Integer> prefPMOER = new ArrayList<>(); prefPMOER.add(AlgRunner.PMOER.get(0));
        for(int i = 1; i < AlgRunner.PMOER.size(); i++) prefPMOER.add(prefPMOER.get(i - 1) + AlgRunner.PMOER.get(i));

        double sumDelay = 0.0;
        for(Vm vm : AlgRunner.vmlist)
        {
            // account for the chance that user declines suggestion here
            if(Math.random() > AUMA_acceptance) continue;


            int vend = vm.getTime()[1] / 300,  vstart = vm.getTime()[0] / 300, runlength = vend - vstart;

            // find and save to window with both:
            // - the same time length as the vm runtime
            // - the minimum average MOER
            int ni = vstart, nj = vend; double minMOER = vm.getAverageMOER();
            for(int i = vstart + 1; i + runlength < Math.min(vstart + 288, AlgRunner.PMOER.size()); i++) //todo: check for 1-off errors
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

        double averageDelay = sumDelay / AlgRunner.vmlist.size(); // in seconds
        return averageDelay / 3600; // in hrs
    }

    /**---------------------------------------------- VM BASED ALGORITHMS START HERE -------------------------------------------------*/

    /**
     * Here, we apply the shutdown strategy on *one* given VM.
     * Shutdown Policy:
     *      if (maximum cpu utilization) / (average cpu utilization) > 5, then recommend a shutdown
     * @param vm_dat the VM to be adjusted (represented as a list of strings)
     */

    public static String[] runShutdown(String[] vm_dat)
    {
        double max_util = Double.parseDouble(vm_dat[2]), avg_util = Double.parseDouble(vm_dat[3]);
        int t_created = (int) Double.parseDouble(vm_dat[0]), t_deleted = (int) Double.parseDouble(vm_dat[1]), t_l = t_deleted - t_created;
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
    public static String[] runCR(String[] vm_dat)
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
