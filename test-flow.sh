#!/bin/bash

# Test Script - Complete End-to-End Flow
# This script tests the entire bet settlement system

set -e

BASE_URL="http://localhost:9999"

echo "🧪 Testing Sports Betting Settlement System"
echo "==========================================="

echo ""
echo "📊 Test Case 1: Event 100 - Winner 201"
echo "---------------------------------------"
echo "Expected: Bets 1 and 3 WIN, Bet 2 LOSES"

curl -X POST "${BASE_URL}/api/events/publish" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": 100,
    "eventName": "Champions League Final - Team A vs Team B",
    "eventWinnerId": 201
  }'

echo ""
echo "✅ Event published. Waiting 5 seconds for processing..."
sleep 5

echo ""
echo "📊 Test Case 2: Event 101 - Winner 302"
echo "---------------------------------------"
echo "Expected: Bet 5 WINS, Bet 4 LOSES"

curl -X POST "${BASE_URL}/api/events/publish" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": 101,
    "eventName": "Premier League - Team C vs Team D",
    "eventWinnerId": 302
  }'

echo ""
echo "✅ Event published. Waiting 5 seconds for processing..."
sleep 5

echo ""
echo "🎉 Tests completed!"
echo ""
echo "📋 Next Steps:"
echo "1. Check application logs for settlement details"
echo "2. Access H2 Console: http://localhost:9999/h2-console"
echo "3. Run these SQL queries:"
echo ""
echo "   -- Check settled bets"
echo "   SELECT * FROM bet WHERE event_id IN (100, 101);"
echo ""
echo "   -- Check settlements"
echo "   SELECT * FROM bet_settlement;"
echo ""

