-- Seed wallets for testing and development
INSERT INTO wallets (id, balance, version, created_at, updated_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 10000, 0, now(), now()),
    ('22222222-2222-2222-2222-222222222222', 5000, 0, now(), now()),
    ('33333333-3333-3333-3333-333333333333', 0, 0, now(), now());
