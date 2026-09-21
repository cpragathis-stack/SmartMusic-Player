@echo off
title SmartBeats - Offline Music Player
echo ========================================================
echo             Starting SmartBeats Offline Player
echo ========================================================

echo Compiling Java source files...
if not exist "target\classes" mkdir target\classes
if not exist "target\classes\fxml" mkdir target\classes\fxml
if not exist "target\classes\css" mkdir target\classes\css

copy /Y src\main\resources\fxml\* target\classes\fxml\ >nul 2>&1
copy /Y src\main\resources\css\* target\classes\css\ >nul 2>&1

powershell -Command "$files = Get-ChildItem -Path 'src\main\java' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName; javac -encoding UTF-8 -d target\classes --module-path 'lib' --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql,java.desktop -cp 'lib\*' $files"

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo Launching SmartBeats...
java --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql,java.desktop -cp "target\classes;lib\*" com.musicplayer.Launcher

pause
