# TEMP selftest harness (Windows): same as tools/server_selftest.sh but driven from PowerShell,
# because git-bash + FIFOs hung on this machine. Boots the dev server, feeds it commands over stdin.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot | Split-Path   # tools/_selftest.ps1 -> project root
Set-Location $root
New-Item -ItemType Directory -Force run | Out-Null
Set-Content run\eula.txt "eula=true"
Set-Content run\server.properties @"
online-mode=false
level-type=minecraft\:flat
generator-settings={"biome"\:"minecraft\:plains","layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:5},{"block"\:"minecraft\:grass_block","height"\:1}]}
spawn-protection=0
view-distance=4
simulation-distance=4
max-tick-time=-1
motd=Tuff SMP dev server
pause-when-empty-seconds=-1
"@
if (Test-Path run\world) { Remove-Item run\world -Recurse -Force }
$log = "run\selftest-server.log"
Remove-Item $log -ErrorAction SilentlyContinue

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c gradlew.bat runServer --no-daemon --console=plain"
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$p = [System.Diagnostics.Process]::Start($psi)
$p.EnableRaisingEvents = $true
# stream stdout+stderr into the log file
$so = New-Object System.IO.StreamWriter((Join-Path $root $log), $false, [System.Text.Encoding]::UTF8)
$so.AutoFlush = $true
$outHandler = [System.Diagnostics.DataReceivedEventHandler]{ if ($_.Data) { $so.WriteLine($_.Data) } }
$errHandler = [System.Diagnostics.DataReceivedEventHandler]{ if ($_.Data) { $so.WriteLine($_.Data) } }
$p.add_OutputDataReceived($outHandler)
$p.add_ErrorDataReceived($errHandler)
$p.BeginOutputReadLine()
$p.BeginErrorReadLine()

function Wait-Log($pattern, $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ((Test-Path $log) -and (Select-String -Path $log -Pattern $pattern -Quiet)) { return $true }
        if ($p.HasExited) { return $false }
        Start-Sleep -Seconds 2
    }
    return $false
}

try {
    if (-not (Wait-Log 'Done \(' 420)) { throw "SERVER DID NOT START - see $log" }
    $p.StandardInput.WriteLine("forceload add -16 -16 32 32")
    Start-Sleep -Seconds 8
    $p.StandardInput.WriteLine("tuffsmp selftest")
    if (-not (Wait-Log 'self-test: .* passed' 240)) { throw "SELFTEST DID NOT FINISH - see $log" }
    $p.StandardInput.WriteLine("kill @e[type=minecraft:item]")
    $p.StandardInput.WriteLine("kill @e[type=minecraft:experience_orb]")
    Start-Sleep -Seconds 3
    $p.StandardInput.WriteLine("stop")
    if (-not $p.WaitForExit(90000)) { $p.Kill() }
} finally {
    if (-not $p.HasExited) { $p.Kill() }
    $so.Close()
}

Write-Output "================ self-test summary ================"
Select-String -Path $log -Pattern "\[SELFTEST\]" | ForEach-Object { $_.Line -replace '^.*\[SELFTEST\] ','' } | Where-Object { $_ -match 'FAIL|=== ' } | ForEach-Object { $_ }
Write-Output ("total PASS lines: " + (Select-String -Path $log -Pattern "\[SELFTEST\] PASS").Count)
Write-Output "================ server log problems ================"
$probs = Select-String -Path $log -Pattern "ERROR|Exception|Caused by" | Where-Object { $_.Line -notmatch 'SELFTEST|FAIL' }
if ($probs) { $probs | Select-Object -First 10 | ForEach-Object { $_.Line } } else { Write-Output "(none)" }
Write-Output "================ leftovers after the test ================"
Select-String -Path $log -Pattern "No entity was found|Killed" | Select-Object -Last 3 | ForEach-Object { $_.Line }
