package cmsc433;

import java.util.List;

/**
 * Customers are simulation actors that have two fields: a name, and a list
 * of Food items that constitute the Customer's order. When running, an
 * customer attempts to enter the Ratsie's (only successful if the
 * Ratsie's has a free table), place its order, and then leave the
 * Ratsie's when the order is complete.
 */
public class Customer implements Runnable {
	// JUST ONE SET OF IDEAS ON HOW TO SET THINGS UP...
	private final String name;
	private final List<Food> order;
	private final int orderNum;
	private static int runningCounter = 0;
	private static int maxCapacity;
	private static int currentCapacity = 0;
	private static Object cLock = new Object();
	private boolean orderReceived = false;

	/**
	 * You can feel free modify this constructor. It must take at
	 * least the name and order but may take other parameters if you
	 * would find adding them useful.
	 */
	public Customer(String name, List<Food> order) {
		this.name = name;
		this.order = order;
		this.orderNum = ++runningCounter;
	}

	public String toString() {
		return name;
	}
	public void setMaxCapacity(int c){
		Customer.maxCapacity = c;
	}
	
	public List<Food> getOrder(){
		return this.order;
	}
	
	public int getOrderNumber(){
		return this.orderNum;
	}
	
	public void orderReceived(){
		Simulation.logEvent(SimulationEvent.customerReceivedOrder(this, order, this.orderNum));
		Simulation.logEvent(SimulationEvent.customerLeavingRatsies(this));
		orderReceived = true;
		currentCapacity--;
		synchronized(cLock){
			cLock.notifyAll();
			
		}
	}

	/**
	 * This method defines what an Customer does: The customer attempts to
	 * enter the Ratsie's (only successful when the Ratsie's has a
	 * free table), place its order, and then leave the Ratsie's
	 * when the order is complete.
	 */
	public void run() {
		// YOUR CODE GOES HERE...

		Simulation.logEvent(SimulationEvent.customerStarting(this));

		synchronized(cLock){
			try {
				while(currentCapacity == maxCapacity) {
					cLock.wait();
				}
				Cook c = new Cook(null);
				
				Simulation.logEvent(SimulationEvent.customerEnteredRatsies(this));
				currentCapacity = currentCapacity + 1;
				Simulation.logEvent(SimulationEvent.customerPlacedOrder(this, this.order, this.orderNum));
				c.placeOrders(this);
				
				cLock.notify();
				
				while(!orderReceived) {
					cLock.wait();
				}
			}
			catch (Exception e){

			}
		}


	}
}
