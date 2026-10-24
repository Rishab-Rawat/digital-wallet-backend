package com.example.wallet.service;

import com.example.wallet.api.ApiModels.AskResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

@Service
public class StatementQaService {
 private final JdbcTemplate jdbc; public StatementQaService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 // Deterministic mock parser. Replace behind this boundary with a JSON-only LLM provider; SQL remains parameterized and caller-scoped.
 public AskResponse ask(long userId,String question){
   String q=question.toLowerCase(Locale.ROOT);String kind;
   if(q.contains("how much")||q.contains("total"))kind=q.contains("received")?"RECEIVED":"SENT";
   else if(q.contains("largest")||q.contains("biggest"))kind="LARGEST";
   else if(q.contains("how many")||q.contains("number of"))kind="COUNT";
   else throw new WalletException("UNSUPPORTED_QUESTION","I can only answer total sent, total received, largest transfer, or transfer count in a date range.",422);
   LocalDate from=parseDate(q,"from ",LocalDate.now().withDayOfMonth(1));LocalDate to=parseDate(q,"to ",LocalDate.now().plusDays(1));if(to.isBefore(from))throw new WalletException("INVALID_DATE_RANGE","The end date must be on or after the start date",422);
   String person=extractPerson(question);
   String direction=kind.equals("RECEIVED")?"CREDIT":"DEBIT";
   String base="SELECT t.id,t.amount_paise FROM transfers t JOIN ledger_entries le ON le.transfer_id=t.id JOIN accounts a ON a.id=le.account_id WHERE a.user_id=? AND le.direction=? AND t.status='COMPLETED' AND le.created_at>=? AND le.created_at<?";
   List<Object> args=new ArrayList<>(List.of(userId,direction,java.sql.Date.valueOf(from),java.sql.Date.valueOf(to.plusDays(1))));
   if(person!=null&&!person.isBlank()){base+=" AND EXISTS (SELECT 1 FROM ledger_entries other JOIN accounts oa ON oa.id=other.account_id JOIN users u ON u.id=oa.user_id WHERE other.transfer_id=t.id AND u.name ILIKE ?)";args.add("%"+person+"%");}
   base+=" GROUP BY t.id,t.amount_paise";
   List<long[]> rows=jdbc.query(base,(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},args.toArray());long amount=0;List<Long> ids=new ArrayList<>();
   if("LARGEST".equals(kind)){rows.sort((a,b)->Long.compare(b[1],a[1]));if(!rows.isEmpty()){amount=rows.get(0)[1];ids.add(rows.get(0)[0]);}}
   else {for(long[] row:rows){amount+=row[1];ids.add(row[0]);}}
   String answer=switch(kind){case "COUNT"->"There were "+ids.size()+" matching transfers.";case "LARGEST"->rows.isEmpty()?"No matching transfers found.":"The largest matching transfer was "+amount+" paise (transfer "+ids.get(0)+").";default->"The matching total was "+amount+" paise across "+ids.size()+" transfers.";};
   return new AskResponse(answer,amount,ids,"type="+kind+", from="+from+", to="+to+(person==null?"":", counterparty="+person));
 }
 private LocalDate parseDate(String q,String marker,LocalDate fallback){int i=q.indexOf(marker);if(i<0)return fallback;Matcher m=Pattern.compile("(\d{4}-\d{2}-\d{2})").matcher(q.substring(i+marker.length()));if(m.find())try{return LocalDate.parse(m.group(1));}catch(Exception ignored){}return fallback;}
 private String extractPerson(String q){Matcher m=Pattern.compile("(?:to|from|with)\s+([A-Z][a-zA-Z]+(?:\s+[A-Z][a-zA-Z]+)?)").matcher(q);return m.find()?m.group(1):null;}
}
