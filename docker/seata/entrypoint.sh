#!/bin/bash
cp /seata-server/libs/jdbc/mysql-connector-java-8.0.27.jar /seata-server/libs/
exec /bin/bash /seata-server-entrypoint.sh
