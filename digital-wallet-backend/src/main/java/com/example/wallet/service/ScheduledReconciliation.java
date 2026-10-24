package com.example.wallet.service;
import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;
@Component public class ScheduledReconciliation {private final ReconciliationService service;public ScheduledReconciliation(ReconciliationService service){this.service=service;}@Scheduled(cron="${wallet.reconciliation-cron:0 0 2 * * *}") public void nightly(){service.run();}}
