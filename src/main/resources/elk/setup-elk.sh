#!/usr/bin/env bash
#
# Elasticsearch 인덱스 템플릿을 올리고 Kibana 대시보드를 가져온다.
# ELK 가 떠 있는 상태에서 한 번만 실행하면 된다.
#
#   ./elk/setup-elk.sh
#
set -euo pipefail

ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[1/3] api-access-logs 인덱스 템플릿 등록 -> ${ELASTICSEARCH_URL}"
curl -sS -X PUT "${ELASTICSEARCH_URL}/_index_template/api-access-logs" \
  -H "Content-Type: application/json" \
  --data-binary "@${SCRIPT_DIR}/index-template-api-access.json"
echo

echo "[2/3] application-logs 인덱스 템플릿 등록"
curl -sS -X PUT "${ELASTICSEARCH_URL}/_index_template/application-logs" \
  -H "Content-Type: application/json" \
  --data-binary "@${SCRIPT_DIR}/index-template-application.json"
echo

echo "[3/3] Kibana 데이터 뷰/시각화/대시보드 가져오기 -> ${KIBANA_URL}"
curl -sS -X POST "${KIBANA_URL}/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form file="@${SCRIPT_DIR}/kibana-dashboard.ndjson"
echo

echo
echo "완료. 대시보드 주소: ${KIBANA_URL}/app/dashboards#/view/api-access-dashboard"
