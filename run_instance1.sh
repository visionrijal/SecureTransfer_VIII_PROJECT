#!/usr/bin/env bash
DB_URL="jdbc:h2:file:./data/securetransfer1;DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE;DB_CLOSE_DELAY=-1;FILE_LOCK=NO"
SERVER_PORT=8080
WEBSOCKET_PORT=8443
P2P_PORT=8444
export SPRING_DATASOURCE_URL="jdbc:h2:file:./data/securetransfer1"
# Add ICE port range JVM arguments for NAT traversal
export MAVEN_OPTS="$MAVEN_OPTS -Dorg.ice4j.ice.MIN_PORT=5000 -Dorg.ice4j.ice.MAX_PORT=5100 -Dorg.ice4j.ice.harvest.PREFERRED_PORT=5000"

# Run the application with explicit main class
export SERVER_PORT=8080
mvn javafx:run -Dspring.datasource.url=$SPRING_DATASOURCE_URL -Dserver.port=$SERVER_PORT -Djavafx.mainClass=com.securetransfer.JavaFXApplication