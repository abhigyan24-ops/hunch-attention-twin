[CmdletBinding()]
param([switch]$InitializeNewIdentity, [switch]$CopyToClipboard)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$keyDir = Join-Path $root '.keys'
$keystore = Join-Path $keyDir 'dhyan-release.jks'
$passwordFile = Join-Path $keyDir 'dhyan-release.password.dpapi'

$keytool = (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source
if (-not $keytool -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
    if (Test-Path -LiteralPath $candidate) { $keytool = $candidate }
}
if (-not $keytool) {
    $candidate = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr/bin/keytool.exe'
    if (Test-Path -LiteralPath $candidate) { $keytool = $candidate }
}
if (-not $keytool) { throw 'Install a JDK or set JAVA_HOME to a JDK installation.' }

Push-Location $root
try {
    foreach ($path in @('.keys/dhyan-release.jks', '.keys/dhyan-release.password.dpapi', 'keystore-ignore-check.jks')) {
        git check-ignore --quiet -- $path
        if ($LASTEXITCODE -ne 0) { throw "Git must ignore $path before proceeding." }
    }
    if ($InitializeNewIdentity) {
        if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $passwordFile)) {
            throw 'Signing material already exists. Restore the existing identity.'
        }
        New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $bytes = New-Object byte[] 32
            $random.GetBytes($bytes)
            $env:DHYAN_RELEASE_KEY_PASSWORD = [Convert]::ToBase64String($bytes)
        } finally { $random.Dispose() }
        ConvertTo-SecureString $env:DHYAN_RELEASE_KEY_PASSWORD -AsPlainText -Force |
            ConvertFrom-SecureString | Set-Content -LiteralPath $passwordFile
        & $keytool -genkeypair -storetype JKS -keystore $keystore -alias dhyan-release -keyalg RSA -keysize 2048 -validity 10000 -storepass:env DHYAN_RELEASE_KEY_PASSWORD -keypass:env DHYAN_RELEASE_KEY_PASSWORD -dname 'CN=Dhyan Companion, O=Dhyan, C=IN'
        if ($LASTEXITCODE -ne 0) { throw 'keytool failed to generate the signing identity.' }
    } else {
        if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $passwordFile)) {
            throw 'Restore the existing release keystore and password backup. Use -InitializeNewIdentity only for a new signing lineage.'
        }
        $secure = ConvertTo-SecureString ((Get-Content -Raw -LiteralPath $passwordFile).Trim())
        $env:DHYAN_RELEASE_KEY_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
    }

    $certificate = & $keytool '-J-Duser.language=en' -list -v -keystore $keystore -alias dhyan-release -storepass:env DHYAN_RELEASE_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify the release certificate.' }
    $certificate | Select-String 'SHA256:'
    if ($CopyToClipboard) {
        Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)))
        Write-Output 'ANDROID_KEYSTORE_B64 copied to the clipboard.'
    }
} finally {
    Remove-Item Env:DHYAN_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
