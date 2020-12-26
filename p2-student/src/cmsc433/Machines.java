package cmsc433;

/**
 * Machines are used to make different kinds of Food. Each Machine type makes
 * just one kind of Food. Each machine type has a count: the set of machines of
 * that type can make that many food items in parallel. If the machines are
 * asked to produce a food item beyond its count, the requester blocks. Each
 * food item takes at least item.cookTime10S seconds to produce. In this
 * simulation, use Thread.sleep(item.cookTime10S) to simulate the actual cooking
 * time.
 */
public class Machines {

	public enum MachineType {
		sodaMachines, fryers, grillPresses, ovens
	};

	// Converts Machines instances into strings based on MachineType.
	public String toString() {
		switch (machineType) {
		case sodaMachines:
			return "Soda Machines";
		case fryers:
			return "Fryers";
		case grillPresses:
			return "Grill Presses";
		case ovens:
			return "Ovens";
		default:
			return "INVALID MACHINE TYPE";
		}
	}

	public final MachineType machineType;
	public final Food machineFoodType;
	private static int count;
	private int currentCount = 0;
	private Object fLock = new Object();

	// YOUR CODE GOES HERE...



	/**
	 * The constructor takes at least the name of the machines, the Food item they
	 * make, and their count. You may extend it with other arguments, if you wish.
	 * Notice that the constructor currently does nothing with the count; you must
	 * add code to make use of this field (and do whatever initialization etc. you
	 * need).
	 */
	public Machines(MachineType machineType, Food foodIn, int countIn) {
		this.machineType = machineType;
		this.machineFoodType = foodIn;
		count = countIn;


		// YOUR CODE GOES HERE...


	}
	public Machines(MachineType machineTypeIn, Food foodIn){
		this.machineType = machineTypeIn;
		this.machineFoodType = foodIn;
	}

	public void log(){
		Simulation.logEvent(SimulationEvent.machinesStarting(this, this.machineFoodType, count));
	}

	public void logShutDown(){
		Simulation.logEvent(SimulationEvent.machinesEnding(this));
	}

	/**
	 * This method is called by a Cook in order to make the Machines' food item. You
	 * can extend this method however you like, e.g., you can have it take extra
	 * parameters or return something other than Object. It should block if the
	 * machines are currently busy (i.e. #items == count). If not, the method should
	 * return, so the Cook making the call can proceed. You will need to implement
	 * some means to notify the calling Cook when the food item is finished.
	 */
	public Thread makeFood() throws InterruptedException {
		Thread cookItems = new Thread(new CookAnItem(this.machineFoodType,  this));
		cookItems.start();

		return cookItems;



	}

	// THIS MIGHT BE A USEFUL METHOD TO HAVE AND USE BUT IS JUST ONE IDEA
	private class CookAnItem implements Runnable {
		Food f;
		Machines m;


		public CookAnItem(Food f, Machines m){
			this.f = f;
			this.m = m;
		}

		public void run() {
			try {
				synchronized(fLock){

					while(currentCount == count) {
						fLock.wait();
					}
					currentCount = currentCount + 1;
				}
				Simulation.logEvent(SimulationEvent.machinesCookingFood(this.m, this.f));
				Thread.sleep(f.cookTime10S);
				currentCount = currentCount - 1;
				synchronized(fLock){
					fLock.notify();
				}
				Simulation.logEvent(SimulationEvent.machinesDoneFood(this.m, this.f));



			} catch(InterruptedException e) { }
		}
	}
}
