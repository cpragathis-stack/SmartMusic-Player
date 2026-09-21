$Host.UI.RawUI.WindowTitle = "SmartBeats - Offline Music Player"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "            Starting SmartBeats Offline Player           " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

if (-not (Test-Path "target\classes\fxml")) { New-Item -ItemType Directory -Force -Path "target\classes\fxml" | Out-Null }
if (-not (Test-Path "target\classes\css")) { New-Item -ItemType Directory -Force -Path "target\classes\css" | Out-Null }

Copy-Item -Path "src\main\resources\fxml\*" -Destination "target\classes\fxml\" -Force -ErrorAction SilentlyContinue
Copy-Item -Path "src\main\resources\css\*" -Destination "target\classes\css\" -Force -ErrorAction SilentlyContinue

Write-Host "Compiling Java source files..." -ForegroundColor Yellow
$files = Get-ChildItem -Path 'src\main\java' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
javac -encoding UTF-8 -d target\classes --module-path 'lib' --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql,java.desktop -cp 'lib\*' $files

if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation failed with error code $LASTEXITCODE" -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit $LASTEXITCODE
}

Write-Host "Launching SmartBeats..." -ForegroundColor Green
java --module-path 'lib' --add-modules javafx.controls,javafx.fxml,javafx.media,java.sql,java.desktop -cp 'target\classes;lib\*' com.musicplayer.Launcher
