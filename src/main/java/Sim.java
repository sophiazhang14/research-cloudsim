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
    // all files listed are located at "cloudsim/[filename]"
    private static final String
            //input files
            shortlist_path = "vmtable_preprocessed_short.csv", /*note: if you wish to use a different sized version of the shortlist, you must change this path*/
            moer_path = "CAISO_NORTH_2022-04_MOER_T.csv", /*note: if you wish to use a different .csv, you must change this path*/
            //output files
            sim_path = "sim.csv",
            sim_with_algo_path = "sim_algo.csv",
            svmlist_path = "simulated_vms.csv";

    // lists
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;
    // MOER data (index represents how many 5-minute intervals have passed since start of month, value represents MOER at that time)
    private static List<Integer> MOER;

    // datacenter-related
    private static Datacenter[] datacenters;
    private static DatacenterBroker broker;

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

        String line = ""; int currID = 0;
        br.readLine(); // flush the useless header line

        while((line = br.readLine()) != null)
        {
            String[] values = line.split(COMMA_DELIMITER);
            MOER.add((int)Double.parseDouble(values[5]));
        }

        br.close();
    }

    /**
     * Initialize the VM list. Vm data from VM shortlist.
     *
     * @throws IOException b/c reading from file...
     */
    private static void init_VMs() throws IOException
    {

        //Fourth step: Create VMs
        int brokerId = broker.getId();
        BufferedReader br = new BufferedReader(new FileReader(shortlist_path));

        UtilizationModel utilizationModel = new UtilizationModelFull();

        // read data from vmtable.csv
        String line = ""; int currID = 0;
        br.readLine(); // flush the useless header line
        for(int i = 0; i < 100; i++)
        {
            if((line = br.readLine()) == null) break;

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
                    endTime = 300 * (int)(Double.parseDouble(values[1])),
                    timeTot = (int)(Double.parseDouble(values[8]));
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
                    new CloudletSchedulerTimeShared());
            vmlist.add(vm);

            int pesNumber=1;
            long length = (long) timeTot * mips;
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
                            startTime);
            cloudlet1.setUserId(brokerId);

            cloudletList.add(cloudlet1);
        }

        br.close();
    }

    /**
     * initialize data: CloudSim, datacenters, broker, VMs, MOER.
     * (calls init_MOER + init_VMs + init_datacenters)
     */
    private static void init_data() {
        vmlist = new ArrayList<>();
        cloudletList = new ArrayList<>();
        MOER = new ArrayList<>();
        datacenters = new Datacenter[100];

        // First step: Initialize the CloudSim package. It should be called
        // before creating any entities.
        int num_user = 1;   // number of cloud users
        Calendar calendar = Calendar.getInstance();
        boolean trace_flag = false;  // mean trace events

        // Initialize the CloudSim library
        CloudSim.init(num_user, calendar, trace_flag);

        // Second step: Create Datacenters
        //Datacenters are the resource providers in CloudSim. We need at list one of them to run a CloudSim simulation
        init_datacenters();

        // Fourth step: create vms
        broker = createBroker();
        try{
            init_MOER();
            init_VMs();
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

        /* Initialize refrences, csv data, cloudSim, brokers, etc...
         */
        init_data();

        /* Run the cloud simulation using original start-end times.
         * Writes ouput to 'sim.csv'*/
        simRun(sim_path);


        /* Initialize refrences, csv data, cloudSim, brokers, etc... (again bc we are restarting)
         * Then, run our MOER/CO2-saving algorithm! (this will modify start-end times of VM runtimes)
         */
        init_data();
        algoRun();

        /* Re-run the cloud simulation using start-end times that were adjusted by our algorithm.
         * Writes output to 'sim_algo.csv'*/
        simRun(sim_with_algo_path);

    }

    /**
     * Here, we apply first algorithm (which shows a faster runtime than the second method) mentioned in paper: approach using intersections (AUI).
     *      For refrence: https://www.overleaf.com/project/631366e0dd56804a99c1de8a
     * This algorithm will adjust the start and end times of each vm such that they are moer-efficient (running at time lower MOER).
     *
     * @todo may develop a hybrid algorithm between AUI & AUMA to find a compromise between runtime and moer-efficiency (later).
     */
    private static void algoRun()
    {
        //TODO implement AUI here.

    }

    private static void simRun(String outputFileName)
    {

        // Sixth step: Starts the simulation
        CloudSim.startSimulation();


        // Final step: Print results when simulation is over
        List<Cloudlet> newList = broker.getCloudletReceivedList();

        CloudSim.stopSimulation();

        try
        {
            FileOutputStream vmstream = new FileOutputStream(svmlist_path);
            printVMList(vmlist, vmstream);
            vmstream.close();
            FileOutputStream fileOutputStream = new FileOutputStream(outputFileName);
            printCloudletList(newList, fileOutputStream);
            fileOutputStream.close();
        } catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }

    private static Datacenter createDatacenter(String name)
    {

        // Here are the steps needed to create a PowerDatacenter:
        // 1. We need to create a list to store
        //    our machine
        List<Host> hostList = new ArrayList<Host>();

        // 2. A Machine contains one or more PEs or CPUs/Cores.
        // In this example, it will have only one core.
        List<Pe> peList = new ArrayList<Pe>();

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
        LinkedList<Storage> storageList = new LinkedList<Storage>();	//we are not adding SAN devices by now

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

        DatacenterBroker broker = null;
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
    private static void printCloudletList(List<Cloudlet> list, OutputStream ostream) throws IOException
    {
        OutputStream prevOStream = Log.getOutput();
        Log.setOutput(ostream);

        int size = list.size();
        Cloudlet cloudlet;

        Log.formatLine(
                "%-13s, %-13s, %-13s, %-14s, %-13s, %-17s, %-17s, %-17s, %-17s",
                "STATUS",
                "Cloudlet ID",
                "User ID",
                "Datacenter ID",
                "VM ID",
                "Run Length (sec)",
                "Start Time (sec)",
                "Finish Time (sec)",
                "Carbon Emitted (lbs)");

        DecimalFormat dft = new DecimalFormat("###.###");
        for (Cloudlet value : list) {
            cloudlet = value;
            Log.format("%-13s", (cloudlet.getStatus() == Cloudlet.SUCCESS) ? "SUCCESS" : "FAIL");

            Log.formatLine(
                    ", %-13s, %-13s, %-14s, %-13s, %-17s, %-17s, %-17s, %-17s",
                    cloudlet.getCloudletId(),
                    cloudlet.getUserId(),
                    cloudlet.getResourceId(),
                    cloudlet.getVmId(),
                    dft.format(cloudlet.getActualCPUTime()),
                    dft.format(cloudlet.getExecStartTime()),
                    dft.format(cloudlet.getFinishTime()),
                    dft.format(cloudlet.getTotalEmissions()));
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
}
