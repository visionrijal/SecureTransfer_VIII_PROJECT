#!/bin/bash

# Unique HTTP server port for this instance
export SERVER_PORT=8081

# Unique H2 database file for this instance
export SPRING_DATASOURCE_URL="jdbc:h2:file:./data/securetransfer2"

# Add ICE port range JVM arguments for NAT traversal
export MAVEN_OPTS="$MAVEN_OPTS -Dorg.ice4j.ice.MIN_PORT=5000 -Dorg.ice4j.ice.MAX_PORT=5100 -Dorg.ice4j.ice.harvest.PREFERRED_PORT=5000"

# Run the application with explicit main class
mvn javafx:run -Dspring.datasource.url=$SPRING_DATASOURCE_URL -Dserver.port=$SERVER_PORT -Djavafx.mainClass=com.securetransfer.JavaFXApplication