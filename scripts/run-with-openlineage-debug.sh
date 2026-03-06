#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
workspace_dir="$(cd "${script_dir}/.." && pwd)"
jar_path="${workspace_dir}/build/libs/spark-openlineage-0.1.0-all.jar"
events_file="${OPENLINEAGE_EVENTS_FILE:-build/openlineage/events.jsonl}"
events_path="${workspace_dir}/${events_file}"

if [[ ! -f "${jar_path}" ]]; then
  echo "Missing jar at ${jar_path}. Build it first with ./gradlew shadowJar." >&2
  exit 1
fi

mkdir -p "$(dirname "${events_path}")"
: > "${events_path}"

docker compose run --rm \
  -e HOME=/tmp/spark-home \
  -e SPARK_LOCAL_DIRS=/tmp/spark-local \
  --entrypoint /opt/spark/bin/spark-submit \
  spark \
  --master 'local[*]' \
  --packages io.openlineage:openlineage-spark_2.13:1.44.0,org.apache.hadoop:hadoop-aws:3.4.3,org.postgresql:postgresql:42.7.10 \
  --conf spark.jars.ivy=/tmp/spark-home/.ivy2 \
  --conf spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener \
  --conf spark.driver.extraJavaOptions=-Dlog4j2.configurationFile=file:/workspace/config/log4j2-openlineage.properties \
  --conf spark.executor.extraJavaOptions=-Dlog4j2.configurationFile=file:/workspace/config/log4j2-openlineage.properties \
  --conf spark.openlineage.namespace=spark-openlineage-demo \
  --conf spark.openlineage.transport.type=composite \
  --conf spark.openlineage.transport.continueOnFailure=true \
  --conf spark.openlineage.transport.transports.datahub.type=http \
  --conf spark.openlineage.transport.transports.datahub.url=http://datahub-gms:8080 \
  --conf spark.openlineage.transport.transports.datahub.endpoint=/openapi/openlineage/api/v1/lineage \
  --conf spark.openlineage.transport.transports.terminal.type=console \
  --conf spark.openlineage.transport.transports.file_sink.type=file \
  --conf spark.openlineage.transport.transports.file_sink.location="/workspace/${events_file}" \
  /workspace/build/libs/spark-openlineage-0.1.0-all.jar \
  "$@"

echo
echo "OpenLineage events were mirrored to ${events_path}"
