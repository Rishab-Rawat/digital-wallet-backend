package com.example.wallet.service;

import com.example.wallet.api.ApiModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Service
public class WalletService {
  private final JdbcTemplate jdbc; private final FraudService fraud; private final ObjectMapper mapper;
  public WalletService(JdbcTemplate jdbc,FraudService fraud,ObjectMapper mapper){this.jdbc=jdbc;this.fraud=fraud;this.mapper=mapper;}
  public long createUser(CreateUserRequest r){try{jdbc.update("INSERT INTO users(name,email,phone) VALUES(?,?,?)",r.name(),r.email().toLowerCase(Locale.ROOT),r.phone());return jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,r.email().toLowerCase(Locale.ROOT));}catch(DuplicateKeyException e){throw new WalletException("USER_EXISTS","Email or phone is already registered",409);}}
  public long createWallet(CreateWalletRequest r){if(jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?",Integer.class,r.userId())==0)throw notFound("User not found");jdbc.update("INSERT INTO accounts(user_id,name,account_type,currency,balance_paise,is_system) VALUES(?,?,'WALLET','INR',0,false)",r.userId(),r.name());return jdbc.queryForObject("SELECT id FROM accounts WHERE user_id=? ORDER BY id DESC LIMIT 1",Long.class,r.userId());}
  public List<BalanceResponse> wallets(long userId){return jdbc.query("SELECT id,user_id,name,balance_paise,currency FROM accounts WHERE user_id=? AND account_type='WALLET' ORDER BY id",(rs,n)->new BalanceResponse(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5)),userId);}
  public BalanceResponse balance(long accountId,long userId){return jdbc.query("SELECT id,user_id,name,balance_paise,currency FROM accounts WHERE id=? AND user_id=? AND account_type='WALLET'",rs->rs.next()?new BalanceResponse(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5)):null,accountId,userId)==null?null:jdbc.queryForObject("SELECT id,user_id,name,balance_paise,currency FROM accounts WHERE id=? AND user_id=?",(rs,n)->new BalanceResponse(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5)),accountId,userId);}
  public List<StatementEntry> statement(long userId,long accountId,int page,int size){if(size<1||size>200||page<0)throw new WalletException("INVALID_PAGINATION","page must be >= 0 and size must be 1..200",400);if(!owns(userId,accountId))throw notFound("Wallet not found");return jdbc.query("SELECT le.id,le.transfer_id,le.account_id,le.direction,le.amount_paise,t.transfer_type,t.status,t.note,le.created_at FROM ledger_entries le JOIN transfers t ON t.id=le.transfer_id WHERE le.account_id=? ORDER BY le.created_at DESC,le.id DESC LIMIT ? OFFSET ?",statementMapper(),accountId,size,page*size);}
  public long statementCount(long accountId){return jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id=?",Long.class,accountId);}
  public boolean owns(long userId,long accountId){return jdbc.queryForObject("SELECT count(*) FROM accounts WHERE id=? AND user_id=? AND account_type='WALLET'",Integer.class,accountId,userId)>0;}
  private RowMapper<StatementEntry> statementMapper(){return (rs,n)->new StatementEntry(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant());}
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public TransferResponse topUp(long userId,TopUpRequest req,String key){long cashIn=systemAccount("CASH_IN");return createIdempotent(userId,key,req.toString(),()->performTransfer("TOP_UP",cashIn,req.walletId(),req.amountPaise(),req.note(),null));}
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public TransferResponse withdraw(long userId,WithdrawRequest req,String key){long cashOut=systemAccount("CASH_OUT");return createIdempotent(userId,key,req.toString(),()->performTransfer("WITHDRAWAL",req.walletId(),cashOut,req.amountPaise(),req.note(),userId));}
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public TransferResponse peerTransfer(long userId,TransferRequest req,String key){if(req.fromWalletId().equals(req.toWalletId()))throw new WalletException("SAME_ACCOUNT","Source and destination must differ",422);if(!owns(userId,req.fromWalletId()))throw notFound("Source wallet not found");return createIdempotent(userId,key,req.toString(),()->performTransfer("PEER_TRANSFER",req.fromWalletId(),req.toWalletId(),req.amountPaise(),req.note(),userId));}
  private TransferResponse createIdempotent(long userId,String key,String body,java.util.function.Supplier<TransferResponse> action){
    if(key==null||key.isBlank())throw new WalletException("IDEMPOTENCY_KEY_REQUIRED","Idempotency-Key header is required",400);
    String hash=Hashing.sha256(body);
    int inserted=jdbc.update("INSERT INTO idempotency_keys(user_id,idempotency_key,request_hash,status) VALUES(?,?,?,'PROCESSING') ON CONFLICT(user_id,idempotency_key) DO NOTHING",userId,key,hash);
    if(inserted==0){var prior=jdbc.query("SELECT request_hash,status,response_code,response_body::text FROM idempotency_keys WHERE user_id=? AND idempotency_key=?",rs->rs.next()?new Prior(rs.getString(1),rs.getString(2),rs.getObject(3)==null?null:rs.getInt(3),rs.getString(4)):null,userId,key);if(prior==null)throw new WalletException("IDEMPOTENCY_CONFLICT","Request is already processing; retry shortly",409);if(!prior.hash.equals(hash))throw new WalletException("IDEMPOTENCY_KEY_REUSED","Same idempotency key used with a different request body",422);if("COMPLETED".equals(prior.status)&&prior.body!=null){try{return mapper.readValue(prior.body,TransferResponse.class);}catch(Exception e){throw new WalletException("IDEMPOTENCY_REPLAY_ERROR","Saved response could not be read",500);}}throw new WalletException("IDEMPOTENCY_IN_PROGRESS","Request with this key is in progress",409);}
    TransferResponse response=action.get();try{String json=mapper.writeValueAsString(response);jdbc.update("UPDATE idempotency_keys SET status='COMPLETED',response_code=200,response_body=?::jsonb WHERE user_id=? AND idempotency_key=?",json,userId,key);}catch(JsonProcessingException e){throw new IllegalStateException(e);}return response;
  }
  private record Prior(String hash,String status,Integer code,String body){}
  @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
  public TransferResponse performTransfer(String type,long from,long to,long amount,String note,Long actingUser){
    if(amount<=0)throw new WalletException("INVALID_AMOUNT","amountPaise must be positive",400);
    List<Account> accounts=jdbc.query("SELECT id,user_id,account_type,balance_paise,name FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",(rs,n)->new Account(rs.getLong(1),(Long)rs.getObject(2),rs.getString(3),rs.getLong(4),rs.getString(5)),from,to);
    if(accounts.size()!=2)throw notFound("Account not found");Account src=accounts.stream().filter(a->a.id==from).findFirst().orElseThrow();Account dst=accounts.stream().filter(a->a.id==to).findFirst().orElseThrow();
    if("WALLET".equals(src.type)&&src.balance<amount)throw new WalletException("INSUFFICIENT_FUNDS","Wallet has insufficient funds",422);
    if("WALLET".equals(dst.type)&&dst.userId==null)throw new WalletException("INVALID_ACCOUNT","Wallet account is invalid",422);
    FraudService.Decision decision=fraud.evaluate(from,amount);
    String status=decision.held()?"HELD":"COMPLETED";
    jdbc.update("INSERT INTO transfers(transfer_type,status,amount_paise,from_account_id,to_account_id,note,risk_flag,completed_at) VALUES(?,?,?,?,?,?,?,CASE WHEN ?='COMPLETED' THEN now() ELSE NULL END)",type,status,amount,from,to,note,decision.flagged(),status);
    long transferId=jdbc.queryForObject("SELECT currval(pg_get_serial_sequence('transfers','id'))",Long.class);
    audit(transferId,null,status,actingUser==null?"system":"user:"+actingUser,decision.message());
    if(!decision.held()){
      jdbc.update("INSERT INTO ledger_entries(transfer_id,account_id,direction,amount_paise) VALUES(?,?,'DEBIT',?),(?,?,'CREDIT',?)",transferId,from,amount,transferId,to,amount);
      jdbc.update("UPDATE accounts SET balance_paise=balance_paise-? WHERE id=?",amount,from);
      jdbc.update("UPDATE accounts SET balance_paise=balance_paise+? WHERE id=?",amount,to);
    }
    for(String rule:decision.rules())jdbc.update("INSERT INTO risk_flags(transfer_id,rule_code,explanation,label) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",transferId,rule,decision.message(),decision.held()?"review":"review");
    Instant created=jdbc.queryForObject("SELECT created_at FROM transfers WHERE id=?",(rs,n)->rs.getTimestamp(1).toInstant(),transferId);
    return new TransferResponse(transferId,status,amount,"INR",decision.flagged(),decision.held()?"Held for manual review: "+decision.message():decision.message(),created);
  }
  @Transactional
  public TransferResponse review(long transferId,ReviewRequest req){if(!Set.of("RELEASE","REJECT").contains(req.action().toUpperCase(Locale.ROOT)))throw new WalletException("INVALID_ACTION","action must be RELEASE or REJECT",400);return reviewTx(transferId,req);}
  @Transactional
  protected TransferResponse reviewTx(long transferId,ReviewRequest req){var t=jdbc.query("SELECT status,from_account_id,to_account_id,amount_paise,transfer_type,note,risk_flag,created_at FROM transfers WHERE id=? FOR UPDATE",rs->rs.next()?new TransferRow(rs.getString(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getString(5),rs.getString(6),rs.getBoolean(7),rs.getTimestamp(8).toInstant()):null,transferId);if(t==null)throw notFound("Transfer not found");if(!"HELD".equals(t.status))throw new WalletException("TRANSFER_NOT_HELD","Only held transfers can be reviewed",409);
    if("REJECT".equalsIgnoreCase(req.action())){jdbc.update("UPDATE transfers SET status='REJECTED' WHERE id=?",transferId);audit(transferId,"HELD","REJECTED",req.changedBy(),req.reason());return new TransferResponse(transferId,"REJECTED",t.amount,"INR",true,"Transfer rejected",t.created);}
    List<Account> accounts=jdbc.query("SELECT id,user_id,account_type,balance_paise,name FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",(rs,n)->new Account(rs.getLong(1),(Long)rs.getObject(2),rs.getString(3),rs.getLong(4),rs.getString(5)),t.from,t.to);Account src=accounts.stream().filter(a->a.id==t.from).findFirst().orElseThrow();if("WALLET".equals(src.type)&&src.balance<t.amount)throw new WalletException("INSUFFICIENT_FUNDS","Source wallet no longer has sufficient funds",422);
    jdbc.update("INSERT INTO ledger_entries(transfer_id,account_id,direction,amount_paise) VALUES(?,?,'DEBIT',?),(?,?,'CREDIT',?)",transferId,t.from,t.amount,transferId,t.to,t.amount);jdbc.update("UPDATE accounts SET balance_paise=balance_paise-? WHERE id=?",t.amount,t.from);jdbc.update("UPDATE accounts SET balance_paise=balance_paise+? WHERE id=?",t.amount,t.to);jdbc.update("UPDATE transfers SET status='COMPLETED',completed_at=now() WHERE id=?",transferId);audit(transferId,"HELD","COMPLETED",req.changedBy(),req.reason());return new TransferResponse(transferId,"COMPLETED",t.amount,"INR",true,"Transfer released by reviewer",t.created);
  }
  private record TransferRow(String status,long from,long to,long amount,String type,String note,boolean flag,Instant created){}
  private record Account(long id,Long userId,String type,long balance,String name){}
  private void audit(long id,String oldStatus,String newStatus,String by,String reason){jdbc.update("INSERT INTO transfer_status_audit(transfer_id,old_status,new_status,changed_by,reason) VALUES(?,?,?,?,?)",id,oldStatus,newStatus,by,reason);}
  private long systemAccount(String type){Long id=jdbc.query("SELECT id FROM accounts WHERE account_type=? AND is_system=true ORDER BY id LIMIT 1",rs->rs.next()?rs.getLong(1):null,type);if(id==null)throw new IllegalStateException("Missing system account: "+type);return id;}
  private WalletException notFound(String msg){return new WalletException("NOT_FOUND",msg,404);}
}
