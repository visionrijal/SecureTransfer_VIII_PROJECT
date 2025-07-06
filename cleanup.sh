#!/bin/bash

# Stop any running Java processes
pkill -f "java.*SecureTransferApplication"

# Remove database files
rm -f ./data/securetransfer.mv.db
rm -f ./data/securetransfer.trace.db

# Create data directory if it doesn't exist
mkdir -p ./data

echo "Cleanup completed. You can now restart the application." 