#!/usr/bin/env bash
# Boots a HEADLESS dev server with the mod, runs "/tuffsmp selftest" from the console, then stops the server.
# Usage:  bash tools/server_selftest.sh        (takes ~1-2 minutes)
# Output: run/selftest-server.log (full server log) and a PASS/FAIL summary on screen.
# Note: this writes run/eula.txt with eula=true (the Minecraft EULA is accepted for this local test server).
set -u
cd "$(dirname "$0")/.."
mkdir -p run
echo "eula=true" > run/eula.txt
cat > run/server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
generator-settings={"biome"\:"minecraft\:plains","layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:5},{"block"\:"minecraft\:grass_block","height"\:1}]}
spawn-protection=0
view-distance=4
simulation-distance=4
max-tick-time=-1
motd=Tuff SMP dev server
pause-when-empty-seconds=-1
PROPS
rm -rf run/world
LOG=run/selftest-server.log
FIFO=$(mktemp -u /tmp/mcin.XXXXXX); mkfifo "$FIFO"
( tail -f "$FIFO" | timeout 900 ./gradlew runServer --no-daemon --console=plain > "$LOG" 2>&1 ) &
for i in $(seq 1 120); do grep -q 'Done (' "$LOG" 2>/dev/null && break; sleep 3; done
if ! grep -q 'Done (' "$LOG"; then echo "SERVER DID NOT START - see $LOG"; pkill -f "tail -f $FIFO"; exit 2; fi
echo "forceload add -16 -16 32 32" > "$FIFO"   # entity lookups need ticking chunks; nobody is online in this test
sleep 6
echo "tuffsmp selftest" > "$FIFO"
for i in $(seq 1 60); do grep -q 'self-test: .* passed' "$LOG" && break; sleep 2; done
echo "kill @e[type=minecraft:item]" > "$FIFO"          # leftovers check: nothing should be lying around after the test
echo "kill @e[type=minecraft:experience_orb]" > "$FIFO"
sleep 3
echo "stop" > "$FIFO"
sleep 8
pkill -f "tail -f $FIFO" 2>/dev/null; rm -f "$FIFO"
echo "================ server log problems (ERROR / exceptions, excluding the self-test's own FAIL lines) ================"
grep -n "ERROR\|Exception\|Caused by" "$LOG" | grep -v "SELFTEST\|FAIL" || echo "(none)"
echo "================ self-test summary ================"
grep "\[SELFTEST\]" "$LOG" | sed 's/.*\[SELFTEST\] //' | grep "FAIL\|=== " 
grep -c "\[SELFTEST\] PASS" "$LOG" | sed 's/^/total PASS lines: /'

echo "================ leftovers after the test (expect: No entity was found) ================"
grep -E "No entity was found|Killed" "$LOG" | tail -3
