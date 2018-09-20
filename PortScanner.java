import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	 *  -Improve sanitization of inputs with parameters.
	 */
	
	
	/*
	 * Data for the time relative to the number of threads used can be found here.
	 * https://imgur.com/a/io5ZF
	 * 
	 * Really, really pretty right?
	 */
	
	// Default scan parameters.
	private static int timeout = 200;
	private static int portCount = 65535;
	private static int threadcount = 40;
	
	private static String target = "0.0.0.0";
	
	private static Scanner inputFile = null;
	private static PrintWriter outputFile = null;
	
	private static ArrayList<String> ipsToScan = new ArrayList<>();
	
	private static final HashSet<String> operators = initializeHashSet();
	
	public static void main(String[] args) 
	{
		boolean isValid = false;
		
		for(int i = 0; i < args.length; ++i)
		{
			// Checking if command argument is a parameter.
			if(operators.contains(args[i]))
			{
				isValid = true;
				continue;
			}
			
			// Argument found valid, reading in change.
			if(isValid)
			{
				adjustScanParameters(args[i-1], args[i]);
				isValid = false;
			}
		}
		
		// Ensures the scan has an ip to work with.
		if("0.0.0.0".equals(target) && inputFile == null) findTarget();
		
		// Avoids inadvertently scanning 0.0.0.0
		if(!("0.0.0.0".equals(target))) ipsToScan.add(target);
		
		for(int i = 0; i < ipsToScan.size(); ++i)
		{
			// Running the scan
			startScan(ipsToScan.get(i), portCount, timeout, threadcount, i);
		}
		
	}
	
	private static void startScan(String ip, int numPorts, int timeout, int threadCount, int index)
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
			try 
			{
				Port current = a.get();
				if(current.getStatus()) current.printport();
			} 
			catch (InterruptedException | ExecutionException e1) 
			{
				e1.printStackTrace();
			}
		}
		
		if(outputFile != null) printToFile(futures, ip, numPorts, timeout, threadCount, index);
		
		long stopTime = System.currentTimeMillis();
		
		// Concluding.
		System.out.println("Scanned " + numPorts + " ports on " + 
							ip + " in " + totalTime(startTime, stopTime));
		
	}
	
	// Scanning a specific ip.
	private static Future<Port> scanIP(ExecutorService es, String ip, int portNum, int timeout) 
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
	private static class Port
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
		
		public int getPortNum() {return portNum;}
		
		public void setStatus(boolean s)
		{
			status = s;
		}
	}
	
	
	// Outputs the results of a scan to a file. Includes specifications about the scan itself.
	private static void printToFile(List<Future<Port>> results, String ipAddress, int numPorts, int timeout, int threadcount, int index) 
	{
		outputFile.println("Scan result for " + ipAddress);
		outputFile.println("*******************************************************");
		outputFile.println("Scan specifications: ");
		outputFile.println("\tNumber of Ports: " + numPorts);
		outputFile.println("\tTimeout (ms): " + timeout);
		outputFile.println("\tThreadcount: " + threadcount +"\n\n");
		
		for(Future<Port> p : results)
		{
			try 
			{
				Port current = p.get();
				
				if(current.getStatus())
				{
					outputFile.println("Port " + current.getPortNum() + " is open.");
				}
 			} 
			catch (InterruptedException | ExecutionException e) 
			{
				e.printStackTrace();
			}
		}
		
		if(results.size() == 0) outputFile.println("No open ports found.");
		
		outputFile.println("*******************************************************");
		
		if(index == ipsToScan.size() -1) outputFile.close();
	}
	
	// Calculates how much time was elapsed between the start and end of the scan.
	private static String totalTime(long startTime, long stopTime)
	{
		long timeDiff = stopTime - startTime;
		
		long minutes = ((timeDiff / 1000) / 60);
		timeDiff -= minutes * 60000;
		
		long seconds = (timeDiff / 1000);
		timeDiff -= seconds * 1000;
		
		long milliseconds = ((timeDiff % 1000) / 10);
		
		return minutes + ":" + seconds + "." + milliseconds;
	}
	
	// Initializes the command hash set that helps parse in command parameters.
	private static HashSet<String> initializeHashSet()
	{
		HashSet<String> toBeReturned = new HashSet<>();
		toBeReturned.add("-i");
		toBeReturned.add("-o");
		toBeReturned.add("-to");
		toBeReturned.add("-tc");
		toBeReturned.add("-T");
		toBeReturned.add("-p");
		
		return toBeReturned;
	}
	
	//TODO Make this method stronger. Dealing with shit input.
	private static void adjustScanParameters(String key, String adjustment)
	{
		if("-to".equals(key)) timeout = Integer.parseInt(adjustment);
		else if("-tc".equals(key)) threadcount = Integer.parseInt(adjustment);
		else if("-T".equals(key)) target = adjustment;
		else if("-p".equals(key)) portCount = Integer.parseInt(adjustment);
		else if("-i".equals(key)) initializeInputFile(adjustment);
		else if("-o".equals(key)) initializeOutputFile(adjustment);
	}
	
	// For use when the user forgets to specify an ip address.
	private static void findTarget()
	{
		Scanner keyboard = new Scanner(System.in);
		String ip;
		
		do
		{
			System.out.println("Please enter a valid IP: ");
			ip = keyboard.nextLine().trim();
			
			if("q".equals(ip)) System.exit(1);
		}
		// Iterates till an ip is found.
		while(!isValidIP(ip));
		
		target = ip;
		keyboard.close();
	}
	
	// Creates the input file so that the ips can be read in and scanned.
	private static void initializeInputFile(String filename)
	{
		try
		{
			inputFile = new Scanner(new FileInputStream(filename));
			verifyFileFormat();
			inputFile.close();
		}
		catch(FileNotFoundException e)
		{
			System.err.println("Input file not found. Terminating");
			System.exit(1);
		}
	}
	
	// Creates the output file so that the results of the scan can be written to file.
	private static void initializeOutputFile(String filename)
	{
		try
		{
			outputFile = new PrintWriter(new FileOutputStream(filename));
		}
		catch(FileNotFoundException e)
		{
			// If the output file has not already been created.
			@SuppressWarnings("unused")
			File f = new File(filename);
			initializeOutputFile(filename);
		}
	}
	
	// Checks that the input file has at least one valid ip address to scan. 
	private static void verifyFileFormat()
	{
		boolean atleastOneValidIP = false;
		
		while(inputFile.hasNext())
		{
			String current = inputFile.next();
			if(isValidIP(current))
			{
				atleastOneValidIP = true;
				ipsToScan.add(current);
			}
		}
		
		if(!atleastOneValidIP)
		{
			throw new IllegalStateException("Input file has no valid IP addresses. Terminating");
		}
	}
	
	// Validates that the string passed in is a valid ip address.
	private static boolean isValidIP(String input)
	{
		Pattern ipPattern = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
		Matcher m = ipPattern.matcher(input);
		return m.matches();
	}
}


