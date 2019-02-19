package org.opennms.netmgt.jmxd;

import java.io.PrintWriter;
import java.util.Properties;

import com.opencloud.slee.remote.RhinoConnection;
import com.opencloud.slee.remote.RhinoConnectionFactory;
import com.opencloud.slee.remote.RhinoSsl;

public class RhinoJmxClient extends Thread
{
	protected RhinoConnection rhinoConnection;
	protected Properties configProperties;
	
	RhinoJmxClient(Properties configProperties)
	{
		rhinoConnection = null;
		this.configProperties = configProperties;
	}
	
	public void createRhinoConnection() throws Exception
	{
		if (rhinoConnection != null)
		{
			rhinoConnection.disconnect();
		}
		
		RhinoConnectionFactory.setLogWriter(new PrintWriter(System.out, true));
		
		RhinoSsl.checkSslProperties(".", configProperties);
		rhinoConnection = RhinoConnectionFactory.connect(configProperties);
		
	}

	public void closeRhinoConnection() throws Exception
	{
		if (rhinoConnection != null)
		{
			rhinoConnection.disconnect();
		}
	}
	
	
}
