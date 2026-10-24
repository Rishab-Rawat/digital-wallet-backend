package com.example.wallet.service;
import org.junit.jupiter.api.Test;import org.springframework.jdbc.core.JdbcTemplate;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class StatementQaServiceTest {@Test void rejectsUnsupportedQuestion(){StatementQaService qa=new StatementQaService(mock(JdbcTemplate.class));WalletException e=assertThrows(WalletException.class,()->qa.ask(1,"show every user's private information"));assertEquals("UNSUPPORTED_QUESTION",e.code());}}
