#!/bin/bash

# ─────────────────────────────────────────────────────────────────────────────
# Rate Limit Smoke Test
# Proves per-tenant token bucket enforcement on FREE tier (60 req/min)
#
# Expected result:
#   Requests 1-60  → 200 OK
#   Request  61+   → 429 Too Many Requests
# ─────────────────────────────────────────────────────────────────────────────

BASE_URL="http://localhost:8080"
TENANT_ID="free-tier"
TOTAL_REQUESTS=65
ENDPOINT="$BASE_URL/api/resources"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Rate Limit Smoke Test"
echo "  Tenant  : $TENANT_ID"
echo "  Tier    : FREE (60 req/min)"
echo "  Sending : $TOTAL_REQUESTS requests"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

pass=0
fail=0
first_429=-1
first_429_body=""

for i in $(seq 1 $TOTAL_REQUESTS); do
    # Single curl call — capture headers + body + status code together
    tmp=$(curl -s -D - -H "X-Tenant-ID: $TENANT_ID" "$ENDPOINT" -w "\nHTTP_STATUS:%{http_code}")

    http_code=$(echo "$tmp" | grep "HTTP_STATUS:" | cut -d: -f2)
    remaining=$(echo "$tmp" | grep -i "x-ratelimit-remaining:" | awk '{print $2}' | tr -d '\r')
    body=$(echo "$tmp" | grep -v "^HTTP" | grep -v "^x-" | grep -v "^X-" | grep -v "^cache" \
         | grep -v "^content" | grep -v "^date" | grep -v "^expires" | grep -v "^pragma" \
         | grep -v "^vary" | grep -v "^connection" | grep -v "^transfer" | grep -v "^HTTP/" \
         | tail -1)

    if [ "$http_code" = "200" ]; then
        pass=$((pass + 1))
        printf "${GREEN}[%3d]${NC} 200 OK" "$i"
        [ -n "$remaining" ] && printf "  (remaining: %s)" "$remaining"
        printf "\n"
    elif [ "$http_code" = "429" ]; then
        fail=$((fail + 1))
        [ "$first_429" = "-1" ] && first_429=$i && first_429_body="$body"
        printf "${RED}[%3d]${NC} 429 TOO MANY REQUESTS\n" "$i"
    else
        printf "${YELLOW}[%3d]${NC} %s UNEXPECTED\n" "$i" "$http_code"
    fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Results"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
printf "  ${GREEN}200 OK${NC}          : %d requests\n" "$pass"
printf "  ${RED}429 Throttled${NC}   : %d requests\n" "$fail"

if [ "$first_429" != "-1" ]; then
    printf "  First 429 at    : request #%d\n" "$first_429"
    echo ""
    echo "  429 Response Body:"
    echo "  $first_429_body" | python3 -m json.tool 2>/dev/null || echo "  $first_429_body"
fi

echo ""

if [ "$pass" -ge 60 ] && [ "$fail" -gt 0 ] && [ "$first_429" -gt 59 ]; then
    echo -e "  ${GREEN}✅ PASS${NC} — Rate limiting is working correctly."
    echo "         First 60 requests allowed, request #$first_429 was throttled."
else
    echo -e "  ${RED}❌ FAIL${NC} — Unexpected behaviour."
    echo "         Expected 60 x 200 then 429. Got $pass x 200, first 429 at #$first_429."
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"