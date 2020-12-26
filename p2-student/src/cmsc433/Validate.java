package cmsc433;

import java.util.List;
import cmsc433.SimulationEvent;

/**
 * Validates a simulation
 */
public class Validate {
	static int numCustomers;
	static int numCooks;
	static int numTables;
	static int machineCount;
	private static class InvalidSimulationException extends Exception {

		private static final long serialVersionUID = 1L;

		public InvalidSimulationException() {
		}
	};

	public Validate(int numCustomers,int numCooks,int numTables,int machineCount){
		this.numCustomers = numCustomers;
		this.numCooks = numCooks;
		this.numTables = numTables;
		this.machineCount = machineCount;

	}

	// Helper method for validating the simulation
	private static void check(boolean check,
			String message) throws InvalidSimulationException {
		if (!check) {
			System.err.println("SIMULATION INVALID : " + message);
			throw new Validate.InvalidSimulationException();
		}
	}

	/**
	 * Validates the given list of events is a valid simulation.
	 * Returns true if the simulation is valid, false otherwise.
	 *
	 * @param events - a list of events generated by the simulation
	 *        in the order they were generated.
	 *
	 * @returns res - whether the simulation was valid or not
	 */

	public static boolean validateSimulation(List<SimulationEvent> events) {
		try {
			int numCustomersGoing = 0, numCustomersComing = 0, numCustomersLeft = 0;
			int machinesStart = 0, machinesEnd = 0, itemsFilled = 0, itemsFinished = 0;

			int ordersReceived = 0, ordersDone = 0, itemsPlaced = 0, itemsDoneBy = 0;
			int cooksEntered = 0, cooksLeft = 0;
			int numCustomersNow = 0;

			for(int i = 0; i < events.size(); i++){
				if (events.get(i).event == SimulationEvent.EventType.CustomerStarting)
					numCustomersGoing = numCustomersGoing + 1;
				if (events.get(i).event == SimulationEvent.EventType.CustomerEnteredRatsies)
					numCustomersComing = numCustomersComing + 1;
				if (events.get(i).event == SimulationEvent.EventType.CustomerLeavingRatsies)
					numCustomersLeft = numCustomersLeft+1;
				if (events.get(i).event == SimulationEvent.EventType.MachinesStarting)
					machinesStart = machinesStart + 1;
				if (events.get(i).event == SimulationEvent.EventType.MachinesEnding)
					machinesEnd = machinesEnd + 1;
				if (events.get(i).event == SimulationEvent.EventType.MachinesStartingFood)
					itemsFilled = itemsFilled + 1;
				if (events.get(i).event == SimulationEvent.EventType.MachinesDoneFood)
					itemsFinished = itemsFinished + 1;
				if (events.get(i).event == SimulationEvent.EventType.CookReceivedOrder)
					ordersReceived = ordersReceived + 1;
				if (events.get(i).event == SimulationEvent.EventType.CookStartedFood)
					itemsPlaced = itemsPlaced + 1;
				if (events.get(i).event == SimulationEvent.EventType.CookFinishedFood)
					itemsDoneBy = itemsDoneBy + 1;
				if (events.get(i).event == SimulationEvent.EventType.CookStarting)
					cooksEntered = cooksEntered + 1;
				if (events.get(i).event == SimulationEvent.EventType.CookEnding)
					cooksLeft = cooksLeft + 1;
				if (events.get(i).event == SimulationEvent.EventType.CookCompletedOrder)
					ordersDone = ordersDone + 1;
				numCustomersNow = numCustomersComing - numCustomersLeft;
				check(numCustomersNow <= numTables, "Too many allowed to enter: " + (numCustomersNow));


			}
			check(events.get(0).event == SimulationEvent.EventType.SimulationStarting,
					"Simulation didn't start with initiation event");
			check(events.get(events.size() - 1).event == SimulationEvent.EventType.SimulationEnded,
					"Simulation didn't end with termination event");

			check(numCustomersGoing == numCustomers, "Incorrect number of customers went: " + numCustomersGoing);

			check(machinesStart == 4, "Too few machine starts: " + machinesStart);
			check(machinesEnd == 4, "Too few machine ends: " + machinesEnd);
			check(numCustomersComing == numCustomers, "Everyone is not inside: " + numCustomersComing);
			check(numCustomersLeft == numCustomers, "Everyone has not left: " + numCustomersLeft);
			check(itemsFilled == (numCustomers*4), "All items weren't filled: " + itemsFilled);
			check(itemsFinished == (numCustomers*4), "All items weren't done: " + itemsFinished);
			check ((itemsFilled - itemsFinished) == 0, "All items were not made");

			check(ordersReceived == numCustomers, "Incorrect number of orders: " +ordersReceived);
			check(itemsPlaced == (numCustomers*4), "Incorrect number of items placed: " + itemsPlaced);
			check(itemsDoneBy == (numCustomers*4), "Incorrect number of items finished: " + itemsDoneBy);
			check(cooksEntered == numCooks, "Too few cooks inside: " + cooksEntered);
			check(cooksLeft == numCooks, "Too few cooks left: " + cooksLeft);

			/*
			 * In P2 you will write validation code for things such as:
			 * Should not have more eaters than specified
			 * Should not have more cooks than specified
			 * The Ratsie's capacity should not be exceeded
			 * The capacity of each machine should not be exceeded
			 * Eater should not receive order until cook completes it
			 * Eater should not leave Ratsie's until order is received
			 * Eater should not place more than one order
			 * Cook should not work on order before it is placed
			 */

			return true;
		} catch (InvalidSimulationException e) {
			return false;
		}

	}
}