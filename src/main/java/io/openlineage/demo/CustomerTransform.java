package io.openlineage.demo;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.concat_ws;
import static org.apache.spark.sql.functions.current_date;
import static org.apache.spark.sql.functions.floor;
import static org.apache.spark.sql.functions.months_between;

final class CustomerTransform {
    private CustomerTransform() {
    }

    static Dataset<Row> transform(Dataset<Row> customers) {
        return transform(customers, current_date());
    }

    static Dataset<Row> transform(Dataset<Row> customers, Column referenceDate) {
        return customers.select(
                col("customer_id"),
                concat_ws(" ", col("first_name"), col("last_name")).alias("fullname"),
                col("email"),
                floor(months_between(referenceDate, col("dob")).divide(12)).cast("int").alias("age"),
                floor(months_between(referenceDate, col("customer_since")).divide(12))
                        .cast("int")
                        .alias("customer_tenure_years"),
                col("city"),
                col("country"));
    }
}

