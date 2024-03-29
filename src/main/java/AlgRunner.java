import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AlgRunner {

    // string constants
    private static final String
            COMMA_DELIMITER = ",";

    //input paths
    private static String

            vm_path,
            moer_path;

    private static final DecimalFormat dft = new DecimalFormat("##############0.###");

    private static int numVMs, numDCs = 2;
    private static boolean fast;

    // lists
    private static List<Cloudlet> cloudletList;
    public static List<Vm> vmlist, vmflist;
    // MOER data (index represents how many 5-minute intervals have passed since start of month, value represents MOER (CO2 lbs/MWh) at that time)
    public static List<Integer> MOER, PMOER;

    // datacenter-related
    private static Datacenter[] datacenters;
    private static DatacenterBroker broker;

    // carbon & waste (is updated after each simulation)!
    public static double lastCarbon, lastWaste;
    public static double[] lastDelay;

    private static long lastStart;
    private static double[] baseResult;

    // constructor sets the input paths
    public AlgRunner(String vm_path, String moer_path, int numVMs, boolean fast) throws IOException
    {
        this.baseResult = null; this.vm_path = vm_path; this.moer_path = moer_path; this.numVMs = numVMs; this.fast = fast; this.MOER = new ArrayList<>(); this.PMOER = new ArrayList<>(); init_MOER();
    }
    public static void setBaseResult(double[] result)
    {
        baseResult = result;
    }
    //------------Below are initialization functions------------//

    private static Datacenter createDatacenter(String name)
    {

        // Here are the steps needed to create a PowerDatacenter:
        // 1. We need to create a list to store
        //    our machine
        List<Host> hostList = new ArrayList<>();

        for(int i = 0; i < 10; i++) {
            // 2. A Machine contains one or more PEs or CPUs/Cores.
            // In this example, it will have only one core.
            List<Pe> peList = new ArrayList<>();

            int mips = Integer.MAX_VALUE / 30;

            // 3. Create PEs and add these into a list.
            for(int j = 0; j < 30; j++)
                peList.add(new Pe(i * 30 + j, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating

            //4. Create Host with its id and list of PEs and add them to the list of machines
            int hostId = i;
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
        }

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
    private static void init_VMs(Supplier<double[]> carbon_adjuster, Consumer<Vm> vm_adjuster) throws IOException
    {

        //Fourth step: Create VMs
        int brokerId = broker.getId();
        BufferedReader br = new BufferedReader(new FileReader(vm_path));

        UtilizationModel utilizationModel = new UtilizationModelFull();

        // read data from vmtable.csv
        String line;

        //flush useless header line
        br.readLine();

        while(vmlist.size() < numVMs)
        {
            if ((line = br.readLine()) == null) break;

            String[] values = line.split(COMMA_DELIMITER);
            boolean missingValue = false; for(String s : values) if(s.equals("")) {missingValue = true; break;}
            if(missingValue) continue;

            int
                    vmid = vmlist.size(), // Vm ID
                    ram = (int)Double.parseDouble(values[6]) * 1000, //RAM in MB
                    numCPUCore = (int)Double.parseDouble(values[5]), //Number of CPUs requested
                    mips = 1000, // Million instructions per second (using default value of 1000)
                    bw = 1000, // bandwidth (using default value of 1000)
                    size = 10000, // idk what this size is specifically referring to
                    startTime = (int)(Double.parseDouble(values[0])),
                    endTime = (int)(Double.parseDouble(values[1]));
            double
                    avgUtil = Double.parseDouble(values[3]) / 100,
                    maxUtil = Double.parseDouble(values[2]) / 100,
                    p95 = Double.parseDouble(values[4]) / 100,
                    power = Double.parseDouble(values[8]);
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
                    maxUtil,
                    p95,
                    startTime,
                    endTime,
                    power,
                    MOER,
                    PMOER,
                    new CloudletSchedulerTimeShared());

            if(endTime - startTime >= 2100 && endTime - startTime <= 86100) vmflist.add(vm);
            vmlist.add(vm);
        }

        lastDelay = carbon_adjuster.get(); // adjust all vms' start+end times to reduce moer if possible

        for(int i = 0; i < vmlist.size(); i++)
        {
            Vm currVm = vmlist.get(i);

            //System.out.println(currVm);

            vm_adjuster.accept(currVm);

            //System.out.println(currVm);
            //System.out.println();

            // skip pointless stuff
            if(fast) continue;

            int pesNumber=1;
            long length = (long) (currVm.getTime()[1] - currVm.getTime()[0]) * (long) currVm.getMips();
            long fileSize = 300;
            long outputSize = 300;

            Cloudlet cloudlet =
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
            cloudlet.setUserId(brokerId);

            cloudletList.add(cloudlet);
        }

        br.close();
        System.out.println("Number of VMs included in simulation: " + vmlist.size());
    }

    /**
     * initialize data: CloudSim, datacenters, broker, VMs, MOER.
     * (calls init_MOER + init_VMs + init_datacenters)
     */
    private static void init_data(Supplier<double[]> carbon_adjuster, Consumer<Vm> vm_adjuster) {
        startHere();
        vmlist = new ArrayList<>(); vmflist = new ArrayList<>();
        cloudletList = new ArrayList<>();
        datacenters = new Datacenter[numDCs];

        // First step: Initialize the CloudSim package. It should be called
        // before creating any entities.
        int num_user = 1; // number of cloud users
        Calendar calendar = Calendar.getInstance();

        // Initialize the CloudSim library
        CloudSim.init(num_user, calendar, false);

        // Second step: Create Datacenters
        //Datacenters are the resource providers in CloudSim. We need at list one of them to run a CloudSim simulation
        init_datacenters();

        // Fourth step: create vms
        broker = createBroker();
        try{
            init_VMs(carbon_adjuster, vm_adjuster);
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
        if(!fast) for(int i = 0; i < vmlist.size(); i++) broker.bindCloudletToVm(cloudletList.get(i).getCloudletId(), vmlist.get(i).getId());
        printDuration("initialize data");
    }


    //------------Below are simulation functions-----------//


    /**
     * Runs one cycle of the program.
     * One cycle includes:
     *  initialization of the cloud simulation,
     *  **execution of carbon+waste-saving algorithm**,
     *  execution of CloudSim simulation,
     *  outputting results+data to files and console.
     *
     * @param name the name of the simulation
     * @param save_carbon **the Runnable referencing the carbon-saving algorithm function**
     * @param vm_adjuster **the Consumer referencing a function that adjusts the vms as they are read from file**
     * @param sp path to the output file to contain cloudlets' final states
     * @param svmlp path to the output file to contain adjusted vms
     * @return returns an array: {[carbon that was emitted (lbs CO2)], [wasted money ($)]}
     */
    public static double[] runCycle(String name, Supplier<double[]> save_carbon, Consumer<Vm> vm_adjuster, String sp, String svmlp)
    {
        Log.print("\n\n\n\n\n");
        Log.printLine("|--------------SIMULATION WITH \'" + name.toUpperCase() +"\' STARTS HERE--------------|");
        Log.print("\n\n\n\n\n");


        /* Initialize refrences, csv data, cloudSim, brokers, etc... (again bc we are starting/restarting)
         * ALSO, run our MOER/CO2-saving algorithm after initialization of vms from input csv! (this will modify start-end times of VM runtimes)
         */
        init_data(save_carbon, vm_adjuster);

        /* Re-run the cloud simulation using start-end times that were adjusted by our algorithm.
         * Writes new cloudlet results to file
         * Writes vms (with now adjusted times) that were simulated to file
         */
        if (!fast) simRunDisplay(sp, svmlp);
        else simRunFast(); // this doesn't write cloudlet/vm info to the files but that can be easily added if needed in the future.
        printResults(name);
        return new double[]{lastCarbon, lastWaste, lastDelay[0], lastDelay[1]};
    }

    /**
     * use when want to output more stuff
     * */
    private static void simRunDisplay(String cloudletFN, String vmFN)
    {
        startHere();
        CloudSim.startSimulation();


        List<Cloudlet> newList = broker.getCloudletReceivedList();

        CloudSim.stopSimulation();
        printDuration("run disp. cloudSim");
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
     * made a new sim function. reason: simRunDisplay is too slow
     * much simpler and faster "version" of cloudSim that only does what we need it to, with minimal amounts of head-cracking.
     * */
    private static void simRunFast()
    {
        //step 1: loop over vms.
        lastCarbon = 0; lastWaste = 0; startHere();
        for(Vm vm : vmlist) {lastCarbon += vm.getCarbon(); lastWaste += vm.getWaste();}
        printDuration("run faster simulation");
        //step 2: finished -_-
    }


    //-------------Below are output functions--------------//


    /**
     * Prints the Cloudlets' final states to a file
     *
     * @param list list of Cloudlets
     * @param ostream output stream (file/console)
     */
    private static void printCloudletList(List<Cloudlet> list, OutputStream ostream)
    {
        lastCarbon = 0.0;
        lastWaste = 0.0;
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
            lastCarbon += cloudlet.getTotalEmissions();
            lastWaste += cloudlet.getTotalWaste();
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
        Log.formatLine("%-10s, %-12s, %-12s, %-14s, %-18s, %-28s, %-28s, %-15s, %-28s",
                "vm id",
                "user id",
                "ram (GB)",
                "num CPU",
                "power (watt)",
                "avg. util (%)",
                "max util (%)",
                "p95 (%)",
                "(start (s), end (s))");
        for(Vm vm : list)
        {
            Log.printLine(vm.toString());
        }
        Log.setOutput(prevOStream);
    }

    /**
     * Prints the final results of a simulation to console
     */
    private static void printResults(String algName)
    {
        System.out.print(algName + ":" +
                ((SimMain.fullOutput) ?
                    "\n" +
                    "Total carbon emitted: " + dft.format(lastCarbon) + " lbs CO2\n" +
                    "Total money wasted by users: $" + dft.format(lastWaste) + "\n" +
                    "Average postponement of runtime over all VMs: " + dft.format(lastDelay[0]) + " hrs\n" +
                    "Average postponement of postponed VMs: " + dft.format(lastDelay[1]) + " hrs"
                    :
                    "\n" +
                    "Average postponement of runtime over all VMs: " + dft.format(lastDelay[0]) + " hrs\n" +
                    "Average postponement of postponed VMs: " + dft.format(lastDelay[1]) + " hrs")
        );
        if(baseResult != null)
            System.out.print("\n" +
                "Saved Carbon: " + dft.format(baseResult[0] - lastCarbon) + " lbs CO2\n" +
                "Saved Money: $" + dft.format(baseResult[1] - lastWaste)
            );
        System.out.print("\n\n\n");
    }


    //-------------Below are diagnostic functions----------//

    private static void startHere()
    {
        lastStart = System.currentTimeMillis();
    }

    private static void printDuration(String s)
    {
        if(SimMain.fullOutput) System.out.println("Took " + dft.format((double)(System.currentTimeMillis() - lastStart) / 1000) + "s to " + s);
    }
}
