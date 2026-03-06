# Spark OpenLineage Demo

This repo contains a local demo that reads customer data from PostgreSQL with Spark, transforms it, writes curated Parquet into MinIO via `s3a://`, and emits OpenLineage events into DataHub during the same Spark run.

## Stack

- Spark 4.1.1 job in Java 21
- Gradle 8.14.4 wrapper
- PostgreSQL source database seeded by Docker Compose
- MinIO object storage with a pre-created `customer-lake` bucket
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
docker compose up -d postgres-source minio minio-bootstrap
```

Then start the DataHub services.

```bash
docker compose up -d broker mysql mysql-setup search opensearch-setup system-update-quickstart datahub-gms datahub-frontend-react
```

The first DataHub startup is heavy. Expect several large image pulls and make sure Docker has enough memory for MySQL, OpenSearch, Kafka, and GMS at the same time.

Wait for the services to settle, then confirm the expected endpoints:

- PostgreSQL source: `localhost:5432`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- DataHub GMS API: `http://localhost:8088`
- DataHub UI: `http://localhost:9002`

## Run The Spark Job

Use the same jar and the same OpenLineage Spark listener, but point the transport at DataHub GMS.

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
  --conf spark.openlineage.transport.type=http \
  --conf spark.openlineage.transport.url=http://datahub-gms:8080 \
  --conf spark.openlineage.transport.endpoint=/openapi/openlineage/api/v1/lineage \
  --conf spark.openlineage.namespace=spark-openlineage-demo \
  /workspace/build/libs/spark-openlineage-0.1.0-all.jar
```

Optional overrides can be passed as application arguments after the jar:

```bash
--app-name=customer-curation-demo
--jdbc-url=jdbc:postgresql://postgres-source:5432/customerdb
--jdbc-user=customers
--jdbc-password=customers
--source-table=public.customers
--output-path=s3a://customer-lake/customers_curated/
--s3-endpoint=http://minio:9000
--s3-access-key=minioadmin
--s3-secret-key=minioadmin
```

## Verify The Output

### MinIO

Open the MinIO console at `http://localhost:9001` and inspect bucket `customer-lake`. The Spark job should overwrite the `customers_curated/` prefix with Parquet files on each run.

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
  --conf spark.hadoop.fs.s3a.endpoint=http://minio:9000 \
  --conf spark.hadoop.fs.s3a.access.key=minioadmin \
  --conf spark.hadoop.fs.s3a.secret.key=minioadmin \
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
