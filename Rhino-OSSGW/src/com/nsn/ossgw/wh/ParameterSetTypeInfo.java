package com.nsn.ossgw.wh;

import java.util.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

public class ParameterSetTypeInfo
{

    public ParameterSetTypeInfo(CompositeData desc)
    {
        this.desc = desc;
        stats = (TabularData)desc.get("stats");
        Collection c = stats.values();
        for(Iterator iterator = c.iterator(); iterator.hasNext();)
        {
            CompositeData cd = (CompositeData)iterator.next();
            String type = (String)cd.get("type");
            if(type != null && type.equalsIgnoreCase("counter"))
                counters.add(cd.get("name"));
            else
            if(type != null && type.equalsIgnoreCase("sample"))
                samples.add(cd.get("name"));
            else
                throw new IllegalArgumentException((new StringBuilder()).append("Unable to determine type of statistic ").append(cd).toString());
        }

        allStats.addAll(counters);
        allStats.addAll(samples);
    }

    public String[] getCounters()
    {
        return (String[])(String[])counters.toArray(new String[counters.size()]);
    }

    public String[] getSamples()
    {
        return (String[])(String[])samples.toArray(new String[samples.size()]);
    }

    public String[] getStatisticNames()
    {
        return (String[])(String[])allStats.toArray(new String[allStats.size()]);
    }

    public String getDescription()
    {
        return (String)desc.get("description");
    }

    public String getDescription(String name)
    {
        return (String)tabularLookup(name, "description", stats);
    }

    public int getStatId(String name)
    {
        return ((Integer)tabularLookup(name, "id", stats)).intValue();
    }

    public String getShortName(String name)
    {
        return (String)tabularLookup(name, "short-name", stats);
    }

    public String getUnits(String name)
    {
        return (String)tabularLookup(name, "unit-label", stats);
    }

    public String getSourceUnits(String name)
    {
        return (String)tabularLookup(name, "source-units", stats);
    }

    public String getDisplayUnits(String name)
    {
        return (String)tabularLookup(name, "display-units", stats);
    }

    public boolean isCounter(String name)
    {
        return ((String)tabularLookup(name, "type", stats)).equalsIgnoreCase("counter");
    }

    public boolean isGaugeCounter(String name)
    {
        String type = (String)tabularLookup(name, "default-view", stats);
        return "gauge".equals(type);
    }

    public boolean isReplicatedStat(String name)
    {
        return ((Boolean)tabularLookup(name, "is-replicated", stats)).booleanValue();
    }

    private Object tabularLookup(String name, String field, TabularData table)
        throws IllegalArgumentException
    {
        CompositeData cd = table.get(new Object[] {
            name
        });
        if(cd == null)
            throw new IllegalArgumentException((new StringBuilder()).append("No record named: ").append(name).toString());
        else
            return cd.get(field);
    }

    private final TabularData stats;
    private final CompositeData desc;
    private final SortedSet counters = new TreeSet();
    private final SortedSet samples = new TreeSet();
    private final TreeSet allStats = new TreeSet();
}