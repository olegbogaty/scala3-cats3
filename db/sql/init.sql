CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    account_id INT UNIQUE NOT NULL,
    bank_code INT NOT NULL,
    balance NUMERIC(18, 2) DEFAULT 0
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

CREATE TABLE IF NOT EXISTS transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    account_id INT NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    status VARCHAR(9) NOT NULL,
    recipient_account INT NOT NULL,
    recipient_bank_code INT NOT NULL,
    transaction_reference VARCHAR UNIQUE NOT NULL,
    transfer_date TIMESTAMP NOT NULL DEFAULT now (),
    CHECK(status in ('STARTED','RUNNING','COMPLETED','FAILED'))
);

INSERT INTO transfers (
    account_id,
    amount,
  	status,
  	recipient_account,
  	recipient_bank_code,
  	transaction_reference
) values (
    1111,
    500,
    'STARTED',
    4444,
    5555,
    'unique_transaction_reference'
);