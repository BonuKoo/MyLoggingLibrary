# Elasticsearch 인덱스 템플릿을 올리고 Kibana 대시보드를 가져온다. (Windows PowerShell)
# ELK 가 떠 있는 상태에서 한 번만 실행하면 된다.
#
#   powershell -ExecutionPolicy Bypass -File .\elk\setup-elk.ps1
#
$ErrorActionPreference = "Stop"

$ElasticsearchUrl = if ($env:ELASTICSEARCH_URL) { $env:ELASTICSEARCH_URL } else { "http://localhost:9200" }
$KibanaUrl        = if ($env:KIBANA_URL)        { $env:KIBANA_URL }        else { "http://localhost:5601" }
$ScriptDir        = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "[1/3] api-access-logs 인덱스 템플릿 등록 -> $ElasticsearchUrl"
curl.exe -sS -X PUT "$ElasticsearchUrl/_index_template/api-access-logs" `
  -H "Content-Type: application/json" `
  --data-binary "@$ScriptDir\index-template-api-access.json"
Write-Host ""

Write-Host "[2/3] application-logs 인덱스 템플릿 등록"
curl.exe -sS -X PUT "$ElasticsearchUrl/_index_template/application-logs" `
  -H "Content-Type: application/json" `
  --data-binary "@$ScriptDir\index-template-application.json"
Write-Host ""

Write-Host "[3/3] Kibana 데이터 뷰/시각화/대시보드 가져오기 -> $KibanaUrl"
curl.exe -sS -X POST "$KibanaUrl/api/saved_objects/_import?overwrite=true" `
  -H "kbn-xsrf: true" `
  --form "file=@$ScriptDir\kibana-dashboard.ndjson"
Write-Host ""

Write-Host ""
Write-Host "완료. 대시보드 주소: $KibanaUrl/app/dashboards#/view/api-access-dashboard"
