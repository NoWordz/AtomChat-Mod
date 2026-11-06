# AtomChat panel sampling - attach JFR to an already running Minecraft client.
# The user starts and stops the game. This script never launches Minecraft.
# Verified on a test JVM: see jfr-probe-verification.txt (RESULT=PASS).

param(
  [Parameter(Mandatory=$true)][ValidateSet('list','start','stop','print')][string]$Action,
  [int]$GamePid = 0,
  [string]$Jdk = 'C:\Program Files\Java\jdk-21.0.11',
  [string]$Jfc = 'D:\AtomChat-Main\docs\verification\2026-11-04-panel-sampling-kit\atomchat-panel.jfc',
  [string]$Out = 'D:\AtomChat-Main\build\panel-perf-20261104\samples',
  [string]$Recording = ''
)

$jcmd = Join-Path $Jdk 'bin\jcmd.exe'
$jfr  = Join-Path $Jdk 'bin\jfr.exe'
if (-not (Test-Path $jcmd)) { throw "jcmd.exe not found: $jcmd" }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

switch ($Action) {
  'list' {
    & $jcmd -l
  }
  'start' {
    if ($GamePid -le 0) { throw 'Pass -GamePid. Use -Action list to find the client PID.' }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $file = Join-Path $Out "panel-$stamp.jfr"
    & $jcmd $GamePid JFR.start "name=atomchat" "settings=$Jfc"
    "Recording 'atomchat' started on PID $GamePid."
    "After the scenarios run:"
    "  .\capture.ps1 -Action stop -GamePid $GamePid -Recording '$file'"
  }
  'stop' {
    if ($GamePid -le 0 -or [string]::IsNullOrEmpty($Recording)) { throw 'Pass -GamePid and -Recording.' }
    & $jcmd $GamePid JFR.dump name=atomchat "filename=$Recording"
    & $jcmd $GamePid JFR.stop name=atomchat
    "Saved: $Recording"
    "Check with:"
    "  .\capture.ps1 -Action print -Recording '$Recording'"
  }
  'print' {
    if ([string]::IsNullOrEmpty($Recording)) { throw 'Pass -Recording.' }
    & $jfr print --events com.atomchat.UiFrame,com.atomchat.PanelSegments,com.atomchat.Navigation,com.atomchat.ScreenLifecycle $Recording
  }
}
