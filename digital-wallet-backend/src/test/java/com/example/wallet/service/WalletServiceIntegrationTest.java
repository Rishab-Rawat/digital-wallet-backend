package com.example.wallet.service;

import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.test.context.DynamicPropertyRegistry;import org.springframework.test.context.DynamicPropertySource;import org.testcontainers.containers.PostgreSQLContainer;import org.testcontainers.junit.jupiter.Container;import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @SpringBootTest
class WalletServiceIntegrationTest {
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("wallet").withUsername("wallet").withPassword("wallet");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
 @Autowired JdbcTemplate jdbc;@Autowired WalletService service;
 @Test void schemaHasAppendOnlyLedgerTrigger(){Integer n=jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname='ledger_no_update_delete'",Integer.class);assertEquals(1,n);}
}
