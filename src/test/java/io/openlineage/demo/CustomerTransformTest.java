package io.openlineage.demo;

import java.sql.Date;
import java.util.List;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.spark.sql.functions.to_date;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CustomerTransformTest {
    private static SparkSession spark;

    @BeforeAll
    static void setUpSpark() {
        spark = SparkSession.builder()
                .appName("customer-transform-test")
                .master("local[1]")
                .config("spark.ui.enabled", "false")
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }

    @AfterAll
    static void tearDownSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void transformsNameAndDateFieldsAndDropsRawColumns() {
        StructType schema = new StructType()
                .add("customer_id", DataTypes.LongType, false)
                .add("first_name", DataTypes.StringType, false)
                .add("last_name", DataTypes.StringType, false)
                .add("email", DataTypes.StringType, false)
                .add("dob", DataTypes.DateType, false)
                .add("customer_since", DataTypes.DateType, false)
                .add("city", DataTypes.StringType, false)
                .add("country", DataTypes.StringType, false);

        Dataset<Row> input = spark.createDataFrame(List.of(
                RowFactory.create(
                        7L,
                        "Ada",
                        "Lovelace",
                        "ada@example.com",
                        Date.valueOf("1990-12-10"),
                        Date.valueOf("2018-05-14"),
                        "London",
                        "UK")),
                schema);

        Dataset<Row> transformed = CustomerTransform.transform(input, to_date(org.apache.spark.sql.functions.lit("2026-03-06")));
        Row row = transformed.collectAsList().get(0);

        assertEquals(7L, ((Number) row.getAs("customer_id")).longValue());
        assertEquals("Ada Lovelace", row.getAs("fullname"));
        assertEquals("ada@example.com", row.getAs("email"));
        assertEquals(35, ((Number) row.getAs("age")).intValue());
        assertEquals(7, ((Number) row.getAs("customer_tenure_years")).intValue());
        assertEquals("London", row.getAs("city"));
        assertEquals("UK", row.getAs("country"));

        List<String> columns = List.of(transformed.columns());
        assertFalse(columns.contains("first_name"));
        assertFalse(columns.contains("last_name"));
        assertFalse(columns.contains("dob"));
        assertFalse(columns.contains("customer_since"));
    }
}
