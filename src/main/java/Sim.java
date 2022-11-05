import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;

@SuppressWarnings("SpellCheckingInspection")
public class Sim {

    // string constants
    private static final String
            COMMA_DELIMITER = ",";

    // file path/name constants
    // all files listed are located at "cloudsim\[filename]"
    private static final String

            //input files
            shortlist_path = "vmtable_preprocessed_short.csv",
            moer_path = "CASIO_NORTH_2019_APRIL.csv",

            //output files
            sim_path = "sim.csv",
            sim_with_AUI_path = "sim_after_AUI.csv",
            svmlist_path = "simulated_vms.csv",
            svmlist_with_AUI_path = "simulated_vms_after_AUI.csv";

    private static final DecimalFormat dft = new DecimalFormat("###.###");

    private static final int numVMs = 1000;

    // lists
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;
    // MOER data (index represents how many 5-minute intervals have passed since start of month, value represents MOER (CO2 lbs/MWh) at that time)
    private static List<Integer> MOER, PMOER;

    // datacenter-related
    private static Datacenter[] datacenters;
    private static DatacenterBroker broker;

    // carbon & waste (is updated after each simulation)!
    private static double carbon;
    private static double waste;

    /**
     * Initialize datacenter(s).
     */
    private static void init_datacenters()
    {
        for(int i = 0; i < datacenters.length; i++)
            datacenters[i] = createDatacenter("Datacenter_" + i);
    }

    /**
     * Initialize moer data from one month (same time interval as VM shortlist timestamps).
     *
     * @throws IOException b/c reading from file...
     */
    private static void init_MOER() throws IOException
    {
        BufferedReader br = new BufferedReader(new FileReader(moer_path));

        String line;
        br.readLine(); // flush the useless header line

        while((line = br.readLine()) != null)
        {
            String[] values = line.split(COMMA_DELIMITER);
            MOER.add((int)Double.parseDouble(values[0]));
            PMOER.add((int)Double.parseDouble(values[1]));
        }

        br.close();
    }

    /**
     * Initialize the VM list. Vm data from VM shortlist.
     *
     * @throws IOException b/c reading from file...
     */
    private static void init_VMs(Runnable adjuster) throws IOException
    {

        //Fourth step: Create VMs
        int brokerId = broker.getId();
        BufferedReader br = new BufferedReader(new FileReader(shortlist_path));

        UtilizationModel utilizationModel = new UtilizationModelFull();

        // read data from vmtable.csv
        String line;
        br.readLine(); // flush the useless header line
        for(int i = 0; i < numVMs; i++)
        {
            if ((line = br.readLine()) == null) break;

            String[] values = line.split(COMMA_DELIMITER);
            int
                    vmid = vmlist.size(), // Vm ID
                    ram = (values[7].equals(">64")) ? 70 :
                            ((values[10].equals(">24"))? 30 :
                                    (int)Double.parseDouble(values[7]) * 1000), //RAM in MB
                    numCPUCore = (values[6].equals(">64")) ? 70 :
                            ((values[9].equals(">24"))? 30: (int)Double.parseDouble(values[6])), //Number of CPUs requested
                    mips = 1000, // Million instructions per second (using default value of 1000)
                    bw = 1000, // bandwidth (using default value of 1000)
                    size = 10000, // idk what this size is specifically referring to
                    startTime = 300 * (int)(Double.parseDouble(values[0])),
                    endTime = 300 * (int)(Double.parseDouble(values[1]));
            double
                    avgUtil = Double.parseDouble(values[3]);
            String
                    vmm = "Windows Hyper-V"; // Azure uses this virtual machine manager (hypervisor)

            Vm vm = new Vm(
                    vmid,
                    brokerId,
                    mips,
                    numCPUCore,
                    ram,
                    bw,
                    size,
                    vmm,
                    avgUtil,
                    startTime,
                    endTime,
                    MOER,
                    PMOER,
                    new CloudletSchedulerTimeShared());
            vmlist.add(vm);
        }

        adjuster.run();

        for(int i = 0; i < numVMs; i++)
        {
            Vm currVm = vmlist.get(i);

            int pesNumber=1;
            long length = (long) (currVm.getTime()[1] - currVm.getTime()[0]) * (long) currVm.getMips();
            long fileSize = 300;
            long outputSize = 300;

            Cloudlet cloudlet1 =
                    new Cloudlet(
                            i,
                            length,
                            pesNumber,
                            fileSize,
                            outputSize,
                            utilizationModel,
                            utilizationModel,
                            utilizationModel,
                            currVm.getTime()[0]);
            cloudlet1.setUserId(brokerId);

            cloudletList.add(cloudlet1);
        }

        br.close();
    }

    /**
     * initialize data: CloudSim, datacenters, broker, VMs, MOER.
     * (calls init_MOER + init_VMs + init_datacenters)
     */
    private static void init_data(Runnable adjuster) {
        vmlist = new ArrayList<>();
        cloudletList = new ArrayList<>();
        MOER = new ArrayList<>(); PMOER = new ArrayList<>();
        datacenters = new Datacenter[100];

        // First step: Initialize the CloudSim package. It should be called
        // before creating any entities.
        int num_user = 1;   // number of cloud users
        Calendar calendar = Calendar.getInstance();

        // Initialize the CloudSim library
        CloudSim.init(num_user, calendar, false);

        // Second step: Create Datacenters
        //Datacenters are the resource providers in CloudSim. We need at list one of them to run a CloudSim simulation
        init_datacenters();

        // Fourth step: create vms
        broker = createBroker();
        try{
            init_MOER();
            init_VMs(adjuster);
        }
        catch (Exception ex) // data initialization from .csv failed somehow
        {
            ex.printStackTrace();
        }

        //submit vm list to the broker
        broker.submitVmList(vmlist);

        //submit cloudlet list to the broker
        broker.submitCloudletList(cloudletList);


        //bind the cloudlets to the vms.
        for(int i = 0; i < vmlist.size(); i++) broker.bindCloudletToVm(cloudletList.get(i).getCloudletId(), vmlist.get(i).getId());
    }

    public static void main(String[] args) {
        FileOutputStream logStream;
        try{
            logStream = new FileOutputStream("simulation_logs.txt");
            Log.setOutput(logStream);
        }catch(Exception ex){
            System.out.println("wtf");
            return;
        }

        Log.printLine("|--------------SIMULATION WITHOUT ALGORITHM STARTS HERE--------------|");
        Log.print("\n\n\n\n\n");

        /* Initialize refrences, csv data, cloudSim, brokers, etc...
         */
        init_data(() -> {});

        /* Run the cloud simulation using original start-end times.
         * Writes cloudlet results to 'sim.csv'
         * Writes vms that were simulated to 'simulated_vms.csv'*/
        simRun(sim_path, svmlist_path);
        double carbon_without_algorithm = carbon,
                waste_without_algorithm = waste;
        printResults("No Algorithm");

        Log.print("\n\n\n\n\n");
        Log.printLine("|--------------SIMULATION WITH AUI STARTS HERE--------------|");
        Log.print("\n\n\n\n\n");


        /* Initialize refrences, csv data, cloudSim, brokers, etc... (again bc we are restarting)
         * Then, run our MOER/CO2-saving algorithm! (this will modify start-end times of VM runtimes)
         */
        init_data(Sim::runAUI);

        /* Re-run the cloud simulation using start-end times that were adjusted by our algorithm.
         * Writes new cloudlet results to 'sim_after_AUI.csv'
         * Writes vms (with now adjusted times) that were simulated to 'simulated_vms_after_AUIrithm.csv'*/
        simRun(sim_with_AUI_path, svmlist_with_AUI_path);
        double carbon_with_AUI = carbon,
                waste_with_AUI = waste;
        printResults("Approach Using Intersections (AUI)");
    }

    private static void simRun(String cloudletFN, String vmFN)
    {

        // Sixth step: Starts the simulation
        CloudSim.startSimulation();


        // Final step: Print results when simulation is over
        List<Cloudlet> newList = broker.getCloudletReceivedList();

        CloudSim.stopSimulation();

        try
        {
            FileOutputStream vmstream = new FileOutputStream(vmFN);
            printVMList(vmlist, vmstream);
            vmstream.close();
            FileOutputStream fileOutputStream = new FileOutputStream(cloudletFN);
            printCloudletList(newList, fileOutputStream);
            fileOutputStream.close();
        } catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }

    /**
     * Here, we apply first algorithm (which shows a faster runtime than the second method) mentioned in paper: approach using intersections (AUI).
     *      For refrence: https://www.overleaf.com/project/631366e0dd56804a99c1de8a
     * This algorithm will adjust the start and end times of each vm such that they are moer-efficient (running at time lower MOER).
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
        if (PMOER.get(0) - moer_thresh >= 0) sign = 1;
        else sign = -1;
        wavg += PMOER.get(0);

        // find possible windows
        for(int i = 1; i < PMOER.size(); i++)
        {
            if (PMOER.get(i) - moer_thresh >= 0 && sign == -1) // crossed threshold (below -> above)
            {
                wavg /= i - wstart;
                sign = 1;
                recWindows.add(new mwindow(wstart, i, wavg)); // add window to possible windows to recommend
            }
            else if (PMOER.get(i) - moer_thresh < 0 && sign == 1) // crossed threshold (above -> below)
            {
                wstart = i;
                wavg = 0;
                sign = -1;
            }
            // we add at end of the loop instead of start
            // this is bc the moer value represents the avg moer for the *next* 5 min.
            wavg += PMOER.get(i);
        }

        // simulate the adjustments of the vm start/end times
        for(Vm vm : vmlist)
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

    private static Datacenter createDatacenter(String name)
    {

        // Here are the steps needed to create a PowerDatacenter:
        // 1. We need to create a list to store
        //    our machine
        List<Host> hostList = new ArrayList<>();

        // 2. A Machine contains one or more PEs or CPUs/Cores.
        // In this example, it will have only one core.
        List<Pe> peList = new ArrayList<>();

        int mips = 100_000;

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating

        //4. Create Host with its id and list of PEs and add them to the list of machines
        int hostId=0;
        int ram = Integer.MAX_VALUE; //host memory (MB)
        long storage = Integer.MAX_VALUE; //host storage
        int bw = Integer.MAX_VALUE;

        hostList.add(
                new Host(
                        hostId,
                        new RamProvisionerSimple(ram),
                        new BwProvisionerSimple(bw),
                        storage,
                        peList,
                        new VmSchedulerTimeShared(peList)
                )
        ); // This is our machine


        // 5. Create a DatacenterCharacteristics object that stores the
        //    properties of a data center: architecture, OS, list of
        //    Machines, allocation policy: time- or space-shared, time zone
        //    and its price (G$/Pe time unit).
        String arch = "x86";      // system architecture
        String os = "Linux";          // operating system
        String vmm = "Windows Hyper-V";
        double time_zone = 10.0;         // time zone this resource located
        double cost = 3.0;              // the cost of using processing in this resource
        double costPerMem = 0.05;		// the cost of using memory in this resource
        double costPerStorage = 0.001;	// the cost of using storage in this resource
        double costPerBw = 0.0;			// the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<>();	//we are not adding SAN devices by now

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);


        // 6. Finally, we need to create a PowerDatacenter object.
        Datacenter datacenter = null;
        try
        {
            datacenter = new Datacenter(
                    name,
                    characteristics,
                    new VmAllocationPolicySimple(hostList),
                    storageList,
                    0);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return datacenter;
    }

    private static DatacenterBroker createBroker()
    {

        DatacenterBroker broker;
        try
        {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    /**
     * Prints the Cloudlets' final states to a file
     *
     * @param list list of Cloudlets
     * @param ostream output stream (file/console)
     */
    private static void printCloudletList(List<Cloudlet> list, OutputStream ostream) {
        carbon = 0.0;
        waste = 0.0;
        OutputStream prevOStream = Log.getOutput();
        Log.setOutput(ostream);

        Cloudlet cloudlet;

        Log.formatLine(
                "%-13s, %-13s, %-13s, %-14s, %-13s, %-17s, %-17s, %-17s, %-17s, %-17s",
                "STATUS",
                "Cloudlet ID",
                "User ID",
                "Datacenter ID",
                "VM ID",
                "Run Length (sec)",
                "Start Time (sec)",
                "Finish Time (sec)",
                "Carbon Emitted (lbs)",
                "Wasted Money ($)");

        for (Cloudlet value : list) {
            cloudlet = value;
            Log.format("%-13s", (cloudlet.getStatus() == Cloudlet.SUCCESS) ? "SUCCESS" : "FAIL");

            Log.formatLine(
                    ", %-13s, %-13s, %-14s, %-13s, %-17s, %-17s, %-17s, %-17s, %-17s",
                    cloudlet.getCloudletId(),
                    cloudlet.getUserId(),
                    cloudlet.getResourceId(),
                    cloudlet.getVmId(),
                    dft.format(cloudlet.getActualCPUTime()),
                    dft.format(cloudlet.getExecStartTime()),
                    dft.format(cloudlet.getFinishTime()),
                    dft.format(cloudlet.getTotalEmissions()),
                    dft.format(cloudlet.getTotalWaste()));
            carbon += cloudlet.getTotalEmissions();
            waste += cloudlet.getTotalWaste();
        }

        Log.setOutput(prevOStream);
    }

    /**
     * Prints the VMs that were simulated to a file
     * @param list list of VMs
     * @param ostream output stream (file/console)
     */
    private static void printVMList(List<Vm> list, OutputStream ostream)
    {
        OutputStream prevOStream = Log.getOutput();
        Log.setOutput(ostream);
        Log.formatLine("%-14s, %-12s, %-12s, %-14s, %-18s, %-16s, %-14s, %-14s", "vm id (in sim)", "user id", "ram (GB)", "num CPU", "power (watt)", "avg. util (%)", "start (sec)", "end (sec)");
        for(Vm vm : list)
        {
            Log.formatLine("%-14s, %-12s, %-12s, %-14s, %-18s, %-16s, %-14s, %-14s",
                    vm.getId(),
                    vm.getUserId(),
                    vm.getRam() / 1000,
                    vm.getNumberOfPes(),
                    String.format("%.2f", vm.getPower()),
                    String.format("%.2f", vm.getPercentUtilization()),
                    vm.getTime()[0],
                    vm.getTime()[1]);
        }
        Log.setOutput(prevOStream);
    }

    /**
     * Prints the final results of a simulation to console
     */
    private static void printResults(String algName)
    {
        System.out.print(algName + ":\nCarbon: " + carbon + " lbs CO2\nMoney wasted by user: $" + waste + "\n\n");
    }
}
