#!/bin/bash

# Quick Start Script for Sports Betting Settlement System
# This script sets up and starts the complete system

set -e

echo "🚀 Sports Betting Settlement System - Quick Start"
echo "=================================================="

# Step 1: Start Infrastructure
echo ""
echo "📦 Step 1: Starting Kafka and RocketMQ..."
docker-compose up -d

echo "⏳ Waiting 10 seconds for services to initialize..."
sleep 10

# Step 2: Check services
echo ""
echo "✅ Step 2: Verifying services..."
docker ps | grep -E "broker|rocketmq"

# Step 3: Build project
echo ""
echo "🔨 Step 3: Building project..."
./mvnw clean install -DskipTests

# Step 4: Start application
echo ""
echo "🎯 Step 4: Starting Spring Boot application..."
echo "The application will start on http://localhost:9999"
echo ""
echo "Available endpoints:"
echo "  - API: http://localhost:9999/api/events/publish"
echo "  - H2 Console: http://localhost:9999/h2-console"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

./mvnw spring-boot:run

