#!/bin/bash

echo "Testing network connectivity..."

# Test if port 8445 is accessible on the other device
echo "Testing connection to 192.168.1.78:8445..."
timeout 5 bash -c "</dev/tcp/192.168.1.78/8445" && echo "SUCCESS: Port 8445 is accessible" || echo "FAILED: Port 8445 is not accessible"

echo "Testing connection to 192.168.1.101:8445..."
timeout 5 bash -c "</dev/tcp/192.168.1.101/8445" && echo "SUCCESS: Port 8445 is accessible" || echo "FAILED: Port 8445 is not accessible"

echo "Testing public IP connectivity..."
echo "Testing connection to 27.34.65.131:8445..."
timeout 5 bash -c "</dev/tcp/27.34.65.131/8445" && echo "SUCCESS: Public IP port 8445 is accessible" || echo "FAILED: Public IP port 8445 is not accessible"

echo "Network test complete." 