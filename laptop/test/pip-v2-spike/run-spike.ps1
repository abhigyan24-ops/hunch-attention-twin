param(
  [ValidateSet('A','B','Both')][string]$Option='Both',
  [switch]$Interactive,
  [string]$OutputDirectory=(Join-Path $env:TEMP 'dhyan-pip-v2-m0-results')
)
$ErrorActionPreference='Stop'
$electron=Join-Path $PSScriptRoot '..\..\node_modules\electron\dist\electron.exe'
if(!(Test-Path -LiteralPath $electron)){throw 'Electron binary missing. Install laptop dependencies first.'}
$electron=(Resolve-Path -LiteralPath $electron).Path
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$options=if($Option -eq 'Both'){@('A','B')}else{@($Option)}
$previousNodeMode=$env:ELECTRON_RUN_AS_NODE
Remove-Item Env:ELECTRON_RUN_AS_NODE -ErrorAction SilentlyContinue
try {
  foreach($architecture in $options) {
    $result=Join-Path $OutputDirectory "option-$architecture.json"
    $arguments=@(('"'+(Join-Path $PSScriptRoot 'main.cjs')+'"'),"--option=$architecture",('"--result='+$result+'"'))
    if($Interactive){$arguments+='--interactive'}
    $process=Start-Process -FilePath $electron -ArgumentList $arguments -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -PassThru -Wait
    if($process.ExitCode -ne 0){throw "Architecture $architecture failed with exit $($process.ExitCode)"}
    if(!(Test-Path -LiteralPath $result)){throw "Architecture $architecture produced no result"}
    $measurement=Get-Content -Raw -LiteralPath $result | ConvertFrom-Json
    if($measurement.status -eq 'NOT_RUN_COMPLETE'){throw "Architecture $architecture did not complete: $($measurement.reason)"}
    $measurement | ConvertTo-Json -Depth 12
  }
} finally {
  if($null -ne $previousNodeMode){$env:ELECTRON_RUN_AS_NODE=$previousNodeMode}
}
