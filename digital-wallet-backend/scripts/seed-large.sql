-- Optional scale seed for a disposable database only. Run after demo system accounts exist.
-- Creates 1,000 users and 2,000 wallets. Does not create 100,000 fully balanced transfers;
-- use a generated API/load-test script so each transfer follows the production transaction path.
INSERT INTO users(name,email,phone,kyc_status)
SELECT 'Seed User '||g, 'seed'||g||'@example.test', '+1999'||lpad(g::text,7,'0'), 'VERIFIED'
FROM generate_series(1,1000) g ON CONFLICT(email) DO NOTHING;
INSERT INTO accounts(user_id,name,account_type,is_system,balance_paise)
SELECT u.id, CASE WHEN x.n=1 THEN 'Main Wallet' ELSE 'Savings Wallet' END, 'WALLET', false, 0
FROM users u CROSS JOIN (VALUES(1),(2)) x(n)
WHERE u.email LIKE 'seed%@example.test' AND NOT EXISTS(SELECT 1 FROM accounts a WHERE a.user_id=u.id AND a.name=CASE WHEN x.n=1 THEN 'Main Wallet' ELSE 'Savings Wallet' END);
