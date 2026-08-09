@echo off
setlocal
title VRChat Legends - Boop Counter

REM ---------------------------------------------------------------------------
REM  Double click this to start counting boops.
REM
REM  The companion app on the headset announces itself every 3 seconds, so the
REM  IP below is only a head start. Setting it means the very first boop can
REM  reach your chatbox without waiting for an announce. If your headset ever
REM  changes address, either fix it here or just delete the --quest argument
REM  and let the announce find it.
REM ---------------------------------------------------------------------------

set QUEST_IP=192.168.1.165

cd /d "%~dp0"

where python >nul 2>&1
if errorlevel 1 (
    echo.
    echo  Python was not found on PATH.
    echo  Install it from https://www.python.org/downloads/ and tick
    echo  "Add python.exe to PATH" during setup, then run this again.
    echo.
    pause
    exit /b 1
)

echo.
echo  Starting the boop counter. Leave this window open.
echo  Make sure PC Link is switched on in the companion app.
echo  Press Ctrl+C to stop.
echo.

REM Anything you pass to run.bat is forwarded, e.g. run.bat --ascii --hold 0
python boop_counter.py --quest %QUEST_IP% %*

echo.
echo  Boop counter stopped.
pause
