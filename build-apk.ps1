# 重新打包 APK。腾讯云/阿里云下不动时自动走 socks5://127.0.0.1:10808
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Zulu\zulu-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$gradle = "$env:TEMP\autoswipe-sdk\gradle-8.7\bin\gradle.bat"
if (-not (Test-Path $gradle)) {
    throw "未找到 Gradle，请先解压 gradle-8.7 到 $env:TEMP\autoswipe-sdk\gradle-8.7"
}

Set-Location $PSScriptRoot
Write-Host "Building assembleDebug..."
cmd /c "`"$gradle`" assembleDebug --no-daemon"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Mirror failed, retry via 127.0.0.1:10808"
    cmd /c "`"$gradle`" assembleDebug --no-daemon -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=10808"
    if ($LASTEXITCODE -ne 0) { throw "build failed" }
}

$apk = Get-ChildItem "$PSScriptRoot\app\build\outputs\apk\debug\*.apk" | Select-Object -First 1
Copy-Item $apk.FullName "$PSScriptRoot\autoswipe.apk" -Force
python -c "import shutil; from pathlib import Path; p=Path(r'$PSScriptRoot'); shutil.copy2(p/'autoswipe.apk', p/'自动上滑.apk')"
Write-Host "OK -> $PSScriptRoot\autoswipe.apk"
