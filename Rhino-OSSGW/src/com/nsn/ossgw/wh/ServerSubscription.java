package com.nsn.ossgw.wh;

public class ServerSubscription {

    public int getSubscriptionId()
    {
        return subscriptionId;
    }

    public String getParameterSetName()
    {
        return parameterSetName;
    }

    public String getCounterName()
    {
        return counterName;
    }

    public String getSampleName()
    {
        return sampleName;
    }

    public int getNodeId()
    {
    	return this.nodeId;
    }
    
    public long[] getData()
    {
    	return this.data;
    }
    
    
    public void setSubscriptionId(int subscriptionId)
    {
    	this.subscriptionId = subscriptionId;
    }
    
    public void setNodeId(int nodeId)
    {
    	this.nodeId = nodeId;
    }
    
    public void setData(long[] data)
    {
    	this.data[0] = data[0];
    	if (data.length > 1)
    	{
    		this.data[1] = data[1];
    	}
    }
    
    private int subscriptionId;
    private int nodeId;
    
    private final String parameterSetName;
    private final String counterName;
    private final String sampleName;
  
    private long []data;
    
    public ServerSubscription(String parameterSetName, String counterName, String sampleName)
    {
    	this.parameterSetName = parameterSetName;
    	this.counterName = counterName;
    	this.sampleName = sampleName;
    	this.data = new long[2];
    }

}
