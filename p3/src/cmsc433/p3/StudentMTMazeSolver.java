package cmsc433.p3;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This file needs to hold your solver to be tested. 
 * You can alter the class to extend any class that extends MazeSolver.
 * It must have a constructor that takes in a Maze.
 * It must have a solve() method that returns the datatype List<Direction>
 *   which will either be a reference to a list of steps to take or will
 *   be null if the maze cannot be solved.
 */
public class StudentMTMazeSolver extends SkippingMazeSolver
{
	public ExecutorService executorPool;

	public StudentMTMazeSolver(Maze maze) {
		super(maze);
	}

	public List<Direction> solve() 
	{
		LinkedList<DFS> tasks = new LinkedList<DFS>();
		
		List<Direction> result = null;
		
		int processors = Runtime.getRuntime().availableProcessors();
		executorPool = Executors.newFixedThreadPool(processors);
		List<Future<List<Direction>>> futures = new LinkedList<Future<List<Direction>>>();
		long totals = 0;
		
		try{
			Choice start = firstChoice(maze.getStart());
			
			int size = start.choices.size();
			for(int i = 0; i < size; i++) {
				Choice currChoice = follow(start.at, start.choices.peek());
				
				tasks.add(new DFS(currChoice, start.choices.pop()));
				
			}
		}catch (SolutionFound e){
			System.out.println("Error caught: "+ e.getMessage().toString());
		}
		try {
			futures = executorPool.invokeAll(tasks);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		executorPool.shutdown();
		for(Future<List<Direction>> answer : futures){
			try {
				
				if(answer.get() != null){
					result = answer.get();
					
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}
		
		for (DFS dep : tasks) {
			totals += dep.count;
		}
		
		System.out.println("# of Choices to get to the path: " + (totals - 1));

		return result;
	}

	private class DFS implements Callable<List<Direction>>{
		Choice begin;
		Direction choiceDir;
		public long count = 0;
		
		public DFS(Choice begin, Direction choiceDir){
			this.begin = begin;
			this.choiceDir = choiceDir;
			
		}  

		@Override
		public List<Direction> call() {
			LinkedList<Choice> choice = new LinkedList<Choice>();
			Choice currChoice;

			try{
				choice.push(this.begin);
				
				while(!choice.isEmpty()){
					currChoice = choice.peek();
					count++;
					if(currChoice.isDeadend()){
						
						choice.pop();
						if (!choice.isEmpty()) {
							choice.peek().choices.pop();
						}
						continue;
					}
					choice.push(follow(currChoice.at, currChoice.choices.peek()));
				}
				return null;
			}catch (SolutionFound e){
				Iterator<Choice> it = choice.iterator();
	            LinkedList<Direction> solutionPath = new LinkedList<Direction>();
	        
	           
	            while (it.hasNext())
	            {
	            	currChoice = it.next();
	                solutionPath.push(currChoice.choices.peek());
	            }
	            solutionPath.push(choiceDir);
	            if (maze.display != null) {
	            	maze.display.updateDisplay();
	            }
	            
	            return pathToFullPath(solutionPath);
			}

		}

	}
}