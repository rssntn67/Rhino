#!/bin/sh -

OSSGW_HOME=`dirname $0`

cd $OSSGW_HOME

    PIDFILE=$OSSGW_HOME/ossgw.pid
    if [ -f $PIDFILE ] ; then
        kill -9 `cat $PIDFILE`
        rm -f $PIDFILE
    else
        echo "PID file not found. Perhaps Ossgw is not running?"
        echo "Shutdown failed."
        exit 1
    fi


