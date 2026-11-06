# Unattended JFR recording. No key presses while playing.
# Run this FIRST, then start Minecraft, play ~2 minutes, close the game (optional).
# The recording is written to disk continuously (filename + dumponexit), so it
# survives the game exiting at any time.
# English prompts only: Windows PowerShell 5.1 misreads BOM-less UTF-8 scripts.

$ErrorActionPreference = 'Stop'

$kit    = 'D:\AtomChat-Main\docs\verification\2026-11-04-panel-sampling-kit'
$jfc    = Join-Path $kit 'atomchat-panel.jfc'
$outDir = 'D:\AtomChat-Main\build\panel-perf-20261104\samples'
$log    = 'D:\Myworld\.minecraft\versions\1.21.1-CCB\logs\latest.log'
$fallbackJcmd = 'C:\Program Files\Java\jdk-21.0.11\bin\jcmd.exe'
$fallbackJfr  = 'C:\Program Files\Java\jdk-21.0.11\bin\jfr.exe'

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$recording = Join-Path $outDir "panel-auto-$stamp.jfr"

Write-Host 'Waiting for Minecraft to start (up to 10 minutes). Start the game now.'
$game = $null
for ($i = 0; $i -lt 200; $i++) {
  $procs = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'KnotClient|net\.fabricmc|minecraft'
  })
  if ($procs.Count -gt 0) { $game = $procs[0]; break }
  Start-Sleep -Seconds 3
}
if (-not $game) { throw 'Minecraft did not appear within 10 minutes.' }

$gamePid = [int]$game.ProcessId
$gameBin = Split-Path $game.ExecutablePath -Parent
$jcmd = Join-Path $gameBin 'jcmd.exe'
$jfr  = Join-Path $gameBin 'jfr.exe'
if (-not (Test-Path $jcmd)) { $jcmd = $fallbackJcmd; $jfr = $fallbackJfr }

Write-Host "Found PID $gamePid. Recording started -> $recording"
Write-Host 'Play now: open the panel, switch pages ~15 times, open/close ~10 times, leave it open ~1 min.'
& $jcmd $gamePid JFR.start "name=atomchat" "settings=$jfc" "filename=$recording" "dumponexit=true"

# Stop when the game exits, or flush after 3 minutes so the data is never stranded.
$startedAt = Get-Date
while ($true) {
  $alive = Get-Process -Id $gamePid -ErrorAction SilentlyContinue
  if (-not $alive) {
    Write-Host 'Game exited; dumponexit wrote the recording.'
    break
  }
  if (((Get-Date) - $startedAt).TotalSeconds -ge 180) {
    try { & $jcmd $gamePid JFR.stop name=atomchat 2>&1 | Out-Host } catch { }
    Write-Host '3 minutes elapsed; recording flushed. You can keep playing or close the game.'
    break
  }
  Start-Sleep -Seconds 3
}

Start-Sleep -Seconds 4
if (-not (Test-Path $recording)) { throw "No JFR file produced at $recording" }
$size = (Get-Item -LiteralPath $recording).Length
Write-Host ''
Write-Host "JFR FILE : $recording  ($size bytes)"
Write-Host "LOG FILE : $log"
Write-Host ''
Write-Host 'Send both files to the agent. Event counts:'
& $jfr summary $recording 2>&1 | Select-Object -First 30
