[CmdletBinding()]
param(
    [string]$Region = "us-east-1",
    [int]$AccountId = 8439524,
    [string]$NotificationEmail,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Continue"

function Write-Head($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Write-Ok($t) { Write-Host "  [OK]    $t" -ForegroundColor Green }
function Write-Empty($t) { Write-Host "  [VAZIO] $t" -ForegroundColor Yellow }
function Write-Bad($t) { Write-Host "  [ERRO]  $t" -ForegroundColor Red }

function Invoke-Probe {
    param([Parameter(Mandatory)][string]$Exe, [Parameter(Mandatory)][string[]]$ProbeArgs)
    $ErrorActionPreference = "Continue"
    $out = & $Exe @ProbeArgs 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return $out
}

# PS 5.1 negocia TLS 1.0 por default e a api.newrelic.com recusa — o erro vem como "conexão fechada".
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$repoRoot = Split-Path $PSScriptRoot -Parent
$alertsPath = Join-Path $repoRoot "k8s/newrelic/alerts.json"
if (-not (Test-Path $alertsPath)) { Write-Bad "nao achei $alertsPath"; exit 1 }

Write-Head "User key (SSM)"
$key = ((Invoke-Probe -Exe "aws" -ProbeArgs @(
            "ssm", "get-parameter", "--name", "/fase4/newrelic/user-key", "--with-decryption",
            "--region", $Region, "--query", "Parameter.Value", "--output", "text")) -join "") -replace "`r|`n", ""
if (-not $key -or -not $key.StartsWith("NRAK-")) {
    Write-Bad "/fase4/newrelic/user-key ausente ou nao e uma USER key (NRAK-)."
    exit 1
}
Write-Ok "user key lida ($($key.Length) chars)"

function Invoke-NerdGraph {
    param([Parameter(Mandatory)][string]$Query)
    $body = @{ query = $Query } | ConvertTo-Json -Depth 20 -Compress
    # Bytes UTF-8: o Invoke-RestMethod do PS 5.1 serializa string como ASCII e corrompe acentos.
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    try {
        return Invoke-RestMethod -Method Post -Uri "https://api.newrelic.com/graphql" `
            -Headers @{ "API-Key" = $key } -ContentType "application/json; charset=utf-8" `
            -Body $bytes -TimeoutSec 60
    }
    catch { return [pscustomobject]@{ transportError = $_.Exception.Message } }
}

# `Get-Content` sem -Encoding le ANSI no PS 5.1 quando o arquivo nao tem BOM.
$spec = [System.IO.File]::ReadAllText($alertsPath, [System.Text.UTF8Encoding]::new($false)) | ConvertFrom-Json

Write-Head "Validando $($spec.conditions.Count) condicao(oes)"
$invalid = 0
foreach ($c in $spec.conditions) {
    $probe = "$($c.nrql) SINCE 10 minutes ago"
    $escaped = ($probe -replace '\\', '\\' -replace '"', '\"')
    $res = Invoke-NerdGraph "{ actor { account(id: $AccountId) { nrql(query: `"$escaped`") { results } } } }"

    if ($res.transportError) { Write-Bad "$($c.name) — transporte: $($res.transportError)"; $invalid++; continue }
    if ($res.errors) { Write-Bad "$($c.name) — $($res.errors[0].message)"; $invalid++; continue }

    $results = @($res.data.actor.account.nrql.results)
    if ($results.Count -eq 0) { Write-Empty "$($c.name) — sintaxe ok, mas NENHUMA linha"; continue }

    $values = @($results[0].PSObject.Properties |
        Where-Object { $_.Name -notin @("beginTimeSeconds", "endTimeSeconds", "timestamp", "facet") } |
        ForEach-Object { $_.Value })
    if (@($values | Where-Object { $null -ne $_ }).Count -eq 0) {
        Write-Empty "$($c.name) — linha retornada, mas o valor e NULL (serie ausente na janela)"
    }
    else { Write-Ok "$($c.name) — sinal presente: $($results[0] | ConvertTo-Json -Compress)" }
}
if ($invalid -gt 0) { Write-Bad "$invalid condicao(oes) invalida(s) — nao vou publicar."; exit 1 }

if ($ValidateOnly) { Write-Host "`n-ValidateOnly: nada publicado." -ForegroundColor Cyan; exit 0 }

Write-Head "Politica"
$search = Invoke-NerdGraph "{ actor { account(id: $AccountId) { alerts { policiesSearch(searchCriteria: {name: `"$($spec.policyName)`"}) { policies { id name } } } } } }"
$policy = $search.data.actor.account.alerts.policiesSearch.policies | Where-Object { $_.name -eq $spec.policyName } | Select-Object -First 1

if ($policy) { Write-Ok "reusando politica existente (id $($policy.id))" }
else {
    $res = Invoke-NerdGraph "mutation { alertsPolicyCreate(accountId: $AccountId, policy: {name: `"$($spec.policyName)`", incidentPreference: $($spec.incidentPreference)}) { id name } }"
    if ($res.errors) { Write-Bad $res.errors[0].message; exit 1 }
    $policy = $res.data.alertsPolicyCreate
    Write-Ok "politica criada (id $($policy.id))"
}

$existing = @{}
$res = Invoke-NerdGraph "{ actor { account(id: $AccountId) { alerts { nrqlConditionsSearch(searchCriteria: {policyId: `"$($policy.id)`"}) { nrqlConditions { id name } } } } } }"
foreach ($c in $res.data.actor.account.alerts.nrqlConditionsSearch.nrqlConditions) { $existing[$c.name] = $c.id }

Write-Head "Condicoes"
foreach ($c in $spec.conditions) {
    $nrql = ($c.nrql -replace '\\', '\\' -replace '"', '\"')
    $desc = ($c.description -replace '\\', '\\' -replace '"', '\"')
    $condition = @"
{
  name: "$($c.name)"
  description: "$desc"
  enabled: true
  nrql: { query: "$nrql" }
  signal: {
    aggregationWindow: $($c.signalAggregationWindowSeconds)
    aggregationMethod: EVENT_FLOW
    aggregationDelay: 120
  }
  terms: [{
    threshold: $($c.thresholdCritical)
    thresholdOccurrences: ALL
    thresholdDuration: $($c.thresholdDurationSeconds)
    operator: $($c.operator)
    priority: CRITICAL
  }]
  expiration: {
    expirationDuration: $($c.expirationDurationSeconds)
    openViolationOnExpiration: $($c.openViolationOnExpiration.ToString().ToLower())
    closeViolationsOnExpiration: true
  }
  violationTimeLimitSeconds: 86400
}
"@
    if ($existing.ContainsKey($c.name)) {
        $res = Invoke-NerdGraph "mutation { alertsNrqlConditionStaticUpdate(accountId: $AccountId, id: `"$($existing[$c.name])`", condition: $condition) { id name } }"
        $payload = $res.data.alertsNrqlConditionStaticUpdate
        $verb = "atualizada"
    }
    else {
        $res = Invoke-NerdGraph "mutation { alertsNrqlConditionStaticCreate(accountId: $AccountId, policyId: `"$($policy.id)`", condition: $condition) { id name } }"
        $payload = $res.data.alertsNrqlConditionStaticCreate
        $verb = "criada"
    }

    if ($res.transportError) { Write-Bad "$($c.name) — transporte: $($res.transportError)"; continue }
    if ($res.errors) { Write-Bad "$($c.name) — $($res.errors[0].message)"; continue }
    Write-Ok "$($c.name) $verb (id $($payload.id))"
}

Write-Head "Notificacao"

if (-not $NotificationEmail) {
    $NotificationEmail = ((Invoke-Probe -Exe "aws" -ProbeArgs @(
                "ssm", "get-parameter", "--name", "/fase4/newrelic/alert-email",
                "--region", $Region, "--query", "Parameter.Value", "--output", "text")) -join "") -replace "[\r\n]", ""
}

if (-not $NotificationEmail) {
    Write-Empty "sem e-mail: as condicoes abrem incidente na UI e nao avisam ninguem"
    Write-Host "         Use -NotificationEmail ou publique em /fase4/newrelic/alert-email." -ForegroundColor DarkGray
}
else {
    $name = $spec.policyName

    $q = "{ actor { account(id: $AccountId) { aiNotifications { destinations(filters: {name: `"$name`"}) { entities { id } } } } } }"
    $destination = (Invoke-NerdGraph $q).data.actor.account.aiNotifications.destinations.entities | Select-Object -First 1
    if (-not $destination) {
        $m = "mutation { aiNotificationsCreateDestination(accountId: $AccountId, destination: {name: `"$name`", type: EMAIL, properties: [{key: `"email`", value: `"$NotificationEmail`"}]}) { destination { id } error { ... on AiNotificationsResponseError { description } } } }"
        $r = Invoke-NerdGraph $m
        if ($r.errors) { Write-Bad $r.errors[0].message }
        $destination = $r.data.aiNotificationsCreateDestination.destination
    }

    if (-not $destination) { Write-Bad "nao consegui criar a destination de e-mail" }
    else {
        Write-Ok "destination de e-mail (id $($destination.id))"

        $q = "{ actor { account(id: $AccountId) { aiNotifications { channels(filters: {name: `"$name`"}) { entities { id } } } } } }"
        $channel = (Invoke-NerdGraph $q).data.actor.account.aiNotifications.channels.entities | Select-Object -First 1
        if (-not $channel) {
            $m = "mutation { aiNotificationsCreateChannel(accountId: $AccountId, channel: {name: `"$name`", type: EMAIL, destinationId: `"$($destination.id)`", product: IINT, properties: [{key: `"subject`", value: `"[Car Workshop] {{ issueTitle }}`"}]}) { channel { id } error { ... on AiNotificationsResponseError { description } } } }"
            $r = Invoke-NerdGraph $m
            if ($r.errors) { Write-Bad $r.errors[0].message }
            $channel = $r.data.aiNotificationsCreateChannel.channel
        }

        if (-not $channel) { Write-Bad "nao consegui criar o channel" }
        else {
            Write-Ok "channel de e-mail (id $($channel.id))"

            $q = "{ actor { account(id: $AccountId) { aiWorkflows { workflows(filters: {name: `"$name`"}) { entities { id } } } } } }"
            $workflow = (Invoke-NerdGraph $q).data.actor.account.aiWorkflows.workflows.entities | Select-Object -First 1
            if ($workflow) { Write-Ok "workflow ja existe (id $($workflow.id))" }
            else {
                $m = "mutation { aiWorkflowsCreateWorkflow(accountId: $AccountId, createWorkflowData: {mutingRulesHandling: NOTIFY_ALL_ISSUES, name: `"$name`", workflowEnabled: true, destinationsEnabled: true, issuesFilter: {name: `"policy`", type: FILTER, predicates: [{attribute: `"labels.policyIds`", operator: EXACTLY_MATCHES, values: [`"$($policy.id)`"]}]}, destinationConfigurations: [{channelId: `"$($channel.id)`", notificationTriggers: [ACTIVATED, CLOSED]}]}) { workflow { id } errors { description } } }"
                $r = Invoke-NerdGraph $m
                if ($r.errors) { Write-Bad $r.errors[0].message }
                elseif ($r.data.aiWorkflowsCreateWorkflow.errors) { $r.data.aiWorkflowsCreateWorkflow.errors | ForEach-Object { Write-Bad $_.description } }
                else { Write-Ok "workflow criado (id $($r.data.aiWorkflowsCreateWorkflow.workflow.id)) — notifica em ACTIVATED e CLOSED" }
            }
        }
    }
}

Write-Host ""
Write-Host "  Politica: $($spec.policyName) (id $($policy.id)) — abra em Alerts -> Alert Policies" -ForegroundColor Cyan
