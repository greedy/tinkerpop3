#!/bin/bash

set -e
set -u

case `uname` in
  CYGWIN*)
    CP="`dirname $0`"/../config
    CP="$CP":$( echo `dirname $0`/../lib/*.jar . | sed 's/ /;/g')
    ;;
  *)
    CP="`dirname $0`"/../config
    CP="$CP":$( echo `dirname $0`/../lib/*.jar . | sed 's/ /:/g')
esac

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
  SOURCE="$(readlink "$SOURCE")"
  [[ $SOURCE != /* ]] && SOURCE="$DIR/$SOURCE"
done
DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
CP=$CP:$( find -L "$DIR"/../ext -mindepth 1 -maxdepth 1 -type d | \
          sort | sed 's/$/\/*/' | tr '\n' ':' )

export CLASSPATH="${CLASSPATH:-}:$CP"

# Find Java
if [ -z "${JAVA_HOME:-}" ]; then
    JAVA="java -server"
else
    JAVA="$JAVA_HOME/bin/java -server"
fi

# Set default message threshold for Log4j Gremlin's console appender
if [ -z "${GREMLIN_LOG_LEVEL:-}" ]; then
    GREMLIN_LOG_LEVEL=WARN
fi

# Script debugging is disabled by default, but can be enabled with -l
# TRACE or -l DEBUG or enabled by exporting
# SCRIPT_DEBUG=nonemptystring to gremlin.sh's environment
if [ -z "${SCRIPT_DEBUG:-}" ]; then
    SCRIPT_DEBUG=
fi

# Process options
MAIN_CLASS=com.tinkerpop.gremlin.console.Console
while getopts "elv" opt; do
    case "$opt" in
    e) MAIN_CLASS=com.tinkerpop.gremlin.groovy.jsr223.ScriptExecutor
       # For compatibility with behavior pre-Titan-0.5.0, stop
       # processing gremlin.sh arguments as soon as the -e switch is
       # seen; everything following -e becomes arguments to the
       # ScriptExecutor main class
       break;;
    l) eval GREMLIN_LOG_LEVEL=\$$OPTIND
       OPTIND="$(( $OPTIND + 1 ))"
       if [ "$GREMLIN_LOG_LEVEL" = "TRACE" -o \
            "$GREMLIN_LOG_LEVEL" = "DEBUG" ]; then
	   SCRIPT_DEBUG=y
       fi
       ;;
    v) MAIN_CLASS=com.tinkerpop.gremlin.util.Gremlin
    esac
done

# Remove processed options from $@. Anything after -e is preserved by the break;; in the case
shift $(( $OPTIND - 1 ))

if [ -z "${JAVA_OPTIONS:-}" ]; then
    JAVA_OPTIONS="-Dlog4j.configuration=conf/log4j-repl.properties -Dgremlin.log4j.level=$GREMLIN_LOG_LEVEL"
fi

if [ -n "$SCRIPT_DEBUG" ]; then
    echo "CLASSPATH: $CLASSPATH"
    set -x
fi

# Start the JVM, execute the application, and return its exit code
exec $JAVA $JAVA_OPTIONS $MAIN_CLASS "$@"
