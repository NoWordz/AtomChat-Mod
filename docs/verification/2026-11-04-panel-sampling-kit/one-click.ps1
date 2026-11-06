# One-click AtomChat panel sampling. Safe to re-run.
# English prompts only: Windows PowerShell 5.1 misreads BOM-less UTF-8 scripts.
# The game is usually launched by PCL with its own Java (and -XX:+PerfDisableSharedMem),
# so `jcmd -l` cannot see it. We find the process via WMI and use the JFR tools from
# the game's own JDK directory.

param(
  [int]$GamePidParam = 0,
  [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'

$kit    = 'D:\AtomChat-Main\docs\verification\2026-11-04-panel-sampling-kit'
$newJar = 'D:\AtomChat-Main\build\panel-perf-20261104\artifacts\atomchat-Fabric-1.21.1-v0.5.0.jar'
$expect = 'd04e913270c124b804672bfddd21e44503d22585a84c5d0a3f3e97528a995114'
$mods   = 'D:\Myworld\.minecraft\versions\1.21.1-CCB\mods'
$target = Join-Path $mods 'atomchat-Fabric-1.21.1-v0.5.0.jar'
$config = 'D:\Myworld\.minecraft\versions\1.21.1-CCB\config\atomchat\atomchat-client.json'
$jdk    = 'C:\Program Files\Java\jdk-21.0.11'
$jcmd   = Join-Path $jdk 'bin\jcmd.exe'
$jfr    = Join-Path $jdk 'bin\jfr.exe'
$jfc    = Join-Path $kit 'atomchat-panel.jfc'
$outDir = 'D:\AtomChat-Main\build\panel-perf-20261104\samples'
$log    = 'D:\Myworld\.minecraft\versions\1.21.1-CCB\logs\latest.log'

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

if (-not $SkipInstall) {
  Write-Host 'STEP 1/3 - Install the diagnostic build (skip if already installed).'
  Write-Host '           Make sure Minecraft is CLOSED before continuing.'
  if (-not (Test-Path $newJar)) { throw "Missing new mod jar: $newJar" }
  $current = if (Test-Path $target) {
    (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower()
  } else { '' }
  if ($current -eq $expect) {
    Write-Host '  Already installed, skipping.'
  } else {
    if (-not (Test-Path $target)) { throw "Missing old mod jar: $target" }
    $backupJar = "$target.bak-$stamp"
    Copy-Item -LiteralPath $target -Destination $backupJar -Force
    Copy-Item -LiteralPath $newJar -Destination $target -Force
    $actual = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $expect) { throw "SHA-256 mismatch: $actual -- restore from $backupJar" }
    Write-Host "  OK. Backup: $backupJar"
  }

  Write-Host 'STEP 2/3 - Enable debug in the client config.'
  $text = [IO.File]::ReadAllText($config)
  if ($text -notmatch '"debug"') { throw 'No "debug" key in config; set "debug": true manually.' }
  if ($text -match '"debug"\s*:\s*true') {
    Write-Host '  Already enabled, skipping.'
  } else {
    $backupConfig = "$config.bak-$stamp"
    Copy-Item -LiteralPath $config -Destination $backupConfig -Force
    $text = $text -replace '"debug"\s*:\s*false', '"debug": true'
    [IO.File]::WriteAllText($config, $text, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  OK. Backup: $backupConfig"
  }
}

Read-Host 'START Minecraft now (Iris shaders OFF), load a world. Then press Enter; the script will wait for the process' | Out-Null

Write-Host 'STEP 3/3 - Waiting for the Minecraft process (up to 3 minutes).'
$game = $null
for ($i = 0; $i -lt 60; $i++) {
  $procs = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java(w)?\.exe$' -and $_.CommandLine -match 'KnotClient|net\.fabricmc|minecraft'
  })
  if ($procs.Count -gt 0) { $game = $procs[0]; break }
  Start-Sleep -Seconds 3
}
if (-not $game -and $GamePidParam -gt 0) {
  $game = Get-CimInstance Win32_Process -Filter "ProcessId = $GamePidParam"
}
if (-not $game) {
  throw 'No Minecraft Java process found. Start the game, or rerun with -GamePidParam <PID>.'
}
$gamePid = [int]$game.ProcessId
$gameBin = Split-Path $game.ExecutablePath -Parent
$gameJcmd = Join-Path $gameBin 'jcmd.exe'
$gameJfr  = Join-Path $gameBin 'jfr.exe'
if (-not (Test-Path $gameJcmd)) { $gameJcmd = $jcmd; $gameJfr = $jfr }
Write-Host "  Found PID $gamePid, using JDK: $gameBin"

$recording = Join-Path $outDir "panel-$stamp.jfr"
$check = (& $gameJcmd $gamePid JFR.check 2>&1 | Out-String)
if ($check -match 'name=atomchat') {
  Write-Host '  An atomchat recording is already running; reusing it.'
} else {
  # filename + dumponexit: the data survives even if the game exits unexpectedly.
  & $gameJcmd $gamePid JFR.start "name=atomchat" "settings=$jfc" "filename=$recording" "dumponexit=true"
}
Write-Host "  Recording on PID $gamePid -> $recording"

Read-Host 'In game: open the panel, switch pages ~15 times, open/close it ~10 times, leave it open ~1 min. Then press Enter' | Out-Null

# JFR.stop flushes the file opened at start. If the game already exited, dumponexit
# has written it for us, so a failed attach here is not a data loss.
try {
  & $gameJcmd $gamePid JFR.stop name=atomchat 2>&1 | Out-Host
} catch {
  Write-Host '  Game already exited; using the dumponexit file.'
}
if (-not (Test-Path $recording)) { throw "No JFR file produced at $recording" }
Write-Host ''
Write-Host 'DONE. Send these two files to the agent:'
Write-Host "  $recording"
Write-Host "  $log"
Write-Host ''
Write-Host 'Quick check (first PanelSegments events):'
& $gameJfr print --events com.atomchat.PanelSegments $recording | Select-Object -First 12

Write-Host ''
Write-Host 'To roll back: copy the .bak jar over the mod jar and the .bak config back.'
