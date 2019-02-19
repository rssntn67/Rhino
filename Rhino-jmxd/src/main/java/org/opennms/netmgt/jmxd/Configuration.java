package org.opennms.netmgt.jmxd;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Configuration extends Properties {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public Configuration() {
	Properties configProperties = new Properties();
	
	try 
		{
			configProperties.load(new FileInputStream("ossgw.properties"));
		}
		catch (IOException ioException)
		{
			System.err.println("Exception received is in main is " +ioException);		
		}
	}
}
