package com.example.wallet.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FraudService {
  public record Decision(boolean held, boolean flagged, List<String> rules, String message) {}
  private final JdbcTemplate jdbc;
  public FraudService(JdbcTemplate jdbc){this.jdbc=jdbc;}
  public Decision evaluate(long fromAccountId, long amountPaise) {
    boolean held=false, flagged=false; List<String> fired=new ArrayList<>();
    var rules=jdbc.query("SELECT rule_code,threshold_paise,window_minutes,action FROM risk_rules WHERE enabled=true ORDER BY id", (rs,n)->new Rule(rs.getString(1),(Long)rs.getObject(2),(Integer)rs.getObject(3),rs.getString(4)));
    for(Rule r:rules){
      boolean hit=false;
      if(r.code.equals("LARGE_TRANSFER") && r.threshold!=null && amountPaise>r.threshold) hit=true;
      if(r.code.equals("VELOCITY_10M") && r.window!=null){ Integer count=jdbc.queryForObject("SELECT count(*) FROM transfers WHERE from_account_id=? AND created_at >= now() - (? * interval '1 minute') AND status IN ('COMPLETED','HELD')",Integer.class,fromAccountId,r.window); hit=count!=null&&count>=5; }
      if(hit){fired.add(r.code); if("HOLD".equals(r.action))held=true;else flagged=true;}
    }
    return new Decision(held,flagged||held,fired,fired.isEmpty()?"No fraud rules triggered":"Flagged by: "+String.join(", ",fired));
  }
  private record Rule(String code,Long threshold,Integer window,String action){}
}
