@echo off
cd /d "%~dp0"
echo ============================================================
echo SmartBeats diagnostic run. Click the dots you see, then copy
echo the "CLICK at (x,y)" lines from this window back to the chat.
echo Close this window to quit the app.
echo ============================================================
java --module-path "target\classes;lib;javafx-sdk\javafx-sdk-21.0.2\lib" --add-modules ALL-MODULE-PATH -m com.musicplayer/com.musicplayer.Main
pause