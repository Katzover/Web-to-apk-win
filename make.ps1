#Requires -Version 5.1
<#
.SYNOPSIS
    Windows equivalent of make.sh for website-to-apk
.DESCRIPTION
    Build an Android APK from a website URL without Android Studio.
    Equivalent to the original make.sh but works natively on Windows.
.EXAMPLE
    .\make.ps1 keygen
    .\make.ps1 build
    .\make.ps1 build confs\youtube\webapk.conf
    .\make.ps1 test
    .\make.ps1 clean
#>

param(
    [Parameter(Position=0)]
    [string]$Command = "",

    [Parameter(Position=1)]
    [string]$Arg1 = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Colors ────────────────────────────────────────────────────────────────────
function log   { Write-Host "[+] $args" -ForegroundColor Green }
function info  { Write-Host "[*] $args" -ForegroundColor Cyan }
function warn  { Write-Host "[!] $args" -ForegroundColor Yellow }
function err   {
    Write-Host "[!] $args" -ForegroundColor Red
    exit 1
}

# ── Globals ───────────────────────────────────────────────────────────────────
$SCRIPT_DIR   = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ORIGINAL_PWD = $PWD.Path
Set-Location $SCRIPT_DIR

$env:ANDROID_HOME      = Join-Path $SCRIPT_DIR "cmdline-tools"
$env:GRADLE_USER_HOME  = Join-Path $SCRIPT_DIR ".gradle-cache"
$INFO = "CN=Developer, OU=Organization, O=Company, L=City, S=State, C=US"

# Detect current appname from build.gradle
function Get-AppName {
    $buildGradle = Join-Path $SCRIPT_DIR "app\build.gradle"
    if (-not (Test-Path $buildGradle)) { err "app\build.gradle not found" }
    $match = Select-String -Path $buildGradle -Pattern 'applicationId "com\.([^.]+)\.' | Select-Object -First 1
    if ($match) { return $match.Matches[0].Groups[1].Value }
    err "Could not detect appname from app\build.gradle"
}

$script:appname    = Get-AppName
$script:CONFIG_DIR  = ""  # Set by Apply-Config, used by Set-Icon / Set-Userscripts

# ── Run a command, exit on failure ────────────────────────────────────────────
function Invoke-Try {
    param([string]$Desc, [scriptblock]$Block)
    try {
        & $Block
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {
            err "Failed ($LASTEXITCODE): $Desc"
        }
    } catch {
        err "Failed: $Desc`n$_"
    }
}

# ── Download a file with progress ─────────────────────────────────────────────
function Download-File {
    param([string]$Url, [string]$Dest)
    info "Downloading: $Url"
    $ProgressPreference = 'SilentlyContinue'
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Dest -UseBasicParsing
    } catch {
        err "Download failed: $Url`n$_"
    }
    $ProgressPreference = 'Continue'
}

# ── set_var: update a Java variable in MainActivity.java ─────────────────────
function Set-Var {
    param([string]$VarName, [string]$Value)

    $javaFile = Join-Path $SCRIPT_DIR "app\src\main\java\com\$($script:appname)\webtoapk\MainActivity.java"
    if (-not (Test-Path $javaFile)) { err "MainActivity.java not found" }

    $content = Get-Content $javaFile -Raw

    if ($content -notmatch [regex]::Escape($VarName) + '\s*=\s*[^;]+;') {
        err "Variable '$VarName' not found in MainActivity.java"
    }

    # Strip surrounding quotes
    $Value = $Value.Trim('"').Trim("'")

    # Quote non-boolean, non-integer values
    if ($Value -notmatch '^(true|false)$' -and $Value -notmatch '^\d+$') {
        $newVal = "`"$Value`""
    } else {
        $newVal = $Value
    }

    $pattern = "($([regex]::Escape($VarName))\s*=\s*)[^;]+;"
    $replacement = "`${1}$newVal;"

    # Replace only first occurrence (matching awk behavior in original make.sh)
    $newContent = [regex]::Replace($content, $pattern, $replacement, 1, 0)

    if ($newContent -ne $content) {
        Set-Content -Path $javaFile -Value $newContent -NoNewline
        log "Updated $VarName to $newVal"

        # Handle permission side-effects
        switch ($VarName) {
            "geolocationEnabled" { Update-Permission "android.permission.ACCESS_FINE_LOCATION" $Value }
            "cameraEnabled"      { Update-Permission "android.permission.CAMERA" $Value }
            "microphoneEnabled"  {
                Update-Permission "android.permission.RECORD_AUDIO" $Value
                Update-Permission "android.permission.MODIFY_AUDIO_SETTINGS" $Value
            }
        }
    }
}

# ── Update a permission in AndroidManifest.xml ────────────────────────────────
function Update-Permission {
    param([string]$PermissionName, [string]$Enabled)

    $manifestFile = Join-Path $SCRIPT_DIR "app\src\main\AndroidManifest.xml"
    $content = Get-Content $manifestFile -Raw

    $tag = "    <uses-permission android:name=`"$PermissionName`" />`n"

    if ($Enabled -eq "true") {
        if ($content -notmatch [regex]::Escape($PermissionName)) {
            $content = $content -replace '(<manifest [^>]+>)', "`$1`n$tag"
            Set-Content -Path $manifestFile -Value $content -NoNewline
            log "Added permission: $PermissionName"
        }
    } else {
        if ($content -match [regex]::Escape($PermissionName)) {
            $content = $content -replace ".*$([regex]::Escape($PermissionName)).*\r?\n", ""
            Set-Content -Path $manifestFile -Value $content -NoNewline
            log "Removed permission: $PermissionName"
        }
    }
}

# ── Merge user config with default.conf ───────────────────────────────────────
function Merge-Config {
    param([string]$UserConf)

    $defaultConf = Join-Path $SCRIPT_DIR "app\default.conf"
    $merged = [System.IO.Path]::GetTempFileName()

    $defaultLines = Get-Content $defaultConf | Where-Object { $_ -notmatch '^\s*(#|$)' }
    $userLines    = Get-Content $UserConf

    $extras = @()
    foreach ($line in $defaultLines) {
        $key = ($line -split '=')[0].Trim()
        if ($key -and -not ($userLines | Where-Object { $_ -match "^\s*$([regex]::Escape($key))\s*=" })) {
            $extras += $line
        }
    }

    ($extras + $userLines) | Set-Content $merged
    return $merged
}

# ── Apply a .conf file ────────────────────────────────────────────────────────
function Apply-Config {
    param([string]$ConfigFile = "webapk.conf")

    if (-not (Test-Path $ConfigFile) -and (Test-Path (Join-Path $ORIGINAL_PWD $ConfigFile))) {
        $ConfigFile = Join-Path $ORIGINAL_PWD $ConfigFile
    }
    if (-not (Test-Path $ConfigFile)) { err "Config file not found: $ConfigFile" }

    $script:CONFIG_DIR = Split-Path -Parent (Resolve-Path $ConfigFile)
    info "Using config: $ConfigFile"

    $merged = Merge-Config $ConfigFile

    $lines = Get-Content $merged
    foreach ($line in $lines) {
        # Skip comments and blank lines
        if ($line -match '^\s*(#|$)') { continue }
        # Strip inline comments
        $line = $line -replace '\s+#.*$', ''
        if ($line -notmatch '=') { continue }

        $key   = ($line -split '=', 2)[0].Trim() -replace '[^a-zA-Z0-9_]', ''
        $value = ($line -split '=', 2)[1].Trim()

        switch ($key) {
            "id"          { Invoke-ChId $value }
            "name"        { Invoke-Rename $value }
            "deeplink"    { Set-DeepLink $value }
            "trustUserCA" { Set-NetworkSecurityConfig $value }
            "icon"        { Set-Icon $value }
            "scripts"     { Set-Userscripts ($value -split '\s+' | Where-Object { $_ }) }
            default       { Set-Var $key $value }
        }
    }

    Remove-Item $merged -ErrorAction SilentlyContinue
}

# ── Build APK ─────────────────────────────────────────────────────────────────
function Invoke-Apk {
    $keystorePath = Join-Path $SCRIPT_DIR "app\my-release-key.jks"
    if (-not (Test-Path $keystorePath)) {
        err "Keystore not found. Run '.\make.ps1 keygen' first"
    }

    $outApk = "app\build\outputs\apk\release\app-release.apk"
    Remove-Item $outApk -ErrorAction SilentlyContinue

    info "Building APK..."

    $gradlew = Join-Path $SCRIPT_DIR "gradlew.bat"
    if (-not (Test-Path $gradlew)) { $gradlew = "gradlew.bat" }

    Invoke-Try "gradlew assembleRelease" {
        & $gradlew assembleRelease --no-daemon --quiet
    }

    if (Test-Path $outApk) {
        log "APK successfully built and signed"
        $dest = "$($script:appname).apk"
        Copy-Item $outApk $dest -Force
        $size = [math]::Round((Get-Item $outApk).Length / 1MB, 2)

        $stringsXml = "app\src\main\res\values\strings.xml"
        $appDisplayName = ""
        if (Test-Path $stringsXml) {
            $m = [regex]::Match((Get-Content $stringsXml -Raw), 'app_name">([^<]+)<')
            if ($m.Success) { $appDisplayName = $m.Groups[1].Value }
        }

        $javaFile = "app\src\main\java\com\$($script:appname)\webtoapk\MainActivity.java"
        $mainURL = ""
        if (Test-Path $javaFile) {
            $m = [regex]::Match((Get-Content $javaFile -Raw), 'String mainURL\s*=\s*"([^"]+)"')
            if ($m.Success) { $mainURL = $m.Groups[1].Value }
        }

        Write-Host "----------------" -ForegroundColor White
        Write-Host "Final APK: " -NoNewline; Write-Host $dest -ForegroundColor Green
        Write-Host "Size:      " -NoNewline; Write-Host "${size} MB" -ForegroundColor Cyan
        Write-Host "Package:   " -NoNewline; Write-Host "com.$($script:appname).webtoapk" -ForegroundColor Cyan
        Write-Host "App name:  " -NoNewline; Write-Host $appDisplayName -ForegroundColor Cyan
        Write-Host "URL:       " -NoNewline; Write-Host $mainURL -ForegroundColor Cyan
        Write-Host "----------------" -ForegroundColor White
    } else {
        err "Build failed"
    }
}

# ── Test on device ────────────────────────────────────────────────────────────
function Invoke-Test {
    info "Detected app name: $($script:appname)"
    Invoke-Try "adb install" { adb install "app\build\outputs\apk\release\app-release.apk" }
    Invoke-Try "adb logcat -c" { adb logcat -c }
    Invoke-Try "adb shell am start" { adb shell am start -n "com.$($script:appname).webtoapk/.MainActivity" }
    Write-Host "=========================="
    adb logcat | Select-String "WebToApk: " | ForEach-Object {
        $_.Line -replace '.*WebToApk: ', ''
    }
}

# ── Generate keystore ─────────────────────────────────────────────────────────
function Invoke-Keygen {
    $keystore = Join-Path $SCRIPT_DIR "app\my-release-key.jks"
    if (Test-Path $keystore) {
        warn "Keystore already exists"
        $answer = Read-Host "Do you want to replace it? (y/N)"
        if ($answer -notmatch '^[Yy]$') { info "Cancelled"; return }
        Remove-Item $keystore
    }

    info "Generating keystore..."
    Invoke-Try "keytool" {
        keytool -genkey -v `
            -keystore $keystore `
            -keyalg RSA -keysize 2048 -validity 10000 `
            -alias my `
            -storepass 123456 -keypass 123456 `
            -dname $INFO
    }
    log "Keystore generated successfully"
}

# ── Clean build files ─────────────────────────────────────────────────────────
function Invoke-Clean {
    info "Cleaning build files..."
    Remove-Item "app\build" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item ".gradle"   -Recurse -Force -ErrorAction SilentlyContinue
    Apply-Config "app\default.conf"
    log "Clean completed"
}

# ── Change application ID ─────────────────────────────────────────────────────
function Invoke-ChId {
    param([string]$NewId)

    if (-not $NewId) { err "Please provide an application ID" }
    if ($NewId -notmatch '^[a-zA-Z][a-zA-Z0-9_]*$') {
        err "Invalid application ID. Use only letters, numbers, underscores; start with a letter"
    }

    # Update package name in all .gradle/.java/.xml files
    Get-ChildItem -Recurse -Include "*.gradle","*.java","*.xml" | ForEach-Object {
        $c = Get-Content $_.FullName -Raw
        $updated = $c -replace 'com\.([a-zA-Z0-9_]*)\.webtoapk', "com.$NewId.webtoapk"
        if ($updated -ne $c) {
            Set-Content -Path $_.FullName -Value $updated -NoNewline
        }
    }

    if ($NewId -ne $script:appname) {
        info "Old name: com.$($script:appname).webtoapk"
        info "Renaming to: com.$NewId.webtoapk"

        $oldDir = "app\src\main\java\com\$($script:appname)"
        $newDir = "app\src\main\java\com\$NewId"

        if (Test-Path $oldDir) {
            Rename-Item $oldDir $newDir
        }
        $script:appname = $NewId
        log "Application ID changed successfully"
    }
}

# ── Rename display name ───────────────────────────────────────────────────────
function Invoke-Rename {
    param([string]$NewName)

    if (-not $NewName) { err "Please provide a display name" }

    Get-ChildItem -Recurse -Filter "strings.xml" | ForEach-Object {
        $c = Get-Content $_.FullName -Raw
        $updated = $c -replace '<string name="app_name">[^<]*</string>', "<string name=`"app_name`">$NewName</string>"
        if ($updated -ne $c) {
            Set-Content -Path $_.FullName -Value $updated -NoNewline
            log "Display name changed to: $NewName ($($_.DirectoryName))"
        }
    }
}

# ── Set deep link hosts in AndroidManifest.xml ────────────────────────────────
function Set-DeepLink {
    param([string]$HostsInput)

    $manifestFile = Join-Path $SCRIPT_DIR "app\src\main\AndroidManifest.xml"
    $content = Get-Content $manifestFile -Raw

    # Remove any existing VIEW intent-filter
    $content = [regex]::Replace(
        $content,
        '(?s)\s*<intent-filter>(?:(?!<intent-filter>).)*android\.intent\.action\.VIEW(?:(?!</intent-filter>).)*</intent-filter>',
        ''
    )

    if ($HostsInput) {
        $hosts = $HostsInput -split '\s+' | Where-Object { $_ }
        $hostTags = ($hosts | ForEach-Object { "                <data android:host=`"$_`" />" }) -join "`n"

        $deepLinkBlock = @"

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:scheme="https" />
$hostTags
            </intent-filter>
"@
        # Insert after the first </intent-filter> only (use [regex]::Replace with count=1)
        $content = [regex]::Replace($content, '(</intent-filter>)', "`$1$deepLinkBlock", 1)
        log "Setting deeplinks for: $HostsInput"
    } else {
        log "Removing deeplink"
    }

    Set-Content -Path $manifestFile -Value $content -NoNewline
}

# ── Network security config ───────────────────────────────────────────────────
function Set-NetworkSecurityConfig {
    param([string]$Enabled)

    $manifestFile = Join-Path $SCRIPT_DIR "app\src\main\AndroidManifest.xml"
    $content = Get-Content $manifestFile -Raw
    $attr = ' android:networkSecurityConfig="@xml/network_security_config"'

    if ($Enabled -eq "true") {
        if ($content -notmatch 'networkSecurityConfig') {
            $content = $content -replace '(<application)', "`$1$attr"
            Set-Content -Path $manifestFile -Value $content -NoNewline
            log "Enabling user CA support"
        }
    } else {
        if ($content -match 'networkSecurityConfig') {
            $content = $content -replace [regex]::Escape($attr), ''
            Set-Content -Path $manifestFile -Value $content -NoNewline
            log "Disabling user CA support"
        }
    }
}

# ── Set icon ──────────────────────────────────────────────────────────────────
function Set-Icon {
    param([string]$IconPath)

    $defaultIcon = Join-Path $SCRIPT_DIR "app\example.png"
    $destFile    = Join-Path $SCRIPT_DIR "app\src\main\res\mipmap\ic_launcher.png"

    if (-not $IconPath) { $IconPath = $defaultIcon }

    # Make relative paths absolute using CONFIG_DIR
    if ($script:CONFIG_DIR -and -not [System.IO.Path]::IsPathRooted($IconPath)) {
        $IconPath = Join-Path $script:CONFIG_DIR $IconPath
    }

    if (-not (Test-Path $IconPath)) { err "Icon file not found: $IconPath" }

    # Check PNG signature
    $bytes = [System.IO.File]::ReadAllBytes($IconPath)
    $pngSig = [byte[]](0x89, 0x50, 0x4E, 0x47)
    for ($i = 0; $i -lt 4; $i++) {
        if ($bytes[$i] -ne $pngSig[$i]) { err "Icon must be PNG format" }
    }

    New-Item -ItemType Directory -Force -Path (Split-Path $destFile) | Out-Null

    if ((Test-Path $destFile) -and
        (Get-FileHash $IconPath).Hash -eq (Get-FileHash $destFile).Hash) {
        return
    }

    Copy-Item $IconPath $destFile -Force
    log "Icon updated successfully"
}

# ── Set userscripts ───────────────────────────────────────────────────────────
function Set-Userscripts {
    param([string[]]$Patterns)

    $scriptsDir = Join-Path $SCRIPT_DIR "app\src\main\assets\userscripts"
    New-Item -ItemType Directory -Force -Path $scriptsDir | Out-Null

    if (-not $Patterns -or $Patterns.Count -eq 0) {
        if ((Get-ChildItem $scriptsDir -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0) {
            Get-ChildItem $scriptsDir | Remove-Item -Force
            log "Userscripts directory cleared"
        }
        return
    }

    $existing = @(Get-ChildItem $scriptsDir -File | Select-Object -ExpandProperty Name)
    $sourceFiles = @()

    foreach ($pattern in $Patterns) {
        if (-not [System.IO.Path]::IsPathRooted($pattern) -and $script:CONFIG_DIR) {
            $pattern = Join-Path $script:CONFIG_DIR $pattern
        }
        $matches_ = Resolve-Path $pattern -ErrorAction SilentlyContinue
        foreach ($m in $matches_) {
            if (Test-Path $m -PathType Leaf) { $sourceFiles += $m.Path }
        }
    }

    $added = @(); $updated = @(); $removed = @()
    $current = @()

    foreach ($src in $sourceFiles) {
        $base = Split-Path -Leaf $src
        $dest = Join-Path $scriptsDir $base
        $current += $base
        if (-not (Test-Path $dest)) {
            Copy-Item $src $dest; $added += $base
        } elseif ((Get-FileHash $src).Hash -ne (Get-FileHash $dest).Hash) {
            Copy-Item $src $dest -Force; $updated += $base
        }
    }

    foreach ($s in $existing) {
        if ($current -notcontains $s) {
            Remove-Item (Join-Path $scriptsDir $s) -Force; $removed += $s
        }
    }

    foreach ($s in $removed) { log "Removed userscript: $s" }
    foreach ($s in $added)   { log "Added userscript: $s" }
    foreach ($s in $updated) { log "Updated userscript: $s" }
}

# ── Download Android SDK command-line tools ───────────────────────────────────
function Get-Tools {
    info "Downloading Android Command Line Tools (Windows)..."

    $url     = "https://dl.google.com/android/repository/commandlinetools-win-12266719_latest.zip"
    $tmpZip  = Join-Path $env:TEMP "cmdline-tools.zip"
    $tmpDir  = Join-Path $env:TEMP "cmdline-tools-extract"

    Download-File $url $tmpZip

    info "Extracting tools..."
    Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
    Expand-Archive -Path $tmpZip -DestinationPath $tmpDir -Force

    $latestDest = "$($env:ANDROID_HOME)\cmdline-tools\latest"
    New-Item -ItemType Directory -Force -Path $latestDest | Out-Null
    Get-ChildItem "$tmpDir\cmdline-tools" | Copy-Item -Destination $latestDest -Recurse -Force

    Remove-Item $tmpZip, $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

    info "Accepting licenses..."
    $sdkmanager = "$latestDest\bin\sdkmanager.bat"
    "y" * 20 | & $sdkmanager --sdk_root=$env:ANDROID_HOME --licenses 2>&1 | Out-Null

    info "Installing SDK components..."
    Invoke-Try "sdkmanager install" {
        & $sdkmanager --sdk_root=$env:ANDROID_HOME `
            "platform-tools" `
            "platforms;android-35" `
            "build-tools;35.0.0"
    }

    log "Android SDK installed successfully!"
}

# ── Download OpenJDK 17 locally ───────────────────────────────────────────────
function Get-Java {
    $installDir  = Join-Path $SCRIPT_DIR "jvm"
    $jdkVersion  = "17.0.2"
    $jdkHash     = "4E4B499A7ED6D19B6AA4A86D60ADB2B8DFE2A4B4A96ED6B72D8B16C24E74A1E8"
    $jdkUrl      = "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip"
    $jdkDir      = Join-Path $installDir "jdk-$jdkVersion"

    if (Test-Path $jdkDir) {
        info "OpenJDK $jdkVersion already downloaded"
        $env:JAVA_HOME = $jdkDir
        $env:PATH = "$jdkDir\bin;$($env:PATH)"
        return
    }

    $tmpZip = Join-Path $env:TEMP "openjdk.zip"
    info "Downloading OpenJDK $jdkVersion..."
    Download-File $jdkUrl $tmpZip

    info "Unpacking to $installDir..."
    New-Item -ItemType Directory -Force -Path $installDir | Out-Null
    Expand-Archive -Path $tmpZip -DestinationPath $installDir -Force
    Remove-Item $tmpZip -ErrorAction SilentlyContinue

    $env:JAVA_HOME = $jdkDir
    $env:PATH = "$jdkDir\bin;$($env:PATH)"
    log "OpenJDK $jdkVersion downloaded successfully!"
}

# ── Reinstall Gradle ──────────────────────────────────────────────────────────
function Invoke-Regradle {
    info "Reinstalling Gradle..."
    Remove-Item "gradle","gradlew","gradlew.bat",".gradle",".gradle-cache" -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path "gradle\wrapper" | Out-Null

    @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-all.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@ | Set-Content "gradle\wrapper\gradle-wrapper.properties"

    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest "https://raw.githubusercontent.com/gradle/gradle/v8.6.0/gradle/wrapper/gradle-wrapper.jar" `
        -OutFile "gradle\wrapper\gradle-wrapper.jar" -UseBasicParsing
    Invoke-WebRequest "https://raw.githubusercontent.com/gradle/gradle/v8.6.0/gradlew.bat" `
        -OutFile "gradlew.bat" -UseBasicParsing
    $ProgressPreference = 'Continue'

    log "Gradle reinstalled successfully"
}

# ── Find Java 17 ──────────────────────────────────────────────────────────────
function Find-Java17 {
    # 1. Check local jvm/ folder
    $localJvm = Join-Path $SCRIPT_DIR "jvm\jdk-17.0.2"
    if (Test-Path "$localJvm\bin\java.exe") {
        $env:JAVA_HOME = $localJvm
        $env:PATH = "$localJvm\bin;$($env:PATH)"
        info "Using local Java installation"
        return $true
    }

    # 2. Check JAVA_HOME env var
    if ($env:JAVA_HOME -and (Test-Path "$($env:JAVA_HOME)\bin\java.exe")) {
        try {
            $ver = (& "$env:JAVA_HOME\bin\java.exe" -version 2>&1)[0] -replace '.*"(\d+).*".*','$1'
            if ($ver -eq "17") {
                info "Using system JAVA_HOME: $($env:JAVA_HOME)"
                $env:PATH = "$($env:JAVA_HOME)\bin;$($env:PATH)"
                return $true
            } else { warn "JAVA_HOME points to Java $ver, need 17" }
        } catch {}
    }

    # 3. Try java.exe on PATH
    $javaExe = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaExe) {
        try {
            $ver = (& java.exe -version 2>&1)[0] -replace '.*"(\d+).*".*','$1'
            if ($ver -eq "17") {
                info "Found Java 17 on PATH"
                return $true
            }
        } catch {}
    }

    # 4. Search common Windows install locations
    $searchPaths = @(
        "$($env:ProgramFiles)\Java",
        "$($env:ProgramFiles)\Eclipse Adoptium",
        "$($env:ProgramFiles)\Microsoft",
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium"
    )
    foreach ($base in $searchPaths) {
        if (-not (Test-Path $base)) { continue }
        foreach ($dir in Get-ChildItem $base -Directory -ErrorAction SilentlyContinue) {
            $javaExe = Join-Path $dir.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                try {
                    $ver = (& $javaExe -version 2>&1)[0] -replace '.*"(\d+).*".*','$1'
                    if ($ver -eq "17") {
                        $env:JAVA_HOME = $dir.FullName
                        $env:PATH = "$($dir.FullName)\bin;$($env:PATH)"
                        info "Found Java 17: $($dir.FullName)"
                        return $true
                    }
                } catch {}
            }
        }
    }

    return $false
}

# ── Build = apply_config + apk ────────────────────────────────────────────────
function Invoke-Build {
    param([string]$ConfigArg = "")

    if ($ConfigArg -and (Test-Path $ConfigArg -PathType Container)) {
        $ConfigArg = Join-Path $ConfigArg "webapk.conf"
    }
    Apply-Config $ConfigArg
    Invoke-Apk
}

# ── Print usage ───────────────────────────────────────────────────────────────
function Show-Usage {
    Write-Host ""
    Write-Host "Usage:" -ForegroundColor White
    Write-Host "  .\make.ps1 keygen          " -NoNewline -ForegroundColor Cyan
    Write-Host "- Generate signing key"
    Write-Host "  .\make.ps1 build [config]  " -NoNewline -ForegroundColor Cyan
    Write-Host "- Apply configuration and build"
    Write-Host "  .\make.ps1 test            " -NoNewline -ForegroundColor Cyan
    Write-Host "- Install and test APK via adb"
    Write-Host "  .\make.ps1 clean           " -NoNewline -ForegroundColor Cyan
    Write-Host "- Clean build files, reset settings"
    Write-Host ""
    Write-Host "  .\make.ps1 apk             " -NoNewline -ForegroundColor Cyan
    Write-Host "- Build APK without apply_config"
    Write-Host "  .\make.ps1 apply_config    " -NoNewline -ForegroundColor Cyan
    Write-Host "- Apply settings from config file"
    Write-Host "  .\make.ps1 get_java        " -NoNewline -ForegroundColor Cyan
    Write-Host "- Download OpenJDK 17 locally"
    Write-Host "  .\make.ps1 regradle        " -NoNewline -ForegroundColor Cyan
    Write-Host "- Reinstall gradle"
    Write-Host ""
}

###############################################################################
# Bootstrap checks
###############################################################################

# Ensure Java 17 is available
if (-not (Find-Java17)) {
    warn "Java 17 not found"
    $answer = Read-Host "Would you like to download OpenJDK 17 to .\jvm? (y/N)"
    if ($answer -match '^[Yy]$') {
        Get-Java
    } else {
        err "Java 17 is required"
    }
}

# Final Java version verification
try {
    $javaVer = (& java.exe -version 2>&1)[0] -replace '.*"(\d+).*".*','$1'
    if ($javaVer -ne "17") { err "Wrong Java version: $javaVer. Java 17 required." }
} catch { err "Could not determine Java version" }

# Warn if adb not found
if (-not (Get-Command adb.exe -ErrorAction SilentlyContinue)) {
    warn "adb not found. '.\make.ps1 test' will not work"
}

# Download Android SDK if missing
if (-not (Test-Path $env:ANDROID_HOME)) {
    warn "Android Command Line Tools not found: .\cmdline-tools"
    $answer = Read-Host "Do you want to download them now? (y/n)"
    if ($answer -match '^[Yy]$') {
        Get-Tools
    } else {
        err "Cannot continue without Android Command Line Tools"
    }
}

###############################################################################
# Dispatch
###############################################################################

if (-not $Command) { Show-Usage; exit 1 }

switch ($Command) {
    "keygen"       { Invoke-Keygen }
    "build"        { Invoke-Build $Arg1 }
    "apk"          { Invoke-Apk }
    "apply_config" { Apply-Config $Arg1 }
    "test"         { Invoke-Test }
    "clean"        { Invoke-Clean }
    "get_java"     { Get-Java }
    "regradle"     { Invoke-Regradle }
    "get_tools"    { Get-Tools }
    default        { err "Unknown command: $Command. Run .\make.ps1 for usage." }
}
