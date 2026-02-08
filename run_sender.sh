#!/bin/bash

# Sender Configuration
echo "Starting Secure Transfer SENDER..."

# Set sender-specific ports and configurations
export SERVER_PORT=8080
export WEBSOCKET_PORT=8445
export P2P_LISTEN_PORT=8444
export P2P_ICE_PREFERRED_PORT=5000
export P2P_ICE_MIN_PORT=5000
export P2P_ICE_MAX_PORT=5100

# Set ICE system properties for sender
export JAVA_OPTS="-Dorg.ice4j.ice.harvest.PREFERRED_PORT=5000 -Dorg.ice4j.ice.MIN_PORT=5000 -Dorg.ice4j.ice.MAX_PORT=5100"

echo "Sender Configuration:"
echo "  HTTP Server Port: $SERVER_PORT"
echo "  WebSocket Port: $WEBSOCKET_PORT"
echo "  P2P Port: $P2P_LISTEN_PORT"
echo "  ICE Port Range: $P2P_ICE_MIN_PORT-$P2P_ICE_MAX_PORT"
echo ""

# Run the application
mvn javafx:run -Dserver.port=$SERVER_PORT -Dwebsocket.port=$WEBSOCKET_PORT -Dp2p.listen.port=$P2P_LISTEN_PORT -Dp2p.ice.preferred.port=$P2P_ICE_PREFERRED_PORT -Dp2p.ice.min.port=$P2P_ICE_MIN_PORT -Dp2p.ice.max.port=$P2P_ICE_MAX_PORT 