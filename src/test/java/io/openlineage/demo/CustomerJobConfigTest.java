package io.openlineage.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerJobConfigTest {
    @Test
    void usesSeaweedFsDefaultsForObjectStorage() {
        CustomerJobConfig config = CustomerJobConfig.fromArgs(new String[0]);

        assertEquals("s3a://customer-lake/customers_curated/", config.outputPath());
        assertEquals("http://seaweedfs-s3:8333", config.s3Endpoint());
        assertEquals("seaweedfsadmin", config.s3AccessKey());
        assertEquals("seaweedfsadmin123", config.s3SecretKey());
    }
}
