package cmsc433;

import java.util.ArrayList;
import java.util.List;

import cmsc433.Machines.MachineType;

/**
 * Cooks are simulation actors that have at least one field, a name.
 * When running, a cook attempts to retrieve outstanding orders placed
 * by Customer and process them.
 */
public class Cook implements Runnable {
	private final String name;
	private static Machines fryers = new Machines(MachineType.fryers, FoodType.fries);
	private static	Machines sodaMachines = new Machines(MachineType.sodaMachines, FoodType.soda);
	private static	Machines ovens = new Machines(MachineType.ovens, FoodType.pizza);
	private static	Machines grillPresses = new Machines(MachineType.grillPresses, FoodType.subs);
	private static List<Customer> customerList = new ArrayList<Customer>();
	private static Object lock = new Object();

	/**
	 * You can feel free modify this constructor. It must
	 * take at least the name, but may take other parameters
	 * if you would find adding them useful.
	 *
	 * @param: the name of the cook
	 */
	public Cook(String name) {
		this.name = name;
	}

	public String toString() {
		return name;
	}
	
	public void logMachines(){
		fryers.log();
		sodaMachines.log();
		ovens.log();
		grillPresses.log();
	}

	public void logMachinesShutDown(){
		fryers.logShutDown();
		sodaMachines.logShutDown();
		ovens.logShutDown();
		grillPresses.logShutDown();
	}

	public void placeOrders(Customer c){
		synchronized(lock){
			customerList.add(c);
			lock.notify();

		}
	}

	private Customer getOrders() throws InterruptedException{
		synchronized(lock){
			while(customerList.size() == 0)
				lock.wait();
			Customer c = customerList.get(0);
			customerList.remove(0);
			return c;
		}
	}


	/**
	 * This method executes as follows. The cook tries to retrieve
	 * orders placed by Customers. For each order, a List<Food>, the
	 * cook submits each Food item in the List to an appropriate
	 * Machine type, by calling makeFood(). Once all machines have
	 * produced the desired Food, the order is complete, and the Customer
	 * is notified. The cook can then go to process the next order.
	 * If during its execution the cook is interrupted (i.e., some
	 * other thread calls the interrupt() method on it, which could
	 * raise InterruptedException if the cook is blocking), then it
	 * terminates.
	 */
	public void run() {

		Simulation.logEvent(SimulationEvent.cookStarting(this));
		try {
			while (true) {
				Customer c = getOrders();
				Simulation.logEvent(SimulationEvent.cookReceivedOrder(this, c.getOrder(), c.getOrderNumber()));
				List<Food> order = c.getOrder();
				Thread[] oThreads = new Thread[order.size()];

				for(int i = 0; i < order.size(); i++){

					Simulation.logEvent(SimulationEvent.cookStartedFood(this, order.get(i), c.getOrderNumber()));
					if (order.get(i) == (FoodType.pizza))
						oThreads[i] = ovens.makeFood();
					else if (order.get(i) == (FoodType.subs))
						oThreads[i] = grillPresses.makeFood();
					else if (order.get(i) == (FoodType.soda))
						oThreads[i] = sodaMachines.makeFood();
					else if (order.get(i) == (FoodType.fries))
						oThreads[i] = fryers.makeFood();
				}

				int j = 0;
				while(j < order.size()){

					if (!oThreads[j].isAlive()){
						Simulation.logEvent(SimulationEvent.cookFinishedFood(this, order.get(j), c.getOrderNumber()));
						j++;
					}
				}
				Simulation.logEvent(SimulationEvent.cookCompletedOrder(this, c.getOrderNumber()));
				c.orderReceived();



				
			}
		} catch (InterruptedException e) {
			// This code assumes the provided code in the Simulation class
			// that interrupts each cook thread when all customers are done.
			// You might need to change this if you change how things are
			// done in the Simulation class.
			Simulation.logEvent(SimulationEvent.cookEnding(this));
		}
	}
}
