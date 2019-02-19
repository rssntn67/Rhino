package com.nsn.ossgw.wh;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.Vector;

import javax.management.*;
import javax.slee.management.*;
import javax.slee.resource.ConfigProperties;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import com.opencloud.slee.remote.RhinoConnection;

public class AlarmNotificationListener implements NotificationListener 
{
	private final String openNMSIpAddress;
	private final String openNMSPort;
	private final String ossgwNodeId;
	private final String alarmComponentId;
	private final String snmpTransport;
	private final String supportedAlarmLevels;
	//private final String rhinoNodeId;
	private final String clusterId;
	private final String mapIndeterminateToLevel;
	private final String otherAlarmTypesNotRequiringMapping;
	private final String openNMSCommunity;
	
	private int snmpRetries;
	private int snmpTimeout;
	
	//private TimeTicks sysUpTime; 
	Properties configProperties;
	
	AlarmNotificationListener(Properties configProperties)
	{
		this.configProperties = configProperties;
		
		openNMSIpAddress = configProperties.getProperty("openNMSIpAddress", "localhost");
		openNMSPort = configProperties.getProperty("openNMSPort", "162");
		openNMSCommunity = configProperties.getProperty("openNMSCommunity", "public");
		ossgwNodeId = configProperties.getProperty("ossgwNodeId", "0");
		alarmComponentId = configProperties.getProperty("alarmComponentId", "Rhino");
		snmpTransport = configProperties.getProperty("snmpTransport", "udp");
		supportedAlarmLevels = configProperties.getProperty("supportedAlarmLevels", 
				"CRITICAL,MAJOR,MINOR,WARNING,INDETERMINATE,CLEAR");
		
		//rhinoNodeId = configProperties.getProperty("rhinoNodeId", "1");
		clusterId = configProperties.getProperty("clusterId", "1");

		mapIndeterminateToLevel = configProperties.getProperty("mapIndeterminateToLevel", "WARNING");

		otherAlarmTypesNotRequiringMapping = configProperties.getProperty("otherAlarmTypesNotRequiringMapping", "");

		snmpRetries = new Integer(configProperties.getProperty("snmpRetries", "1")).intValue();
		snmpTimeout = new Integer(configProperties.getProperty("snmpTimeout", "1000")).intValue();
	}
	
    public void handleNotification( Notification notification, Object handback )
    {
    	long startTime = System.currentTimeMillis();
    	
    	int nodeId = 0;
    	if (handback != null)
    	{
    		RhinoConnection rhinoConnection = (RhinoConnection)handback;
    		nodeId = rhinoConnection.getServerNodeId();
    	}
    	String rhinoNodeId = Integer.toString(nodeId);
    	
        AlarmNotification alarmNotification = (AlarmNotification) notification;

        System.out.println( "Raw Notification is " + alarmNotification.toString() );
        System.out.println( "Notification Type is " + alarmNotification.getType());
        
        /* validation checks */
        String alarmId = alarmNotification.getAlarmID();
        if (alarmId == null || alarmId.isEmpty() == true)
        {
        	System.err.println("Notification received is invalid :"+alarmNotification.toString());
        	return;
        }
        
		//Address targetAddress = GenericAddress.parse("udp:10.32.100.13/162");
		Address targetAddress = GenericAddress.parse(snmpTransport+":"+openNMSIpAddress+"/"+openNMSPort);
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString(openNMSCommunity));
		target.setAddress(targetAddress);
		target.setVersion(SnmpConstants.version2c);
		target.setRetries(snmpRetries);
		target.setTimeout(snmpTimeout);
		
		OID oid = new OID("1.3.6.1.4.1.28458.1.23.1.1.2.1");
		PDU pdu = new PDU();
		pdu.setType(PDU.TRAP);
		
		Vector vbs = new Vector();
		String alarmAdditionalText = "";
		
		long sysUpTime = (System.currentTimeMillis() - startTime)/ 10;

		// Rhino Element ID
		NotificationSource rhinoNtfnSrc = alarmNotification.getNotificationSource();
		
		String rhinoElementId = "";
		if ((rhinoNtfnSrc != null) && (rhinoNtfnSrc.getClass() != null) && 
				(rhinoNtfnSrc.getClass().getName().equals("javax.slee.management.ResourceAdaptorEntityNotification")))
		{
			ResourceAdaptorEntityNotification ntfn = 
				(ResourceAdaptorEntityNotification)rhinoNtfnSrc;
			rhinoElementId = ntfn.getEntityName();
		}
		else if ((rhinoNtfnSrc != null) && (rhinoNtfnSrc.getClass() != null) && 
				(rhinoNtfnSrc.getClass().getName().equals("javax.slee.management.SbbNotification")))
		{
			SbbNotification ntfn = (SbbNotification)rhinoNtfnSrc;
			rhinoElementId = ntfn.getService().getName();
		}
				
		// Alarm text
		String alarmMessage = "";
		if (alarmNotification.getMessage() != null)
			alarmMessage = alarmNotification.getMessage();
		
		// Alarm Severity
		String alarmSeverity = "";
		
		if (alarmNotification.getAlarmLevel().toString() != null)
			alarmSeverity = alarmNotification.getAlarmLevel().toString();
		
		if (alarmSeverity.isEmpty() != true)
		{
			alarmSeverity = alarmSeverity.toUpperCase();
			String supportedAlarmLevelArray[] = supportedAlarmLevels.split(",");
			int alarmLevelIndex = 0;
			for (; alarmLevelIndex < supportedAlarmLevelArray.length; alarmLevelIndex++)
			{
				if (supportedAlarmLevelArray[alarmLevelIndex].equals(alarmSeverity))
				{
					break;
				}
			}
			if (alarmLevelIndex == supportedAlarmLevelArray.length)
			{
				// put the unsupported alarm level in additional text
				alarmAdditionalText += "unsupportedAlarmLevel="+alarmSeverity+";";
				alarmSeverity = "INDETERMINATE";
			}
		}
		else
		{
			alarmSeverity = "INDETERMINATE";
		}
		if (alarmSeverity.equals("INDETERMINATE") == true)
		{
			// Do the mapping here as NetAct doesnt support INDETERMINATE
			alarmSeverity = mapIndeterminateToLevel;
		}
		
		// AlarmEventType
		String alarmEventType = "";
		if (alarmNotification.getAlarmType() != null)
			alarmEventType = alarmNotification.getAlarmType();
		
		System.out.println("Event type received is "+alarmEventType);
		
		String mappedEventType = "";
		if (alarmEventType.isEmpty() != true)
		{
			mappedEventType = configProperties.getProperty(alarmEventType, "");
			
			if (mappedEventType.isEmpty() == true)
			{
				if (otherAlarmTypesNotRequiringMapping.isEmpty() != true)
				{
					String otherAlarmTypesNotRequiringMappingArray[] = otherAlarmTypesNotRequiringMapping.split(",");
					int alarmTypesIndex = 0;
					for (; alarmTypesIndex < otherAlarmTypesNotRequiringMappingArray.length; alarmTypesIndex++)
					{
						System.out.println(otherAlarmTypesNotRequiringMappingArray[alarmTypesIndex]);

						if (otherAlarmTypesNotRequiringMappingArray[alarmTypesIndex].equalsIgnoreCase(alarmEventType))
						{
							mappedEventType = alarmEventType;
							break;
						}
					}
				}
			}
		}
		
		if (mappedEventType.isEmpty() == true)
		{
			// event type not found in the configurations
			System.err.println("Received an alarm with invalid event type: "+alarmEventType);
			return;
		}
		
		// probable cause
		Throwable alarmCause = alarmNotification.getCause();
		String alarmCauseMsg = "";
		if (alarmCause != null)
		{
			alarmCauseMsg = alarmNotification.getCause().getMessage();
		}
		
		// Alarm source
		String rhinoNtfnSrcStr = "";
		if (rhinoNtfnSrc.toString() != null)
			rhinoNtfnSrcStr = rhinoNtfnSrc.toString();
		
		// Additional text. put in not matched alarm type and alarm instance id, sequence number  here
		String alarmInstanceId = alarmNotification.getInstanceID();		
		if ((alarmInstanceId != null ) && (alarmInstanceId.isEmpty() != true))
		{
			alarmAdditionalText += "alarmInstanceId="+alarmInstanceId+";";
		}
		
		long alarmSeqNum = alarmNotification.getSequenceNumber();
		if (alarmSeqNum != 0)
		{
			alarmAdditionalText += "alarmSeqNum="+alarmSeqNum+";";
		}
				
		// timestamp
		Date currentTime = new Date(alarmNotification.getTimeStamp());
		
		if (currentTime == null)
		{
			//set the current time
			currentTime = new Date(System.currentTimeMillis());
		}		
		DateFormat timestampFormat = new SimpleDateFormat("MM/dd/yy HH:mm:ss");	
		String timeStampStr = timestampFormat.format(currentTime);

		vbs.add(0, new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(sysUpTime)));
		
		vbs.add(1, new VariableBinding(SnmpConstants.snmpTrapOID, oid));
		
		// Rhino alarm ID
		vbs.add(2, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.1"), 
				new OctetString(alarmComponentId+alarmId)));
		
		// Rhino alarm component
		vbs.add(3, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.2"), 
				new OctetString(alarmComponentId)));
		
		// Rhino alarm emID
		vbs.add(4, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.3"), 
				new OctetString(ossgwNodeId)));
		
		// Rhino cluster ID
		vbs.add(5, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.4"), 
				new OctetString(clusterId)));
		
		// Rhino node ID
		
		vbs.add(6, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.5"), 
				new OctetString(rhinoNodeId)));
		
		vbs.add(7, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.6"), 
				new OctetString(rhinoElementId)));
		System.out.println("rhinoElementId:"+rhinoElementId);

		vbs.add(8, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.7"), 
				new OctetString(alarmMessage)));
		System.out.println("alarmMessage:"+alarmMessage);
				
		vbs.add(9, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.8"), 
				new OctetString(alarmAdditionalText)));
		System.out.println("alarmAdditionalText:"+alarmAdditionalText);

		vbs.add(10, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.9"), 
				new OctetString(alarmSeverity)));
		System.out.println("alarmSeverity:"+alarmSeverity);			
		
		vbs.add(11, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.10"), 
				new OctetString(mappedEventType)));
		System.out.println("mappedEventType:"+mappedEventType);
		
		vbs.add(12, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.11"), 
			new OctetString(alarmCauseMsg)));
		System.out.println("alarmCauseMsg:"+alarmCauseMsg);
		
		vbs.add(13, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.12"), 
				new OctetString(rhinoNtfnSrcStr)));
		System.out.println("rhinoNtfnSrcStr:"+rhinoNtfnSrcStr);
		
		vbs.add(14, new VariableBinding(new OID("1.3.6.1.4.1.28458.1.23.1.1.1.1.1.13"), 
					new OctetString(timeStampStr)));
		System.out.println("timeStamp:"+timeStampStr);
		
	    for (int i=0; i<vbs.size(); i++) 
	    {
	    	pdu.add((VariableBinding)vbs.get(i));
	    }

		try
		{
			DefaultUdpTransportMapping udpTransportMap = new DefaultUdpTransportMapping();
		    Snmp snmp = new Snmp(udpTransportMap);
		    ResponseEvent response =  snmp.send(pdu, target);
		    System.out.println("pdu:"+pdu);
		    System.out.println("response:"+response);
		    snmp.close();
		}
		catch (Exception e) 
		{
		  System.err.println("Exception received in handleNotification is" +e);
		}
    }
    
}
