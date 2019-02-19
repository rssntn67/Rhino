package com.nsn.ossgw.wh;

import java.io.File;
import java.io.FileWriter;
import java.text.*;
import java.util.*;

import com.opencloud.slee.client.Alarm;
import com.opencloud.slee.remote.*;
import com.opencloud.rhino.management.alarm.AlarmMBean;
import com.opencloud.rhino.monitoring.stats.ext.*;


public class RhinoJmxStatsClient extends RhinoJmxClient
{
	private StatsManagementMBean statsManagement;
	private long sessionId;
	private int rhinoNodeId;
	private int servicesInstanceId;
	private String ossgwNodeId;
	
	private HashMap subscriptionsById;
	
	private HashMap platformSubscr;
	private int totalPlatformSubscr;

	private HashMap cginRaSubscr;
	private int totalCginRaSubscr;

	private HashMap dbRaSubscr;
	private int totalDbRaSubscr;

	private HashMap correlationRaSubscr;
	private int totalCorrelationRaSubscr;

	private HashMap servicesSubscr;
	private int totalServicesSubscr;

	private String currentTimeStamp;
	private int omesFilesInterval;
	private final int omesFilesMinInterval;
	private int maxCollectingInterval;
	private final String filenamePrefix;
	private final String filenameSuffix;
	private final String omesDir;
	
	private final String ossgwDNName;
	private final String rhinoPlatformDnName;
	private final String servicesDnName;
	private final String raDnName;
	private final String servicesSbbDelimiter;
	private final String supportedParamSetNames;
	
	private String fileTimeStamp;
	
	RhinoJmxStatsClient(Properties configProperties)
	{
		super(configProperties);
		
		statsManagement = null;
		sessionId = 0;
		rhinoNodeId = 0;
		subscriptionsById = new HashMap();
		
		platformSubscr = new HashMap();
		totalPlatformSubscr = 0;
		
		cginRaSubscr = new HashMap();
		totalCginRaSubscr = 0;
		
		dbRaSubscr = new HashMap();
		totalDbRaSubscr = 0;
		
		correlationRaSubscr = new HashMap();
		totalCorrelationRaSubscr = 0;
		
		servicesSubscr = new HashMap();
		totalServicesSubscr = 0;
		
		fileTimeStamp = null;
		
		ossgwNodeId = configProperties.getProperty("ossgwNodeId", "0");
		omesFilesInterval = new Integer(configProperties.getProperty("omesFilesInterval", "9000000")).intValue();
		omesFilesMinInterval = new Integer(configProperties.getProperty("omesFilesMinInterval", "60000")).intValue();
		maxCollectingInterval = new Integer(configProperties.getProperty("maxCollectingInterval", "20000")).intValue();
		servicesInstanceId = new Integer(configProperties.getProperty("servicesInstanceId", "0")).intValue();
		filenamePrefix = configProperties.getProperty("omesFilesPrefix", "PEDR_");
		filenameSuffix = configProperties.getProperty("omesFilesSuffix", ".dat");
		omesDir = configProperties.getProperty("omesFilesDir", ".");
		ossgwDNName = configProperties.getProperty("ossgwDNName", "ONMS");
		rhinoPlatformDnName = configProperties.getProperty("rhinoPlatformDnName", "RPLT");
		servicesDnName = configProperties.getProperty("servicesDnName", "SERS");
		raDnName = configProperties.getProperty("raDnName", "RACG");
		servicesSbbDelimiter = configProperties.getProperty("servicesSbbDelimiter", ".");
		supportedParamSetNames = configProperties.getProperty("supportedParamSetNames", "");
		
		if (omesFilesInterval < omesFilesMinInterval)
			omesFilesInterval = omesFilesMinInterval;
		
		if (omesFilesInterval < maxCollectingInterval)
			maxCollectingInterval = omesFilesInterval;
		
	}

	public void run()
	{
		System.out.println("In run method of RhinoJmxStatsClient");
		try
		{
			initMbeanCollection();
			while (true)
			{
				long currentTime = System.currentTimeMillis();
				//DateFormat df = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss z");			
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sssZ");			
				DateFormat df2 = new SimpleDateFormat("yyyyMMddHHmmss");			
				String tempTimeStamp = df.format(new Date(currentTime));
				fileTimeStamp = df2.format(new Date(currentTime));
				
				System.out.println("Temp timestamp is " +tempTimeStamp);			
				int tempLen = tempTimeStamp.length();
				currentTimeStamp = "";
				currentTimeStamp = tempTimeStamp.substring(0, tempLen-2)+":"+tempTimeStamp.substring(tempLen-2,tempLen);
				 
				System.out.println("Current timestamp is " +currentTimeStamp);
				
				int collectFrequency = omesFilesInterval/omesFilesMinInterval - 1;
				System.out.println("collectFrequency is " +collectFrequency);

				int freq = 0;
				while (freq < collectFrequency)
				{
					Thread.sleep(omesFilesMinInterval);
					Snapshot[] snapshots = statsManagement.getAccumulatedSamples(sessionId);
					freq++;
				}
				Thread.sleep(omesFilesMinInterval);
				collectStats();
				writeStats();
			}
		}
		catch (Exception e)
		{
			System.err.println("Exception received in run of RhinoJmxStatsClient is " +e);			
		}
		finally
		{
			try
			{
				closeRhinoConnection();
			}
			catch (Exception closeException)
			{
				System.err.println("Close connection exception received in run of RhinoJmxStatsClient is " +closeException);
			}
		}
	}

	private void initMbeanCollection()  throws Exception
	{
		createRhinoConnection();
		
		statsManagement = (StatsManagementMBean) 
			RhinoManagement.getStatsManagementMBean(rhinoConnection);
		
		subscribeCounters();
		
		// rhino will begin gathering samples
		statsManagement.startCollecting(sessionId, maxCollectingInterval);		
	}
	
	private void subscribeCounters() throws Exception
	{
		sessionId = statsManagement.createSession();
		String parameterSetNames[] = statsManagement.getRootParameterSetNames();
		
		String supportedParamSetNamesArray[] = supportedParamSetNames.split(",");
		for (int setIndex = 0; setIndex < parameterSetNames.length; setIndex++)
		{
			int supportedParamSetIndex = 0;
			for (; supportedParamSetIndex < supportedParamSetNamesArray.length; supportedParamSetIndex++)
			{
				if (supportedParamSetNamesArray[supportedParamSetIndex].equals(parameterSetNames[setIndex]))
				{
					break;
				}
			}
			if (supportedParamSetIndex == supportedParamSetNamesArray.length)
			{
    			System.out.println(parameterSetNames[setIndex]+" ignored as Ossgw is not configured to support it");
				continue;
			}

	        String parameterType = statsManagement.getParameterSetType(parameterSetNames[setIndex]);
	        		
	        if(parameterType != null && parameterType.isEmpty() != true)
	        {
	        	ParameterSetTypeInfo parameterWrapper = new ParameterSetTypeInfo(
	        			statsManagement.getParameterSetTypeDescription(parameterType));
	        	
	        	String counters[] = parameterWrapper.getCounters();
	        	if(counters.length > 0)
	        	{
	        		System.out.println();
        			System.out.println("Counter for "+parameterSetNames[setIndex]+" are ");
	        		for (int counterIndex=0; counterIndex <counters.length; counterIndex++)
	        		{
	        			System.out.println(counters[counterIndex]);
	        			ServerSubscription subscr = new ServerSubscription(parameterSetNames[setIndex], counters[counterIndex], null);
	        			int subscriptionId = statsManagement.subscribeCounter(sessionId, 0, 
	        					parameterSetNames[setIndex], 
	        					counters[counterIndex], SubscriptionMode.SIMPLE_GAUGE);
	        			subscriptionsById.put(subscriptionId, subscr);
	        			platformSubscr.put(totalPlatformSubscr, subscr);
	        			totalPlatformSubscr++;
	        		}
	        	}
	        }
        	else
        	{
    	        System.out.println("No parameter set type found for "+parameterSetNames[setIndex]);
    	        String keys[] = statsManagement.getParameterSetNames(parameterSetNames[setIndex]);
    	        for(int keysIndex = 0; keysIndex < keys.length; keysIndex++)
    	        {
    	        	String key = keys[keysIndex];
        			System.out.println("Key for "+parameterSetNames[setIndex]+" is "+key);
        			
        			String keyName = null;
        			/* processing for services */
        			/* TBD - Below Strings can be made configurable */
        			if (key.startsWith("SLEE-Usage") && 
        				((key.endsWith("UsageParameterSet") == true) || 
        				 (key.endsWith("IVRProxyStats") == true) ))
        			{
        				/* key is of type 
        				SLEE-Usage.Services.ServiceID[name=BarredAnalysisService,vendor=NSN,version=1.0].SbbID[name=BarredAnalysisSbb,vendor=NSN,version=1.0].BarredAnalysisUsageParameterSet
        				or 
        				SLEE-Usage.Services.ServiceID[name=IVR Proxy Service,vendor=OpenCloud,version=0.1].SbbID[name=IVRProxySbb,vendor=OpenCloud,version=0.1].IVRProxyStats
        				*/
        				/* split with name= */
        				String tempKeys[] = key.split("name=", 10);
        				if (tempKeys.length != 3)
        					continue;
        				
        				/* now split tempKeys[1] and tempKeys[2] with ,*/
        				String serviceName[] = tempKeys[1].split(",", 10);
        				if (serviceName.length < 1)
        					continue;
        				
        				String sbbName[] = tempKeys[2].split(",", 10);
        				if (sbbName.length < 1)
        					continue;
        				
        				/* remove spaces from service name if present */
        				serviceName[0] = serviceName[0].replaceAll(" ", "");
        				
        				keyName = serviceName[0] + servicesSbbDelimiter + sbbName[0];
            			if (keyName != null && keyName.isEmpty() != true)
            			{
            				System.out.println("Key for "+parameterSetNames[setIndex]+" is "+key);
            				System.out.println("KeyName for "+parameterSetNames[setIndex]+" is "+keyName);

            				String keyType = statsManagement.getParameterSetType(key);
            				if(keyType != null && keyType.isEmpty() != true)
            				{
            					ParameterSetTypeInfo keyWrapper = new ParameterSetTypeInfo(
        		        			statsManagement.getParameterSetTypeDescription(keyType));
        		        	
            					String counters[] = keyWrapper.getCounters();
            					if(counters.length > 0)
            					{
            						System.out.println();
            						System.out.println("Counter for "+keyName+" are");

            						for (int counterIndex=0; counterIndex <counters.length; counterIndex++)
            						{
            							System.out.println(counters[counterIndex]);
            							ServerSubscription subscr = new ServerSubscription(keyName, counters[counterIndex], null);
            							int subscriptionId = statsManagement.subscribeCounter(sessionId, 0, 
        		        					key, 
        		        					counters[counterIndex], SubscriptionMode.SIMPLE_GAUGE);
            							subscriptionsById.put(subscriptionId, subscr);
            							servicesSubscr.put(totalServicesSubscr, subscr);
            							totalServicesSubscr++;
            						}
            					}
            				}
            			}

        			}
        			/* processing for CGIN RA*/
        			/* TBD - Below Strings can be made configurable */
        			else if ((key.startsWith("CGIN")) &&
        					((key.endsWith("CGINInbound") == true) ||
        					(key.endsWith("TCAP") == true) ||
        					(key.endsWith("TimingWheelStats") == true)))
        			{
        				/* key is of type 
        				CGIN-Ericsson-CS1.cginra.CGINInbound
        				*/
        				if (key.endsWith("CGINInbound") == true)
        				{
        					keyName = "CGINInbound";
        				}
        				else if (key.endsWith("TCAP") == true)
        				{
        					keyName = "TCAP";
        				}
        				else
        				{
        					keyName = "TimingWheelStats";
        				}
            			if (keyName != null && keyName.isEmpty() != true)
            			{
            				System.out.println("Key for "+parameterSetNames[setIndex]+" is "+key);
            				System.out.println("KeyName for "+parameterSetNames[setIndex]+" is "+keyName);

            				String keyType = statsManagement.getParameterSetType(key);
            				if(keyType != null && keyType.isEmpty() != true)
            				{
            					ParameterSetTypeInfo keyWrapper = new ParameterSetTypeInfo(
        		        			statsManagement.getParameterSetTypeDescription(keyType));
        		        	
            					String counters[] = keyWrapper.getCounters();
            					if(counters.length > 0)
            					{
            						System.out.println();
            						System.out.println("Counter for "+keyName+" are");

            						for (int counterIndex=0; counterIndex <counters.length; counterIndex++)
            						{
            							System.out.println(counters[counterIndex]);
            							ServerSubscription subscr = new ServerSubscription(keyName, counters[counterIndex], null);
            							int subscriptionId = statsManagement.subscribeCounter(sessionId, 0, 
        		        					key, 
        		        					counters[counterIndex], SubscriptionMode.SIMPLE_GAUGE);
            							subscriptionsById.put(subscriptionId, subscr);
            							cginRaSubscr.put(totalCginRaSubscr, subscr);
            							totalCginRaSubscr++;
            						}
            					}
            				}
            			}

        			}
        			/* processing for DB RA*/
        			/* TBD - Below Strings can be made configurable */
        			else if ((key.startsWith("DatabaseQuery")) &&
        					((key.endsWith("DataSource") == true) || 
        					(key.endsWith("Query") == true) || 
        					(key.endsWith("WorkerPool") == true)))
        			{
        				/* key is of type 
        				DatabaseQuery.dbquery-0.DataSource
        				*/
        				if (key.endsWith("DataSource") == true)
        				{
        					keyName = "DataSource";
        				}
        				else if (key.endsWith("Query") == true)
        				{
        					keyName = "Query";
        				}
        				else
        				{
        					keyName = "WorkerPool";
        				}
            			if (keyName != null && keyName.isEmpty() != true)
            			{
            				System.out.println("Key for "+parameterSetNames[setIndex]+" is "+key);
            				System.out.println("KeyName for "+parameterSetNames[setIndex]+" is "+keyName);

            				String keyType = statsManagement.getParameterSetType(key);
            				if(keyType != null && keyType.isEmpty() != true)
            				{
            					ParameterSetTypeInfo keyWrapper = new ParameterSetTypeInfo(
        		        			statsManagement.getParameterSetTypeDescription(keyType));
        		        	
            					String counters[] = keyWrapper.getCounters();
            					if(counters.length > 0)
            					{
            						System.out.println();
            						System.out.println("Counter for "+keyName+" are");

            						for (int counterIndex=0; counterIndex <counters.length; counterIndex++)
            						{
            							System.out.println(counters[counterIndex]);
            							ServerSubscription subscr = new ServerSubscription(keyName, counters[counterIndex], null);
            							int subscriptionId = statsManagement.subscribeCounter(sessionId, 0, 
        		        					key, 
        		        					counters[counterIndex], SubscriptionMode.SIMPLE_GAUGE);
            							subscriptionsById.put(subscriptionId, subscr);
            							dbRaSubscr.put(totalDbRaSubscr, subscr);
            							totalDbRaSubscr++;
            						}
            					}
            				}
            			}
        			}
        			
        			/* processing for Correlation RA*/
        			/* TBD - Below Strings can be made configurable */
        			else if ((key.startsWith("SLEE-Usage")) &&
        					(key.endsWith("correlationra") == true))
        			{
        				/* key is of type 
        				SLEE-Usage.RAEntities.correlationra
        				*/
        				keyName = "correlationra";
        				System.out.println("Key for "+parameterSetNames[setIndex]+" is "+key);
        				System.out.println("KeyName for "+parameterSetNames[setIndex]+" is "+keyName);

        				String keyType = statsManagement.getParameterSetType(key);
        				if(keyType != null && keyType.isEmpty() != true)
        				{
        					ParameterSetTypeInfo keyWrapper = new ParameterSetTypeInfo(
    		        			statsManagement.getParameterSetTypeDescription(keyType));
    		        	
        					String counters[] = keyWrapper.getCounters();
        					if(counters.length > 0)
        					{
        						System.out.println();
        						System.out.println("Counter for "+keyName+" are");

        						for (int counterIndex=0; counterIndex <counters.length; counterIndex++)
        						{
        							System.out.println(counters[counterIndex]);
        							ServerSubscription subscr = new ServerSubscription(keyName, counters[counterIndex], null);
        							int subscriptionId = statsManagement.subscribeCounter(sessionId, 0, 
    		        					key, 
    		        					counters[counterIndex], SubscriptionMode.SIMPLE_GAUGE);
        							subscriptionsById.put(subscriptionId, subscr);
        							correlationRaSubscr.put(totalCorrelationRaSubscr, subscr);
        							totalCorrelationRaSubscr++;
        						}
        					}
        				}

        			}
        			
        			else
        			{
        				continue;
        			}
    	        }
        	}
		}
	}
	
	private void collectStats() throws Exception
	{	
		Snapshot[] snapshots = statsManagement.getAccumulatedSamples(sessionId);
		/* only use the last snapshot */
		if (snapshots.length >= 1)
		{
			int snapshotIndex = snapshots.length - 1;
			Snapshot snapshot = snapshots[snapshotIndex];
			int[] subsIds = snapshot.getSubscriptionIds();
			int[] nodeIds = snapshot.getRespondingNodeIds();
			rhinoNodeId = nodeIds[0];
			System.out.println("collectStats: snapshot:"+snapshotIndex+" subscriptions:"+subsIds.length);
			
			for (int subsIndex = 0; subsIndex < subsIds.length; subsIndex++)
			{
				final int subscriptionId = subsIds[subsIndex];
				ServerSubscription subscr = (ServerSubscription)subscriptionsById.get(subscriptionId);
				if ((subscr != null) && (subscr.getCounterName() != null))
				{
					subscr.setNodeId(nodeIds[subsIndex]);
					subscr.setData(snapshot.getData()[subsIndex]);
					System.out.println("nodeId: "+nodeIds[subsIndex]+" "+subscr.getCounterName()+" "+subscr.getParameterSetName()+"="+snapshot.getData()[subsIndex][0]);
				}
				else
				{
					System.err.println("Unknown subscription id: "+subscriptionId);
				}
			}
		}
	}

	private void writeStats() throws Exception
	{
		String fileName = omesDir+"/"+ filenamePrefix + ossgwNodeId + "_" + fileTimeStamp + filenameSuffix;
		System.out.println("Current filename is " +fileName);

		File omesFile = new File(fileName);
		FileWriter omesFileWriter = new FileWriter(omesFile);
		omesFileWriter.write("<?xml version=\"1.0\"?>\n");
		omesFileWriter.write("<OMeS>\n");
		omesFileWriter.write("\t<PMSetup startTime=\""+currentTimeStamp+"\" interval=\""+(int)(omesFilesInterval/60000)+"\">\n");
		
		/* platform counters */
		omesFileWriter.write("\t\t<PMMOResult>\n");
		omesFileWriter.write("\t\t\t<MO>\n");
		omesFileWriter.write("\t\t\t\t<DN>"+ossgwDNName+"-"+ossgwNodeId+"/"+rhinoPlatformDnName+"-"+rhinoNodeId+"</DN>\n");
		omesFileWriter.write("\t\t\t</MO>\n");
		
		String lastPlatformParam = "";
		
		for (int subscrIndex = 0; subscrIndex < totalPlatformSubscr; subscrIndex++) 
		{
			ServerSubscription subscr = (ServerSubscription)platformSubscr.get(subscrIndex);

			if (lastPlatformParam.equals(subscr.getParameterSetName()) != true)
			{
				if (lastPlatformParam.equals("") == true)
				{
					/* for the first time */
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				else
				{
					omesFileWriter.write("\t\t\t</PMTarget>\n");
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				lastPlatformParam = subscr.getParameterSetName();
			}
			omesFileWriter.write("\t\t\t\t<" + subscr.getCounterName() + ">" + subscr.getData()[0] + "</" + subscr.getCounterName() + ">\n");
		}
		omesFileWriter.write("\t\t\t</PMTarget>\n");
		omesFileWriter.write("\t\t</PMMOResult>\n");
		
		
		/* CGIN ra counters */
		omesFileWriter.write("\t\t<PMMOResult>\n");
		omesFileWriter.write("\t\t\t<MO>\n");
		
		/* TBD - Below CGIN can be made configurable */
		omesFileWriter.write("\t\t\t\t<DN>"+ossgwDNName+"-"+ossgwNodeId+"/"+rhinoPlatformDnName+"-"+rhinoNodeId+"/"+raDnName+"-CGIN</DN>\n");
		omesFileWriter.write("\t\t\t</MO>\n");
		
		String lastCginRaParam = "";
		
		for (int subscrIndex = 0; subscrIndex < totalCginRaSubscr; subscrIndex++) 
		{
			ServerSubscription subscr = (ServerSubscription)cginRaSubscr.get(subscrIndex);

			if (lastCginRaParam.equals(subscr.getParameterSetName()) != true)
			{
				if (lastCginRaParam.equals("") == true)
				{
					/* for the first time */
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				else
				{
					omesFileWriter.write("\t\t\t</PMTarget>\n");
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				lastCginRaParam = subscr.getParameterSetName();
			}
			omesFileWriter.write("\t\t\t\t<" + subscr.getCounterName() + ">" + subscr.getData()[0] + "</" + subscr.getCounterName() + ">\n");
		}
		omesFileWriter.write("\t\t\t</PMTarget>\n");
		omesFileWriter.write("\t\t</PMMOResult>\n");
		
		
		/* Db ra counters */
		omesFileWriter.write("\t\t<PMMOResult>\n");
		omesFileWriter.write("\t\t\t<MO>\n");
		
		/* TBD - Below DBRA can be made configurable */
		omesFileWriter.write("\t\t\t\t<DN>"+ossgwDNName+"-"+ossgwNodeId+"/"+rhinoPlatformDnName+"-"+rhinoNodeId+"/"+raDnName+"-DBRA</DN>\n");
		omesFileWriter.write("\t\t\t</MO>\n");
		
		String lastDbRaParam = "";
		
		for (int subscrIndex = 0; subscrIndex < totalDbRaSubscr; subscrIndex++) 
		{
			ServerSubscription subscr = (ServerSubscription)dbRaSubscr.get(subscrIndex);

			if (lastDbRaParam.equals(subscr.getParameterSetName()) != true)
			{
				if (lastDbRaParam.equals("") == true)
				{
					/* for the first time */
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				else
				{
					omesFileWriter.write("\t\t\t</PMTarget>\n");
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				lastDbRaParam = subscr.getParameterSetName();
			}
			omesFileWriter.write("\t\t\t\t<" + subscr.getCounterName() + ">" + subscr.getData()[0] + "</" + subscr.getCounterName() + ">\n");
		}
		omesFileWriter.write("\t\t\t</PMTarget>\n");
		omesFileWriter.write("\t\t</PMMOResult>\n");
		
		/* Correlation ra counters */
		omesFileWriter.write("\t\t<PMMOResult>\n");
		omesFileWriter.write("\t\t\t<MO>\n");
		
		/* TBD - Below Correlation RA can be made configurable */
		omesFileWriter.write("\t\t\t\t<DN>"+ossgwDNName+"-"+ossgwNodeId+"/"+rhinoPlatformDnName+"-"+rhinoNodeId+"/"+raDnName+"-Correlation RA</DN>\n");
		omesFileWriter.write("\t\t\t</MO>\n");
		
		String lastCorrelationRaParam = "";
		
		for (int subscrIndex = 0; subscrIndex < totalCorrelationRaSubscr; subscrIndex++) 
		{
			ServerSubscription subscr = (ServerSubscription)correlationRaSubscr.get(subscrIndex);

			if (lastCorrelationRaParam.equals(subscr.getParameterSetName()) != true)
			{
				if (lastCorrelationRaParam.equals("") == true)
				{
					/* for the first time */
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + "Correlation" + "\">\n");
				}
				else
				{
					omesFileWriter.write("\t\t\t</PMTarget>\n");
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				lastCorrelationRaParam = subscr.getParameterSetName();
			}
			omesFileWriter.write("\t\t\t\t<" + subscr.getCounterName() + ">" + subscr.getData()[0] + "</" + subscr.getCounterName() + ">\n");
		}
		omesFileWriter.write("\t\t\t</PMTarget>\n");
		omesFileWriter.write("\t\t</PMMOResult>\n");
		
		
		/* service counters */
		omesFileWriter.write("\t\t<PMMOResult>\n");
		omesFileWriter.write("\t\t\t<MO>\n");
		omesFileWriter.write("\t\t\t\t<DN>"+ossgwDNName+"-"+ossgwNodeId+"/"+rhinoPlatformDnName+"-"+rhinoNodeId+"/"+servicesDnName+"-"+servicesInstanceId+"</DN>\n");
		omesFileWriter.write("\t\t\t</MO>\n");
		
		String lastServicesParam = "";
		
		for (int subscrIndex = 0; subscrIndex < totalServicesSubscr; subscrIndex++) 
		{
			ServerSubscription subscr = (ServerSubscription)servicesSubscr.get(subscrIndex);

			if (lastServicesParam.equals(subscr.getParameterSetName()) != true)
			{
				if (lastServicesParam.equals("") == true)
				{
					/* for the first time */
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				else
				{
					omesFileWriter.write("\t\t\t</PMTarget>\n");
					omesFileWriter.write("\t\t\t<PMTarget measurementType=\"" + subscr.getParameterSetName() + "\">\n");
				}
				lastServicesParam = subscr.getParameterSetName();
			}
			omesFileWriter.write("\t\t\t\t<" + subscr.getCounterName() + ">" + subscr.getData()[0] + "</" + subscr.getCounterName() + ">\n");
		}
		omesFileWriter.write("\t\t\t</PMTarget>\n");
		omesFileWriter.write("\t\t</PMMOResult>\n");
		
		/* end the PMSetup and OMeS tags */
		omesFileWriter.write("\t</PMSetup>\n");
		omesFileWriter.write("</OMeS>\n");
		omesFileWriter.close();
	}
	

}
