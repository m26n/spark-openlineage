# Spark OpenLineage Demo

This repo contains a local demo that reads customer data from PostgreSQL with Spark, transforms it, writes curated Parquet into MinIO via `s3a://`, and emits OpenLineage events into Marquez during the same Spark run.

## Stack

- Spark 3.5.8 job in Java 17
- Gradle 8.14.4 wrapper
- PostgreSQL source database seeded by Docker Compose
- MinIO object storage with a pre-created `customer-lake` bucket
- Marquez API and web UI for lineage inspection

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
docker compose up -d postgres-source minio minio-bootstrap marquez-db marquez-api marquez-web
```

Wait for the services to settle, then confirm the expected endpoints:

- PostgreSQL source: `localhost:5432`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- Marquez API: `http://localhost:5005`
- Marquez UI: `http://localhost:3000`

## Run The Spark Job

Submit the job from the Spark container. The job defaults already match the Docker Compose service names and credentials.

```bash
docker compose run --rm \
  -e HOME=/tmp/spark-home \
  -e SPARK_LOCAL_DIRS=/tmp/spark-local \
  --entrypoint /opt/spark/bin/spark-submit \
  spark \
  --master local[*] \
  --packages io.openlineage:openlineage-spark_2.12:1.44.0,org.apache.hadoop:hadoop-aws:3.3.4,org.postgresql:postgresql:42.7.7 \
  --conf spark.jars.ivy=/tmp/spark-home/.ivy2 \
  --conf spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener \
  --conf spark.openlineage.transport.type=http \
  --conf spark.openlineage.transport.url=http://marquez-api:5000 \
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

### Marquez

Open the Marquez UI at `http://localhost:3000`. Look for the namespace `spark-openlineage-demo` and the Spark job derived from app name `customer-curation-demo`. The lineage view should show:

- one PostgreSQL input dataset for `public.customers`
- one object-store output dataset for `s3a://customer-lake/customers_curated/`

### Manual Smoke Query

You can inspect the curated dataset with Spark directly:

```bash
docker compose run --rm \
  -e HOME=/tmp/spark-home \
  -e SPARK_LOCAL_DIRS=/tmp/spark-local \
  --entrypoint /opt/spark/bin/spark-sql \
  spark \
  --master local[*] \
  --packages org.apache.hadoop:hadoop-aws:3.3.4 \
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
