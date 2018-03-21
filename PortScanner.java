import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PortScanner {

	/*
	 * Author: Cole Polyak
	 * 20 March 2018
	 * PortScanner.java
	 * 
	 */
	
	/*
	 *  TODO List
	 *  ----------
	 *  -Implement support for scanning a range of IPs.
	 *  -File output
	 *  -Find optimal threadcount and timeout.
	 *  -Clean up output.
	 */
	
	
	/*
	 * Data for the time relative to the number of threads used can be found here.
	 * https://imgur.com/a/io5ZF
	 * 
	 * Really, really pretty right?
	 */
	
	public static void main(String[] args) 
	{
		// Gathering information about target.
		Scanner keyboard = new Scanner(System.in);
		System.out.print("Target IP: ");
		String ip = keyboard.nextLine().trim();
		
		System.out.print("Number ports to be scanned (Max 65535): ");
		int numPorts = keyboard.nextInt();
		
		// 200 milliseconds reccomended.
		System.out.print("Timeout (ms): ");
		int timeout = keyboard.nextInt();
		
		// 40 thread-count reccomended.
		System.out.print("Threadcount: ");
		int threadCount = keyboard.nextInt();

		keyboard.close();
		
		// Running scan.
		startScan(ip, numPorts, timeout, threadCount);
		
	}
	
	public static void startScan(String ip, int numPorts, int timeout, int threadCount)
	{
		System.out.println("**********Starting scan on " + ip + "**********");
		
		// For calculating time elapsed.
		long startTime = System.currentTimeMillis();
		
		// Initializing thread pool.
		ExecutorService es = Executors.newFixedThreadPool(threadCount);
		
		// Where output will be stored.
		final List<Future<Port>> futures = new ArrayList<>();
		
		// Scanning ports.
		for(int i = 0; i < numPorts; ++i)
		{
			futures.add(scanIP(es, ip, i, timeout));
		}
		
		// Closing threadpool.
		es.shutdown();

		// Iterating through results and outputting open ports.
		for(Future<Port> a : futures)
		{
			try {
				if(a.get().getStatus()) 
				{
					try 
					{
						a.get().printport();
					} 
					catch (InterruptedException e) 
					{
						e.printStackTrace();
					} 
					catch (ExecutionException e) 
					{
						e.printStackTrace();
					}
				}
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			} 
			catch (ExecutionException e) 
			{
				e.printStackTrace();
			}
		}
		
		long stopTime = System.currentTimeMillis();
		
		// Concluding.
		System.out.println("Scanned " + numPorts + " ports on " + 
							ip + " in " + totalTime(startTime, stopTime));
	}
	
	// Scanning a specific ip.
	public static Future<Port> scanIP(ExecutorService es, String ip, int portNum, int timeout) 
	{
		// Utilizing the pre-created threadpool.
		return es.submit(new Callable<Port>() {
			@Override public Port call()
			{
					Port a = new Port(portNum, true);
					
					// Establishing a connection on the port.
					try
					{
						Socket s = new Socket();
						s.connect(new InetSocketAddress(ip, portNum), timeout);
						s.close();
					}
					catch(Exception e) 
					{ 
						// Sets the port closed if anything goes wrong.
						a.setStatus(false);
					}
					
					// Outputting where in the scan the program is.
					if(portNum % 1000 == 0) {System.out.println("-----Scanning port: " + portNum + " -----");}

					// Returning the results of the port.
					return a;
				}
		});
		
		
		
	}
	
	// Creates objects that represent the status of a port.
	public static class Port
	{
		
		private int portNum;
		private boolean status;
		
		public Port(int portNum, boolean status)
		{
			this.portNum = portNum;
			this.status = status;
		}
		
		// Prints if the port is open.
		public void printport()
		{
			String open;
			if(status) {open = "open";}
			else {open = "closed";}
			
			System.out.println("Port: " + portNum + " is " + open);
		}
		
		public boolean getStatus() {return status;}
		
		public void setStatus(boolean s)
		{
			status = s;
		}
	}
	
	// Calculates how much time was elapsed between the start and end of the scan.
	public static String totalTime(long startTime, long stopTime)
	{
		long timeDiff = stopTime - startTime;
		
		long minutes = ((timeDiff / 1000) / 60);
		timeDiff -= minutes * 60000;
		
		long seconds = (timeDiff / 1000);
		timeDiff -= seconds * 1000;
		
		long milliseconds = ((timeDiff % 1000) / 10);
		
		return minutes + ":" + seconds + "." + milliseconds;
	}
	
	
}


