<#
.SYNOPSIS
Finds which part of our structured-output schema Gemini rejects.

.DESCRIPTION
The app sends `generationConfig.responseJsonSchema` and gets back
`400 INVALID_ARGUMENT: Request contains an invalid argument` with no indication
of *which* argument. No JVM test can catch this: MockWebServer accepts any
schema at all, so the live API is the only oracle.

This bisects. Each case adds one feature to the previous one, so the first FAIL
after a PASS names the culprit. The final case is the schema the app actually
sends, dumped from AiWorkoutJsonSchema into tools/gemini-schema.json.

Runs in the same PowerShell session, so $env:GEMINI_API_KEY is read directly --
no child-process inheritance to go wrong. The key is never printed, never
written to a file, and never passed as an argument.

.EXAMPLE
$env:GEMINI_API_KEY = "your-key"
.\tools\probe-gemini-schema.ps1

.EXAMPLE
$env:GEMINI_API_KEY = "your-key"
.\tools\probe-gemini-schema.ps1 -Model gemini-3.5-flash-lite
#>
[CmdletBinding()]
param(
    [string] $Model = "gemini-3.5-flash"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($env:GEMINI_API_KEY)) {
    Write-Host "Set the key first, in this same window:" -ForegroundColor Yellow
    Write-Host '  $env:GEMINI_API_KEY = "your-key"'
    Write-Host "Then run this script again. It is never printed or stored."
    exit 1
}

$uri = "https://generativelanguage.googleapis.com/v1beta/models/$Model" + ":generateContent"
$headers = @{ "x-goog-api-key" = $env:GEMINI_API_KEY }

function Invoke-Probe {
    param(
        [string] $Name,
        [string] $Schema
    )

    $generationConfig = @{ responseMimeType = "application/json" }
    if (-not [string]::IsNullOrWhiteSpace($Schema)) {
        # Round-trips through ConvertFrom-Json so a malformed variant fails here
        # rather than looking like a rejection from Google.
        $generationConfig["responseJsonSchema"] = ($Schema | ConvertFrom-Json)
    }

    $payload = @{
        contents         = @(
            @{ parts = @(@{ text = "Return one tiny example object matching the schema." }) }
        )
        generationConfig = $generationConfig
    } | ConvertTo-Json -Depth 40 -Compress

    $status = 0
    $message = ""
    try {
        Invoke-RestMethod -Method Post -Uri $uri -Headers $headers `
            -ContentType "application/json" -Body $payload | Out-Null
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

    if ($status -eq 200) {
        Write-Host ("  PASS  {0,-34} {1}" -f $Name, $status) -ForegroundColor Green
    } else {
        Write-Host ("  FAIL  {0,-34} {1}  {2}" -f $Name, $status, $message) -ForegroundColor Red
    }
}

Write-Host "model: $Model"
Write-Host ""

Invoke-Probe "no schema at all" ""
Invoke-Probe "flat object" '{"type":"object","properties":{"a":{"type":"string"}},"required":["a"]}'
Invoke-Probe "+ additionalProperties false" '{"type":"object","additionalProperties":false,"properties":{"a":{"type":"string"}},"required":["a"]}'
Invoke-Probe "+ integer min/max" '{"type":"object","properties":{"a":{"type":"integer","minimum":1,"maximum":3}},"required":["a"]}'
Invoke-Probe "+ array minItems/maxItems" '{"type":"object","properties":{"a":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"string"}}},"required":["a"]}'
Invoke-Probe "nullable via type array" '{"type":"object","properties":{"a":{"type":["integer","null"],"minimum":1,"maximum":3}},"required":["a"]}'
Invoke-Probe "nullable via anyOf+null" '{"type":"object","properties":{"a":{"anyOf":[{"type":"integer"},{"type":"null"}]}},"required":["a"]}'
Invoke-Probe "string minLength" '{"type":"object","properties":{"a":{"type":"string","minLength":1}},"required":["a"]}'
Invoke-Probe "two levels of nesting" '{"type":"object","properties":{"d":{"type":"array","items":{"type":"object","properties":{"e":{"type":"array","items":{"type":"object","properties":{"x":{"type":"string"}},"required":["x"]}}},"required":["e"]}}},"required":["d"]}'

$schemaPath = Join-Path $PSScriptRoot "gemini-schema.json"
if (Test-Path $schemaPath) {
    Invoke-Probe "the app's real schema" (Get-Content $schemaPath -Raw)
} else {
    Write-Host "  SKIP  the app's real schema (tools/gemini-schema.json missing)"
}

Write-Host ""
Write-Host "The first FAIL after a PASS names the feature Gemini rejects."
Write-Host "If only the last line fails, it is the whole schema's size or depth."
