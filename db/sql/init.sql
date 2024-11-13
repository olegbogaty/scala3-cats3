CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    account_id INT UNIQUE NOT NULL,
    bank_code INT NOT NULL,
    balance NUMERIC DEFAULT 0
);

CREATE TABLE IF NOT EXISTS transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    account_id INT NOT NULL,
    amount NUMERIC NOT NULL,
    status VARCHAR NOT NULL,
    recipient_account INT NOT NULL,
    recipient_bank_code INT NOT NULL,
    transaction_reference VARCHAR UNIQUE NOT NULL,
    transfer_date TIMESTAMP NOT NULL DEFAULT now (),
    CHECK(status in ('PENDING','SUCCESS','FAILURE'))
);

-- Pre-populate any necessary data via SQL scripts.
INSERT INTO accounts (
    account_id,
    bank_code,
  	balance
) VALUES (
    1111,
    9999,
    2000
), (
    2222,
    9999,
    1000
);

INSERT INTO transfers (
    account_id,
    amount,
  	status,
  	recipient_account,
  	recipient_bank_code,
  	transaction_reference
) VALUES (
    1111,
    10.5,
    'PENDING',
    2222,
    9999,
    'unique_transaction_reference'
);