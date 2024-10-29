CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    account_id INT NOT NULL,
    bank_code INT NOT NULL,
    balance NUMERIC(18, 2) DEFAULT 0,
    UNIQUE (account_id, bank_code)
);

INSERT INTO accounts (
    account_id,
    bank_code,
  	balance
) values (
    1111,
    2222,
    1000
), (
    4444,
    5555,
    2000
);
