# Spark OpenLineage Demo

This repo contains a local demo that reads customer data from PostgreSQL with Spark, transforms it, writes curated Parquet into SeaweedFS via `s3a://`, and emits OpenLineage events into DataHub during the same Spark run.

## Stack

- Spark 4.1.1 job in Java 21
- Gradle 8.14.4 wrapper
- PostgreSQL source database seeded by Docker Compose
- SeaweedFS object storage with a pre-created `customer-lake` bucket
- DataHub GMS and frontend for lineage inspection

## Project Layout

- `docker-compose.yml`: local infrastructure and the disposable Spark runtime container
- `src/main/java/io/openlineage/demo`: Spark job code
- `src/test/java/io/openlineage/demo`: transformation-focused Spark test
- `docker/postgres/source/init/01_customers.sql`: source schema and sample data

## Build The Job

```bash
./gradlew shadowJar
```

The runnable jar is written to `build/libs/spark-openlineage-0.1.0-all.jar`.

## Start Infrastructure

```bash
docker compose up -d \
  postgres-source \
  seaweedfs-master \
  seaweedfs-volume \
  seaweedfs-filer \
  seaweedfs-s3 \
  seaweedfs-bootstrap
```

Then start the DataHub services.

```bash
docker compose up -d broker mysql mysql-setup search opensearch-setup system-update-quickstart datahub-gms datahub-frontend-react
```

The first DataHub startup is heavy. Expect several large image pulls and make sure Docker has enough memory for MySQL, OpenSearch, Kafka, and GMS at the same time.

Wait for the services to settle, then confirm the expected endpoints:

- PostgreSQL source: `localhost:5432`
- SeaweedFS S3 API: `http://localhost:8333`
- SeaweedFS filer UI: `http://localhost:8888`
- SeaweedFS master UI: `http://localhost:9333`
- DataHub GMS API: `http://localhost:8088`
- DataHub UI: `http://localhost:9002`

## Run The Spark Job

The simplest path is the helper script below. It keeps the existing DataHub ingestion, mirrors every OpenLineage event into the Spark terminal output, and also writes newline-delimited JSON to `build/openlineage/events.jsonl` on the host.

```bash
./scripts/run-with-openlineage-debug.sh
```

Optional application overrides can be passed through unchanged:

```bash
./scripts/run-with-openlineage-debug.sh \
  --app-name=customer-curation-demo \
  --jdbc-url=jdbc:postgresql://postgres-source:5432/customerdb \
  --jdbc-user=customers \
  --jdbc-password=customers \
  --source-table=public.customers \
  --output-path=s3a://customer-lake/customers_curated/ \
  --s3-endpoint=http://seaweedfs-s3:8333 \
  --s3-access-key=seaweedfsadmin \
  --s3-secret-key=seaweedfsadmin123
```

If you want the raw `spark-submit` command instead, use the same jar and listener but switch the OpenLineage transport to a composite sink.

```bash
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
  --conf spark.openlineage.transport.type=composite \
  --conf spark.openlineage.transport.continueOnFailure=true \
  --conf spark.openlineage.transport.transports.datahub.type=http \
  --conf spark.openlineage.transport.transports.datahub.url=http://datahub-gms:8080 \
  --conf spark.openlineage.transport.transports.datahub.endpoint=/openapi/openlineage/api/v1/lineage \
  --conf spark.openlineage.transport.transports.terminal.type=console \
  --conf spark.openlineage.transport.transports.file_sink.type=file \
  --conf spark.openlineage.transport.transports.file_sink.location=/workspace/build/openlineage/events.jsonl \
  --conf spark.openlineage.namespace=spark-openlineage-demo \
  /workspace/build/libs/spark-openlineage-0.1.0-all.jar
```

## Verify The Output

### SeaweedFS

List the demo bucket contents through the SeaweedFS S3 endpoint. The Spark job should overwrite the `customers_curated/` prefix with Parquet files on each run.

```bash
docker compose run --rm --entrypoint aws seaweedfs-bootstrap \
  s3 ls s3://customer-lake/customers_curated/ \
  --recursive \
  --endpoint-url http://seaweedfs-s3:8333
```

### DataHub

Open the DataHub UI at `http://localhost:9002`. Search for `customers_curated` or `public.customers`, then open the dataset entity and inspect the Lineage tab.

Expected checks:

- one PostgreSQL source dataset corresponding to `public.customers`
- one S3 dataset for `customers_curated`
- dataset-level lineage between the two
- one Spark SQL data job named `customer_curation_demo.execute_insert_into_hadoop_fs_relation_command.customers_curated`

The OpenLineage ingest endpoint exposed by DataHub GMS is:

- `http://localhost:8088/openapi/openlineage/api/v1/lineage`

The GMS GraphQL API can be used to confirm the ingested entities:

```bash
curl -sS -X POST http://localhost:8088/api/graphql \
  -H 'Content-Type: application/json' \
  --data '{"query":"query SearchDatasets { search(input:{type: DATASET, query:\"customers_curated\", start:0, count:10}) { total searchResults { entity { urn type ... on Dataset { name platform { name } } } } } }"}'
```

To inspect the fine-grained lineage that DataHub persisted from the OpenLineage column-lineage facet:

```bash
curl -sS -X POST http://localhost:8088/api/graphql \
  -H 'Content-Type: application/json' \
  --data '{"query":"query JobFineGrained { dataJob(urn:\"urn:li:dataJob:(urn:li:dataFlow:(spark,customer_curation_demo,dev),customer_curation_demo.execute_insert_into_hadoop_fs_relation_command.customers_curated)\") { inputOutput { fineGrainedLineages { transformOperation upstreams { urn path } downstreams { urn path } } } } }"}'
```

That query should show mappings such as:

- `first_name` + `last_name` -> `fullname`
- `dob` -> `age`
- `customer_since` -> `customer_tenure_years`

### OpenLineage Payloads

When you use `./scripts/run-with-openlineage-debug.sh`, the same events are available in two extra places:

- the Spark submit terminal output, emitted by the OpenLineage `console` transport
- `build/openlineage/events.jsonl`, emitted by the OpenLineage `file` transport

Each line in `build/openlineage/events.jsonl` is one event JSON document. Useful quick checks:

```bash
wc -l build/openlineage/events.jsonl
```

```bash
tail -n 2 build/openlineage/events.jsonl | jq .
```

### Manual Smoke Query

You can inspect the curated dataset with Spark directly:

```bash
docker compose run --rm \
  -e HOME=/tmp/spark-home \
  -e SPARK_LOCAL_DIRS=/tmp/spark-local \
  --entrypoint /opt/spark/bin/spark-sql \
  spark \
  --master 'local[*]' \
  --packages org.apache.hadoop:hadoop-aws:3.4.3 \
  --conf spark.jars.ivy=/tmp/spark-home/.ivy2 \
  --conf spark.hadoop.fs.s3a.endpoint=http://seaweedfs-s3:8333 \
  --conf spark.hadoop.fs.s3a.access.key=seaweedfsadmin \
  --conf spark.hadoop.fs.s3a.secret.key=seaweedfsadmin123 \
  --conf spark.hadoop.fs.s3a.path.style.access=true \
  --conf spark.hadoop.fs.s3a.connection.ssl.enabled=false \
  --conf spark.hadoop.fs.s3a.aws.credentials.provider=org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider \
  -e "SELECT * FROM parquet.\`s3a://customer-lake/customers_curated/\`;"
```

## Run Tests

```bash
./gradlew test
```

The test suite validates the core transformation contract:

- `fullname` is built from `first_name` and `last_name`
- `age` is derived from `dob`
- `customer_tenure_years` is derived from `customer_since`
- raw source-only columns do not survive in the output schema
