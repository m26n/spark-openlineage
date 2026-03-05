package io.openlineage.demo;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;

public final class CustomerPipelineApp {
    private CustomerPipelineApp() {
    }

    public static void main(String[] args) {
        CustomerJobConfig config = CustomerJobConfig.fromArgs(args);

        SparkSession spark = SparkSession.builder()
                .appName(config.appName())
                .getOrCreate();

        spark.sparkContext().setLogLevel("WARN");
        configureS3(spark, config);

        Dataset<Row> source = spark.read()
                .format("jdbc")
                .option("url", config.jdbcUrl())
                .option("dbtable", config.sourceTable())
                .option("user", config.jdbcUser())
                .option("password", config.jdbcPassword())
                .option("driver", "org.postgresql.Driver")
                .load();

        CustomerTransform.transform(source)
                .write()
                .mode(SaveMode.Overwrite)
                .parquet(config.outputPath());

        spark.stop();
    }

    private static void configureS3(SparkSession spark, CustomerJobConfig config) {
        spark.sparkContext().hadoopConfiguration().set("fs.s3a.endpoint", config.s3Endpoint());
        spark.sparkContext().hadoopConfiguration().set("fs.s3a.access.key", config.s3AccessKey());
        spark.sparkContext().hadoopConfiguration().set("fs.s3a.secret.key", config.s3SecretKey());
        spark.sparkContext().hadoopConfiguration().set("fs.s3a.path.style.access", "true");
        spark.sparkContext().hadoopConfiguration().set("fs.s3a.connection.ssl.enabled", "false");
        spark.sparkContext().hadoopConfiguration().set(
                "fs.s3a.aws.credentials.provider",
                "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider");
        spark.sparkContext().hadoopConfiguration().set(
                "fs.s3a.impl",
                "org.apache.hadoop.fs.s3a.S3AFileSystem");
    }
}

