package com.example.wallet.service;

import com.example.wallet.api.ApiModels.ReconciliationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReconciliationService {
 private final JdbcTemplate jdbc; public ReconciliationService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional(isolation=Isolation.REPEATABLE_READ)
 public ReconciliationResponse run(){
   try{jdbc.queryForObject("SELECT job_name FROM job_locks WHERE job_name='nightly-reconciliation' FOR UPDATE NOWAIT",String.class);}catch(Exception e){return new ReconciliationResponse(0,"SKIPPED",0,0,"Another reconciliation run holds the job lock");}
   long run=jdbc.queryForObject("INSERT INTO reconciliation_runs(status) VALUES('RUNNING') RETURNING id",Long.class);int checks=0,problems=0;
   checks++; long[] totals=jdbc.queryForObject("SELECT COALESCE(sum(amount_paise) FILTER (WHERE direction='DEBIT'),0),COALESCE(sum(amount_paise) FILTER (WHERE direction='CREDIT'),0) FROM ledger_entries",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)});
   if(totals[0]!=totals[1]){problem(run,"GLOBAL_LEDGER_BALANCE",null,String.valueOf(totals[0]),String.valueOf(totals[1]),"Global debit and credit totals differ");problems++;}
   checks++; var unbalanced=jdbc.query("SELECT t.id,COALESCE(sum(le.amount_paise) FILTER (WHERE le.direction='DEBIT'),0),COALESCE(sum(le.amount_paise) FILTER (WHERE le.direction='CREDIT'),0) FROM transfers t LEFT JOIN ledger_entries le ON le.transfer_id=t.id WHERE t.status='COMPLETED' GROUP BY t.id HAVING COALESCE(sum(le.amount_paise) FILTER (WHERE le.direction='DEBIT'),0)<>COALESCE(sum(le.amount_paise) FILTER (WHERE le.direction='CREDIT'),0) OR count(le.id)=0",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)});
   for(long[] x:unbalanced){problem(run,"TRANSFER_BALANCE",x[0],String.valueOf(x[1]),String.valueOf(x[2]),"Transfer entries do not balance or are missing");problems++;}
   checks++; var mismatches=jdbc.query("SELECT a.id,a.balance_paise,COALESCE(sum(CASE WHEN le.direction='CREDIT' THEN le.amount_paise ELSE -le.amount_paise END),0) computed FROM accounts a LEFT JOIN ledger_entries le ON le.account_id=a.id GROUP BY a.id,a.balance_paise HAVING a.balance_paise<>COALESCE(sum(CASE WHEN le.direction='CREDIT' THEN le.amount_paise ELSE -le.amount_paise END),0)",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)});
   for(long[] x:mismatches){problem(run,"STORED_BALANCE",x[0],String.valueOf(x[2]),String.valueOf(x[1]),"Stored balance differs from ledger");problems++;}
   checks++;var overdrafts=jdbc.query("SELECT a.id,COALESCE(sum(CASE WHEN le.direction='CREDIT' THEN le.amount_paise ELSE -le.amount_paise END),0) computed FROM accounts a LEFT JOIN ledger_entries le ON le.account_id=a.id WHERE a.account_type='WALLET' GROUP BY a.id HAVING COALESCE(sum(CASE WHEN le.direction='CREDIT' THEN le.amount_paise ELSE -le.amount_paise END),0)<0",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)});
   for(long[] x:overdrafts){problem(run,"WALLET_OVERDRAFT",x[0],">=0",String.valueOf(x[1]),"Computed wallet balance is negative");problems++;}
   checks++;var noEntries=jdbc.query("SELECT t.id FROM transfers t LEFT JOIN ledger_entries le ON le.transfer_id=t.id WHERE t.status='COMPLETED' GROUP BY t.id HAVING count(le.id)=0",(rs,n)->rs.getLong(1));for(Long id:noEntries){problem(run,"TRANSFER_WITHOUT_ENTRIES",id,"at least 2 entries","0","Completed transfer has no ledger entries");problems++;}
   jdbc.update("UPDATE reconciliation_runs SET ended_at=now(),status=?,checks_run=?,problems_found=?,details=? WHERE id=?",problems==0?"OK":"MISMATCH",checks,problems,problems==0?"All checks passed":"See reconciliation_problems",run);
   return new ReconciliationResponse(run,problems==0?"OK":"MISMATCH",checks,problems,problems==0?"All checks passed":"See reconciliation_problems" );
 }
 private void problem(long run,String check,Long entity,String expected,String actual,String details){jdbc.update("INSERT INTO reconciliation_problems(run_id,check_name,entity_id,expected_value,actual_value,details) VALUES(?,?,?,?,?,?)",run,check,entity,expected,actual,details);}
}
