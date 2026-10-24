package com.example.wallet.api;

import com.example.wallet.api.ApiModels.*;import com.example.wallet.service.*;
import jakarta.validation.Valid;import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@RestController @RequestMapping("/api")
public class WalletController {
 private final WalletService service;private final ReconciliationService reconciliation;private final StatementQaService qa;private final JdbcTemplate jdbc;
 public WalletController(WalletService s,ReconciliationService r,StatementQaService q,JdbcTemplate jdbc){service=s;reconciliation=r;qa=q;this.jdbc=jdbc;}
 @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> createUser(@Valid @RequestBody CreateUserRequest req){long id=service.createUser(req);return Map.of("id",id,"name",req.name(),"email",req.email());}
 @PostMapping("/wallets") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> createWallet(@Valid @RequestBody CreateWalletRequest req){long id=service.createWallet(req);return Map.of("id",id,"userId",req.userId(),"name",req.name(),"currency","INR");}
 @GetMapping("/users/{userId}/wallets") public List<BalanceResponse> wallets(@PathVariable long userId){return service.wallets(userId);}
 @GetMapping("/users/{userId}/wallets/{walletId}/balance") public BalanceResponse balance(@PathVariable long userId,@PathVariable long walletId){var b=service.balance(walletId,userId);if(b==null)throw new WalletException("NOT_FOUND","Wallet not found",404);return b;}
 @PostMapping("/users/{userId}/top-ups") public TransferResponse topUp(@PathVariable long userId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody TopUpRequest req){return service.topUp(userId,req,key);}
 @PostMapping("/users/{userId}/withdrawals") public TransferResponse withdraw(@PathVariable long userId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody WithdrawRequest req){return service.withdraw(userId,req,key);}
 @PostMapping("/users/{userId}/transfers") public TransferResponse transfer(@PathVariable long userId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody TransferRequest req){return service.peerTransfer(userId,req,key);}
 @GetMapping("/users/{userId}/wallets/{walletId}/statement") public PageResponse<StatementEntry> statement(@PathVariable long userId,@PathVariable long walletId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return new PageResponse<>(service.statement(userId,walletId,page,size),page,size,service.statementCount(walletId));}
 @PostMapping("/users/{userId}/statement/ask") public AskResponse ask(@PathVariable long userId,@Valid @RequestBody AskRequest req){return qa.ask(userId,req.question());}
 @PostMapping("/admin/reconciliation/run") public ReconciliationResponse reconcile(){return reconciliation.run();}
 @PostMapping("/admin/transfers/{transferId}/review") public TransferResponse review(@PathVariable long transferId,@Valid @RequestBody ReviewRequest req){return service.review(transferId,req);}
 @GetMapping("/admin/reconciliation/runs/{runId}/problems") public List<Map<String,Object>> problems(@PathVariable long runId){return jdbc.queryForList("SELECT check_name,entity_id,expected_value,actual_value,details FROM reconciliation_problems WHERE run_id=? ORDER BY id",runId);}
 @GetMapping("/health") public Map<String,String> health(){return Map.of("status","UP");}
}
