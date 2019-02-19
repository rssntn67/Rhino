package com.nsn.ossgw.wh;

import java.util.Properties;

import javax.slee.management.*;
import javax.slee.facilities.*;
import javax.management.*;

public class RhinoJmxAlarmsClient extends RhinoJmxClient
{
	private AlarmNotificationListener alarmListener;
	private ObjectName alarmMbeanObject;
	private AlarmLevelFilter alarmLevelFilter;
	RhinoJmxAlarmsClient(Properties configProperties)
	{
		super(configProperties);
		
		alarmListener = null;
		alarmMbeanObject = null;
		alarmLevelFilter = null;
	}

	public void run()
	{
		System.out.println("In run method of RhinoJmxAlarmsClient");
		try
		{
			initMbeanCollection();
			listenForAlarms();

			/*
			while (true)
			{
				yield();
			}
			*/
			
		}
		catch (Exception e)
		{
			System.err.println("Exception received in run of RhinoJmxAlarmsClient is " +e);			
			try
			{
				if ((rhinoConnection != null) && (alarmListener != null))
				{
					rhinoConnection.removeNotificationListener(alarmMbeanObject, alarmListener, 
							alarmLevelFilter, null );
				}
				closeRhinoConnection();
			}
			catch (Exception closeException)
			{
				System.err.println("Close connection exception received in run of RhinoJmxAlarmsClient is " +closeException);
			}
		}
	}

	private void initMbeanCollection() throws Exception
	{
		createRhinoConnection();
	}
	
	private void listenForAlarms() throws Exception
	{
		// Add Alarm Notification Listener
		alarmMbeanObject = new ObjectName(AlarmMBean.OBJECT_NAME );
		alarmLevelFilter = new AlarmLevelFilter(AlarmLevel.INDETERMINATE );
		alarmListener = new AlarmNotificationListener(configProperties);
		rhinoConnection.addNotificationListener(alarmMbeanObject, alarmListener, alarmLevelFilter, this.rhinoConnection );
	}

}
