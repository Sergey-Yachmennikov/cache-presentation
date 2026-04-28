CREATE TABLE coupons (
    id       BIGSERIAL    PRIMARY KEY,
    code     VARCHAR(50)  NOT NULL UNIQUE,
    discount NUMERIC(5,2) NOT NULL
);

INSERT INTO coupons (code, discount) VALUES
    ('SAVE10',  10.00),
    ('SAVE20',  20.00),
    ('WELCOME', 15.00);
