$ErrorActionPreference = 'Stop'
& python (Join-Path $PSScriptRoot 'build.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
