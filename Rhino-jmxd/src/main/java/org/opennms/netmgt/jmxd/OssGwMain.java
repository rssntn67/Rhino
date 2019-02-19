package org.opennms.netmgt.jmxd;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


public class OssGwMain 
{

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{
		System.out.println("Starting OSSGW");

		Properties configProperties = new Properties();
		try 
		{
			configProperties.load(new FileInputStream("ossgw.properties"));
		}
		catch (IOException ioException)
		{
			System.err.println("Exception received is in main is " +ioException);		
		}
		//configProperties.list(System.out);
		final int retryRhinoConnectionInterval;
		retryRhinoConnectionInterval = new Integer(
					configProperties.getProperty("retryRhinoConnectionInterval", 
						"60000")).intValue();
		while (true)
		{
			try
			{
				RhinoJmxStatsClient jmxStatsClient = new RhinoJmxStatsClient(configProperties);
				jmxStatsClient.start();

				RhinoJmxAlarmsClient jmxAlarmsClient = new RhinoJmxAlarmsClient(configProperties);
				jmxAlarmsClient.start();
				
				jmxStatsClient.join();
				jmxAlarmsClient.join();
			    // Finished
			}
			catch (InterruptedException interruptedException) 
			{
				System.err.println("Exception received in main is " +interruptedException);			
			}
			try
			{
				System.out.println("Retrying rhino connection after " 
						+retryRhinoConnectionInterval +"ms");	
				Thread.sleep(retryRhinoConnectionInterval);
			}
			catch (InterruptedException interruptedException) 
			{
				System.err.println("Exception received while sleeping in main is " 
						+interruptedException);			
			}
			
		}

	}

}
