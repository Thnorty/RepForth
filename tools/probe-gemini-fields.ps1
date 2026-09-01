<#
.SYNOPSIS
Bisects the app's real schema field by field to find what Gemini rejects.

.DESCRIPTION
Stage one (probe-gemini-schema.ps1) showed that every JSON Schema *feature* the
app uses is accepted on its own -- additionalProperties, min/max, minItems,
both nullable forms, minLength, and two levels of nesting all returned 200 --
while the app's real schema returned 400 INVALID_ARGUMENT. So the fault is a
combination, not a feature.

This builds that real schema up one field at a time. Every case is generated
from tools/gemini-schema.json rather than retyped, so each is a genuine subset
of what ships. The first FAIL names the field that cannot be added.

Unlike stage one, the schema is spliced into the request body as raw text
rather than round-tripped through ConvertFrom-Json -- PowerShell would otherwise
rewrite `0.0` as `0`, and the exact bytes are the thing under test.

.EXAMPLE
$env:GEMINI_API_KEY = "your-key"
.\tools\probe-gemini-fields.ps1 -Model gemini-3.5-flash-lite
#>
[CmdletBinding()]
param(
    [string] $Model = "gemini-3.5-flash",
    [int] $Repeat = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
    Write-Host "Set the key first, in this same window:" -ForegroundColor Yellow
    Write-Host '  $env:GEMINI_API_KEY = "your-key"'
    exit 1
}

$uri = "https://generativelanguage.googleapis.com/v1beta/models/$Model" + ":generateContent"
$headers = @{ "x-goog-api-key" = $env:GEMINI_API_KEY }

function Invoke-Probe {
    param(
        [string] $Name,
        [string] $Schema
    )

    # Raw splice: the bytes sent must be the bytes generated. An empty schema
    # means "send none at all" -- splicing "" produced `"responseJsonSchema":}}`
    # and a parse error from Google that looked like a real rejection.
    $generation = '{"responseMimeType":"application/json"}'
    if (-not [string]::IsNullOrWhiteSpace($Schema)) {
        $generation = '{"responseMimeType":"application/json","responseJsonSchema":' + $Schema + '}'
    }
    $body = '{"contents":[{"parts":[{"text":"Return one tiny example object matching the schema."}]}],' +
            '"generationConfig":' + $generation + '}'

    $status = 0
    $message = ""
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        Invoke-RestMethod -Method Post -Uri $uri -Headers $headers `
            -ContentType "application/json" -Body $bytes | Out-Null
        $status = 200
    } catch {
        $response = $_.Exception.Response
        if ($null -ne $response) {
            $status = [int] $response.StatusCode
            $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
            $raw = $reader.ReadToEnd()
            $reader.Close()
            try {
                $message = ($raw | ConvertFrom-Json).error.message
            } catch {
                $message = $raw
            }
        } else {
            $message = $_.Exception.Message
        }
    }

    return @{ status = $status; message = $message }
}

function Invoke-Case {
    param(
        [string] $Name,
        [string] $Schema
    )

    # Repeats the *identical* request. If the verdicts disagree, the endpoint is
    # not deterministic and no amount of schema bisecting can mean anything.
    $marks = @()
    $lastMessage = ""
    for ($i = 0; $i -lt $Repeat; $i++) {
        $r = Invoke-Probe -Name $Name -Schema $Schema
        if ($r.status -eq 200) { $marks += "." } else { $marks += "X"; $lastMessage = $r.message }
        if ($Repeat -gt 1) { Start-Sleep -Milliseconds 400 }
    }
    $joined = ($marks -join "")
    if ($joined -notmatch "X") {
        Write-Host ("  PASS  {0,-32} {1}" -f $Name, $joined) -ForegroundColor Green
    } elseif ($joined -notmatch "\.") {
        Write-Host ("  FAIL  {0,-32} {1}  {2}" -f $Name, $joined, $lastMessage) -ForegroundColor Red
    } else {
        Write-Host ("  FLAKY {0,-32} {1}  {2}" -f $Name, $joined, $lastMessage) -ForegroundColor Yellow
    }
}

Write-Host "model: $Model"
Write-Host ""

# probe-cases.generated.ps1 is scratch: whoever is bisecting writes the cases
# they need into it. With none staged, the useful default is the schema the app
# actually ships, which is the question this tool exists to answer.
$cases = Join-Path $PSScriptRoot "probe-cases.generated.ps1"
if (Test-Path $cases) {
    . $cases
} else {
    $schemaPath = Join-Path $PSScriptRoot "gemini-schema.json"
    if (-not (Test-Path $schemaPath)) {
        Write-Host "No cases staged and no $schemaPath to fall back on." -ForegroundColor Yellow
        exit 1
    }
    Invoke-Case "no schema at all" ""
    Invoke-Case "the schema the app sends" (Get-Content $schemaPath -Raw)
}

Write-Host ""
Write-Host "PASS = every attempt succeeded, FAIL = every attempt failed."
Write-Host "FLAKY = identical requests disagreed, and the endpoint is the variable."
