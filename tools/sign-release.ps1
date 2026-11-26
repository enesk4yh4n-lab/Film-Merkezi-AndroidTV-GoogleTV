[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Keystore,
    [string]$Alias='filmmerkezi',
    [string]$InputApk=(Join-Path $PSScriptRoot '../android/native/build/outputs/apk/release/native-release-unsigned.apk'),
    [Parameter(Mandatory=$true)][string]$OutputApk
)
$ErrorActionPreference='Stop'
$workspacePath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
$keyPath=(Resolve-Path -LiteralPath $Keystore).Path
if($keyPath.StartsWith($workspacePath,[StringComparison]::OrdinalIgnoreCase)){throw 'Keep the signing key outside the project.'}
$inputPath=(Resolve-Path -LiteralPath $InputApk).Path
$outputPath=[IO.Path]::GetFullPath($OutputApk)
if(Test-Path -LiteralPath $outputPath){throw 'Output APK already exists; choose a new output path.'}
$sdkPath=$env:ANDROID_HOME
if(-not $sdkPath){$sdkPath=$env:ANDROID_SDK_ROOT}
if(-not $sdkPath){throw 'Set ANDROID_HOME to the Android SDK directory.'}
$toolPath=Join-Path $sdkPath 'build-tools/36.1.0'
$align=Join-Path $toolPath 'zipalign.exe'
$signer=Join-Path $toolPath 'apksigner.bat'
if(-not (Test-Path -LiteralPath $align) -or -not (Test-Path -LiteralPath $signer)){throw 'Android Build Tools 36.1.0 are required.'}
function Invoke-Signer([string[]]$ToolArgs,[switch]$Quiet) {
    # Windows PowerShell 5 treats Java warnings on stderr as ErrorRecords.
    # The native exit code, rather than a harmless stderr warning, is authoritative.
    $previousPreference=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    try {
        if($Quiet){& $signer @ToolArgs *> $null}else{& $signer @ToolArgs}
        $script:signExit=$LASTEXITCODE
    }finally{$ErrorActionPreference=$previousPreference}
}
Invoke-Signer -ToolArgs @('verify',$inputPath) -Quiet
if($script:signExit -eq 0){throw 'Input is already signed. Build an unsigned release APK first.'}
$outputDirectory=Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$alignedPath=Join-Path $outputDirectory ('.'+[Guid]::NewGuid().ToString('N')+'.aligned.apk')
try {
    & $align -P 16 -f 4 $inputPath $alignedPath
    if($LASTEXITCODE -ne 0){throw 'zipalign failed.'}
    $signArgs=@('sign','--ks',$keyPath,'--ks-key-alias',$Alias,'--min-sdk-version','23',
        '--v1-signing-enabled','true','--v2-signing-enabled','true','--v4-signing-enabled','false','--out',$outputPath)
    if($env:FM_KEYSTORE_PASSWORD){$signArgs+=@('--ks-pass','env:FM_KEYSTORE_PASSWORD')}
    if($env:FM_KEY_PASSWORD){$signArgs+=@('--key-pass','env:FM_KEY_PASSWORD')}
    $signArgs+=$alignedPath
    Invoke-Signer -ToolArgs $signArgs
    if($script:signExit -ne 0){throw 'APK signing failed.'}
    Invoke-Signer -ToolArgs @('verify','--verbose',$outputPath)
    if($script:signExit -ne 0){throw 'APK signature verification failed.'}
    & $align -c -P 16 4 $outputPath
    if($LASTEXITCODE -ne 0){throw 'Signed APK alignment verification failed.'}
    Get-FileHash -LiteralPath $outputPath -Algorithm SHA256
}finally {
    if(Test-Path -LiteralPath $alignedPath){Remove-Item -LiteralPath $alignedPath}
}
