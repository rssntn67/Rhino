#!/bin/sh -

OSSGW_HOME=`dirname $0`

cd $OSSGW_HOME

PIDFILE=$OSSGW_HOME/ossgw.pid
if [ -f $PIDFILE ]; then
    PID=`cat $PIDFILE`
    kill -0 $PID 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "Ossgw already running with pid: $PID"
        exit 1
    fi
fi

java  -classpath ".:bin:lib/jainslee-api-1.1.jar:lib/rhino-client.jar:lib/rhino-remote-2.1.jar:lib/snmp4j-1.11.jar" com.nsn.ossgw.wh.OssGwMain > ossgw.log 2>&1 &

CHILDPID=$!
echo $CHILDPID > $PIDFILE
