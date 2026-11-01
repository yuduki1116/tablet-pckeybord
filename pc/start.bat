@echo off
adb reverse tcp:5555 tcp:5555
adb shell am start -n com.yuzuki.futurekb/.MainActivity
python "%~dp0server.py"
pause
