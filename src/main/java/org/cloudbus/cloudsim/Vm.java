/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 * 
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a Virtual Machine (VM) that runs inside a Host, sharing a hostList with other VMs. It processes
 * cloudlets. This processing happens according to a policy, defined by the CloudletScheduler. Each
 * VM has a owner, which can submit cloudlets to the VM to execute them.
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */

/**
 * NOTE:
 * All custom/added constructors and instance variables are labeled with 'CUSTOM' in its description.
 * Added methods are appended to the end of this file.
 *  -NH
 * */

public class Vm {

	/** The VM unique id. */
	private int id;

	/** The user id. */
	private int userId;

	/** A Unique Identifier (UID) for the VM, that is compounded by the user id and VM id. */
	private String uid;

	/** The size the VM image size (the amount of storage it will use, at least initially). */
	private long size;

	/** The MIPS capacity of each VM's PE. */
	private double mips;

	/** The number of PEs required by the VM. */
	private int numberOfPes;

	/** The required ram *in MB*. */
	private int ram;

	/** The required bw. */
	private long bw;

	/** CUSTOM. The percentage data about utilization of cpu*/
	private double avg_util, max_util ,p95;

	/** CUSTOM. The start time (sec) and end time (sec) of the runtime of this VM. */
	private int start, end;

	/** The Virtual Machine Monitor (VMM) that manages the VM. */
	private String vmm;

	/** The Cloudlet scheduler the VM uses to schedule cloudlets execution. */
	private CloudletScheduler cloudletScheduler;

	/** The PM that hosts the VM. */
	private Host host;

	/** Indicates if the VM is in migration process. */
	private boolean inMigration;
	
	/** NEW Indicates if the VM is in Pause State (e.g. Stop & copy phase)*/
	private boolean inPause;
	
	/** The current allocated storage size. */
	private long currentAllocatedSize;

	/** The current allocated ram. */
	private int currentAllocatedRam;

	/** The current allocated bw. */
	private long currentAllocatedBw;

	/** The current allocated mips for each VM's PE. */
	private List<Double> currentAllocatedMips;

	/** CUSTOM. Refrences to the moer lists in Sim.java */
	private List<Integer> MOER;
	private List<Integer> PMOER;

	/** Indicates if the VM is being instantiated. */
	private boolean beingInstantiated;

	/** The mips allocation history. 
         * @todo Instead of using a list, this attribute would be 
         * a map, where the key can be the history time
         * and the value the history itself. 
         * By this way, if one wants to get the history for a given
         * time, he/she doesn't have to iterate over the entire list
         * to find the desired entry.
         */
	private final List<VmStateHistoryEntry> stateHistory = new LinkedList<VmStateHistoryEntry>();

	/**
	 * Creates a new Vm object.
	 * 
	 * @param id unique ID of the VM
	 * @param userId ID of the VM's owner
	 * @param mips the mips
	 * @param numberOfPes amount of CPUs
	 * @param ram amount of ram
	 * @param bw amount of bandwidth
	 * @param size The size the VM image size (the amount of storage it will use, at least initially).
	 * @param vmm virtual machine monitor
	 * @param cloudletScheduler cloudletScheduler policy for cloudlets scheduling
         * 
	 * @pre id >= 0
	 * @pre userId >= 0
	 * @pre size > 0
	 * @pre ram > 0
	 * @pre bw > 0
	 * @pre cpus > 0
	 * @pre priority >= 0
	 * @pre cloudletScheduler != null
	 * @post $none
	 */
	public Vm(
			int id,
			int userId,
			double mips,
			int numberOfPes,
			int ram,
			long bw,
			long size,
			String vmm,
			CloudletScheduler cloudletScheduler) {
		setId(id);
		setUserId(userId);
		setUid(getUid(userId, id));
		setMips(mips);
		setNumberOfPes(numberOfPes);
		setRam(ram);
		setBw(bw);
		setSize(size);
		setVmm(vmm);
		setCloudletScheduler(cloudletScheduler);

		setInMigration(false);
		setInPause(false);
		setBeingInstantiated(true);

		setCurrentAllocatedBw(0);
		setCurrentAllocatedMips(null);
		setCurrentAllocatedRam(0);
		setCurrentAllocatedSize(0);
	}

	/**
	 * CUSTOM CONSTRUCTOR
	 *
	 * Custom constructor for VM with avg % CPU utilization, start and end.
	 */
	public Vm(
			int id,
			int userId,
			double mips,
			int numberOfPes,
			int ram,
			long bw,
			long size,
			String vmm,

			double avg_util,
			double max_util,
			double p95,
			int start, int end,
			List<Integer> MOER,
			List<Integer> PMOER,

			CloudletSchedulerTimeShared cloudletScheduler) {
		setId(id);
		setUserId(userId);
		setUid(getUid(userId, id));
		setMips(mips);
		setNumberOfPes(numberOfPes);
		setRam(ram);
		setBw(bw);
		setSize(size);
		setVmm(vmm);
		setCloudletScheduler(cloudletScheduler);

		setInMigration(false);
		setBeingInstantiated(true);

		// custom instance variables are initialized here
		this.avg_util = avg_util;
		this.max_util = max_util;
		this.p95 = p95;
		this.MOER = MOER;
		this.PMOER = PMOER;
		setTime(new int[]{start, end});
		cloudletScheduler.setVM(this);

		setCurrentAllocatedBw(0);
		setCurrentAllocatedMips(null);
		setCurrentAllocatedRam(0);
		setCurrentAllocatedSize(0);
	}

	/**
	 * Updates the processing of cloudlets running on this VM.
	 * 
	 * @param currentTime current simulation time
	 * @param mipsShare list with MIPS share of each Pe available to the scheduler
	 * @return time predicted completion time of the earliest finishing cloudlet, or 0 if there is no
	 *         next events
	 * @pre currentTime >= 0
	 * @post $none
	 */
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		if (mipsShare != null) {
			return getCloudletScheduler().updateVmProcessing(currentTime, mipsShare);
		}
		return 0.0;
	}

	/**
	 * Gets the current requested mips.
	 * 
	 * @return the current requested mips
	 */
	public List<Double> getCurrentRequestedMips() {
		List<Double> currentRequestedMips = getCloudletScheduler().getCurrentRequestedMips();
		if (isBeingInstantiated()) {
			currentRequestedMips = new ArrayList<Double>();
			for (int i = 0; i < getNumberOfPes(); i++) {
				currentRequestedMips.add(getMips());
			}
		}
		return currentRequestedMips;
	}

	/**
	 * Gets the current requested total mips.
	 * 
	 * @return the current requested total mips
	 */
	public double getCurrentRequestedTotalMips() {
		double totalRequestedMips = 0;
		for (double mips : getCurrentRequestedMips()) {
			totalRequestedMips += mips;
		}
		return totalRequestedMips;
	}

	/**
	 * Gets the current requested max mips among all virtual PEs.
	 * 
	 * @return the current requested max mips
	 */
	public double getCurrentRequestedMaxMips() {
		double maxMips = 0;
		for (double mips : getCurrentRequestedMips()) {
			if (mips > maxMips) {
				maxMips = mips;
			}
		}
		return maxMips;
	}

	/**
	 * Gets the current requested bw.
	 * 
	 * @return the current requested bw
	 */
	public long getCurrentRequestedBw() {
		if (isBeingInstantiated()) {
			return getBw();
		}
		return (long) (getCloudletScheduler().getCurrentRequestedUtilizationOfBw() * getBw());
	}

	/**
	 * Gets the current requested ram.
	 * 
	 * @return the current requested ram
	 */
	public int getCurrentRequestedRam() {
		if (isBeingInstantiated()) {
			return getRam();
		}
		return (int) (getCloudletScheduler().getCurrentRequestedUtilizationOfRam() * getRam());
	}

	/**
	 * Gets total CPU utilization percentage of all clouddlets running on this VM at the given time
	 * 
	 * @param time the time
	 * @return total utilization percentage
	 */
	public double getTotalUtilizationOfCpu(double time) {
		return getCloudletScheduler().getTotalUtilizationOfCpu(time);
	}

	/**
	 * Get total CPU utilization of all cloudlets running on this VM at the given time (in MIPS).
	 * 
	 * @param time the time
	 * @return total cpu utilization in MIPS
         * @see #getTotalUtilizationOfCpu(double) 
	 */
	public double getTotalUtilizationOfCpuMips(double time) {
		return getTotalUtilizationOfCpu(time) * getMips();
	}

	/**
	 * Sets the uid.
	 * 
	 * @param uid the new uid
	 */
	public void setUid(String uid) {
		this.uid = uid;
	}

	/**
	 * Gets unique string identifier of the VM.
	 * 
	 * @return string uid
	 */
	public String getUid() {
		return uid;
	}

	/**
	 * Generate unique string identifier of the VM.
	 * 
	 * @param userId the user id
	 * @param vmId the vm id
	 * @return string uid
	 */
	public static String getUid(int userId, int vmId) {
		return userId + "-" + vmId;
	}

	/**
	 * Gets the VM id.
	 * 
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * Sets the VM id.
	 * 
	 * @param id the new id
	 */
	protected void setId(int id) {
		this.id = id;
	}

	/**
	 * Sets the user id.
	 * 
	 * @param userId the new user id
	 */
	protected void setUserId(int userId) {
		this.userId = userId;
	}

	/**
	 * Gets the ID of the owner of the VM.
	 * 
	 * @return VM's owner ID
	 * @pre $none
	 * @post $none
	 */
	public int getUserId() {
		return userId;
	}

	/**
	 * Gets the mips.
	 * 
	 * @return the mips
	 */
	public double getMips() {
		return mips;
	}

	/**
	 * Sets the mips.
	 * 
	 * @param mips the new mips
	 */
	protected void setMips(double mips) {
		this.mips = mips;
	}

	/**
	 * Gets the number of pes.
	 * 
	 * @return the number of pes
	 */
	public int getNumberOfPes() {
		return numberOfPes;
	}

	/**
	 * Sets the number of pes.
	 * 
	 * @param numberOfPes the new number of pes
	 */
	public void setNumberOfPes(int numberOfPes) {
		this.numberOfPes = numberOfPes;
	}

	/**
	 * Gets the amount of ram.
	 * 
	 * @return amount of ram
	 * @pre $none
	 * @post $none
	 */
	public int getRam() {
		return ram;
	}

	/**
	 * Sets the amount of ram.
	 * 
	 * @param ram new amount of ram
	 * @pre ram > 0
	 * @post $none
	 */
	public void setRam(int ram) {
		this.ram = ram;
	}

	/**
	 * Gets the amount of bandwidth.
	 * 
	 * @return amount of bandwidth
	 * @pre $none
	 * @post $none
	 */
	public long getBw() {
		return bw;
	}

	/**
	 * Sets the amount of bandwidth.
	 * 
	 * @param bw new amount of bandwidth
	 * @pre bw > 0
	 * @post $none
	 */
	public void setBw(long bw) {
		this.bw = bw;
	}

	/**
	 * Gets the amount of storage.
	 * 
	 * @return amount of storage
	 * @pre $none
	 * @post $none
	 */
	public long getSize() {
		return size;
	}

	/**
	 * Sets the amount of storage.
	 * 
	 * @param size new amount of storage
	 * @pre size > 0
	 * @post $none
	 */
	public void setSize(long size) {
		this.size = size;
	}

	/**
	 * Gets the VMM.
	 * 
	 * @return VMM
	 * @pre $none
	 * @post $none
	 */
	public String getVmm() {
		return vmm;
	}

	/**
	 * Sets the VMM.
	 * 
	 * @param vmm the new VMM
	 */
	protected void setVmm(String vmm) {
		this.vmm = vmm;
	}

	/**
	 * Sets the host that runs this VM.
	 * 
	 * @param host Host running the VM
	 * @pre host != $null
	 * @post $none
	 */
	public void setHost(Host host) {
		this.host = host;
	}

	/**
	 * Gets the host.
	 * 
	 * @return the host
	 */
	public Host getHost() {
		return host;
	}

	/**
	 * Gets the vm scheduler.
	 * 
	 * @return the vm scheduler
	 */
	public CloudletScheduler getCloudletScheduler() {
		return cloudletScheduler;
	}

	/**
	 * Sets the vm scheduler.
	 * 
	 * @param cloudletScheduler the new vm scheduler
	 */
	protected void setCloudletScheduler(CloudletScheduler cloudletScheduler) {
		this.cloudletScheduler = cloudletScheduler;
	}

	/**
	 * Checks if is in migration.
	 * 
	 * @return true, if is in migration
	 */
	public boolean isInMigration() {
		return inMigration;
	}

	/**
	 * Sets the in migration.
	 * 
	 * @param inMigration the new in migration
	 */
	public void setInMigration(boolean inMigration) {
		this.inMigration = inMigration;
	}

	/**
	 * Gets the current allocated size.
	 * 
	 * @return the current allocated size
	 */
	public long getCurrentAllocatedSize() {
		return currentAllocatedSize;
	}

	/**
	 * Sets the current allocated size.
	 * 
	 * @param currentAllocatedSize the new current allocated size
	 */
	protected void setCurrentAllocatedSize(long currentAllocatedSize) {
		this.currentAllocatedSize = currentAllocatedSize;
	}

	/**
	 * Gets the current allocated ram.
	 * 
	 * @return the current allocated ram
	 */
	public int getCurrentAllocatedRam() {
		return currentAllocatedRam;
	}

	/**
	 * Sets the current allocated ram.
	 * 
	 * @param currentAllocatedRam the new current allocated ram
	 */
	public void setCurrentAllocatedRam(int currentAllocatedRam) {
		this.currentAllocatedRam = currentAllocatedRam;
	}

	/**
	 * Gets the current allocated bw.
	 * 
	 * @return the current allocated bw
	 */
	public long getCurrentAllocatedBw() {
		return currentAllocatedBw;
	}

	/**
	 * Sets the current allocated bw.
	 * 
	 * @param currentAllocatedBw the new current allocated bw
	 */
	public void setCurrentAllocatedBw(long currentAllocatedBw) {
		this.currentAllocatedBw = currentAllocatedBw;
	}

	/**
	 * Gets the current allocated mips.
	 * 
	 * @return the current allocated mips
	 * @TODO replace returning the field by a call to getCloudletScheduler().getCurrentMipsShare()
	 */
	public List<Double> getCurrentAllocatedMips() {
		return currentAllocatedMips;
	}

	/**
	 * Sets the current allocated mips.
	 * 
	 * @param currentAllocatedMips the new current allocated mips
	 */
	public void setCurrentAllocatedMips(List<Double> currentAllocatedMips) {
		this.currentAllocatedMips = currentAllocatedMips;
	}

	/**
	 * Checks if is being instantiated.
	 * 
	 * @return true, if is being instantiated
	 */
	public boolean isBeingInstantiated() {
		return beingInstantiated;
	}

	/**
	 * Sets the being instantiated.
	 * 
	 * @param beingInstantiated the new being instantiated
	 */
	public void setBeingInstantiated(boolean beingInstantiated) {
		this.beingInstantiated = beingInstantiated;
	}

	/**
	 * Gets the state history.
	 * 
	 * @return the state history
	 */
	public List<VmStateHistoryEntry> getStateHistory() {
		return stateHistory;
	}

	/**
	 * Adds a VM state history entry.
	 * 
	 * @param time the time
	 * @param allocatedMips the allocated mips
	 * @param requestedMips the requested mips
	 * @param isInMigration the is in migration
	 */
	public void addStateHistoryEntry(
			double time,
			double allocatedMips,
			double requestedMips,
			boolean isInMigration) {
		VmStateHistoryEntry newState = new VmStateHistoryEntry(
				time,
				allocatedMips,
				requestedMips,
				isInMigration);
		if (!getStateHistory().isEmpty()) {
			VmStateHistoryEntry previousState = getStateHistory().get(getStateHistory().size() - 1);
			if (previousState.getTime() == time) {
				getStateHistory().set(getStateHistory().size() - 1, newState);
				return;
			}
		}
		getStateHistory().add(newState);
	}

	public boolean isInPause() {
		return inPause;
	}

	public void setInPause(boolean inPause) {
		this.inPause = inPause;
	}





	/*----------------------  CUSTOM METHODS BELOW  --------------------------
	 * -------------------------------  ...  ----------------------------------
	 */





	public int[] getTime() {return new int[]{start, end};}
	public void setTime(int[] t) {start = t[0]; end = t[1];}

	public double getAvg_util() {return avg_util;}

	public void setAvg_util(double avg_util) {
		this.avg_util = avg_util;
	}

	public double getMax_util() {return max_util;}

	public void setMax_util(double max_util) {
		this.max_util = max_util;
	}

	public double getP95() {return p95;}

	public void setP95(double p95) {
		this.p95 = p95;
	}

	/**
	 * Gets the power (watt) of this VM.
	 *
	 * @return power (watt)
	 */
	public double getPower() {
		int cores = numberOfPes;
		int memory = ram / 1000; // divide by 1000 bc MB --> GB

		// Lin. Reg.
		switch (cores) {
			case 2:
				return Math.max(-12.1318 * memory + 42.1 * avg_util + 120.023, 0.0);
			case 4:
				return Math.max(-0.792386 * memory + 40.41 * avg_util + 23.2432, 0.0);
			case 8:
				return Math.max(42.1392 * avg_util + 16.6206, 0.0);
			default:
				return Math.max(-0.0200128 * memory + 188.199 * avg_util + 112.653, 0.0);
		}
	}

	/**
	 * Gets the total energy (MWh) over the runtime from start to end.
	 *
	 * @return energy (MWh)
	 */
	public double getEnergy() {
		int time = end - start;
		if (time == 0) return 0;
		return getPower() * time / (1_000_000 * 3600.0); // watt * s / 1000000 / 3600 = megawatt * hour
	}

	/**
	 * Gets observed MOER (*averaged* over the runtime interval) in pounds of emissions per megawatt-hour (e.g. CO2 lbs/MWh)
	 *
	 * @return
	 */
	public double getAverageMOER() {
		double avgMOER = 0;
		int ms = start / 300, me = end / 300; // convert time-scale from unit = sec to unit = 5min.
		if(me - ms == 0) return 0;
		for(int i = ms; i < me; i++) // from [start, end).
			avgMOER += MOER.get(i);
		avgMOER /= me - ms;
		return avgMOER;
	}

	/**
	 * Gets *predicted* MOER (*averaged* over the runtime interval) in pounds of emissions per megawatt-hour (e.g. CO2 lbs/MWh)
	 *
	 * @return
	 */
	public double getAveragePMOER() {
		double avgPMOER = 0;
		int ms = start / 300, me = end / 300; // convert time-scale from unit = sec to unit = 5min.
		if(me - ms == 0) return 0;
		for(int i = ms; i < me; i++) // from [start, end).
			avgPMOER += PMOER.get(i);
		avgPMOER /= me - ms;
		return avgPMOER;
	}

	/**
	 * Gets the carbon over the runtime.
	 *
	 * @return
	 */
	public double getCarbon() {
		return getAverageMOER() * getEnergy();
	}

	/**
	 * Gets the *predicted* carbon over the runtime.
	 *
	 * @return
	 */
	public double getPCarbon() {
		return getAveragePMOER() * getEnergy();
	}

	/**
	 * Get price in $/hour
	 *
	 * @return
	 */
	public double getPrice() {
		return -0.0038 + 0.0468 * numberOfPes + 0.0017 * ram / 1000.0;
	}

	/**
	 * Get total cost in $
	 *
	 * @return
	 */
	private double getCost() {
		return getPrice() * (end - start) / 3600.0;
	}

	/**
	 * Get wasted money in $
	 *
	 * @return
	 */
	public double getWaste() {
		return getCost() * (1 - avg_util / 100);
	}

	/**
	 * good ol' toString method
	 *
	 * @return vm as string
	 */
	@Override
	public String toString()
	{
		String out;
		out = String.format("%-10s, %-12s, %-12s, %-14s",
				getId(),
				getUserId(),
				getRam() / 1000,
				getNumberOfPes());
		out += String.format(", %-18s, %-28s, %-28s, %-15s, %-28s",
				String.format("%.2f", getPower()),
				String.format("%.2f", avg_util),
				String.format("%.2f", max_util),
				String.format("%.2f", p95),
				"(" + start + ", " + end + ")");
		return out;
	}

}
