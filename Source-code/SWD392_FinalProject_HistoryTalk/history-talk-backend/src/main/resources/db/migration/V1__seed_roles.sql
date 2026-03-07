
INSERT INTO "user" (uid, email, password, role, user_name)
VALUES
    (
        gen_random_uuid(),
        'customer@historytalk.com',
        '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', -- Hash của chữ '123456789'
        'CUSTOMER',
        'CUSTOMER1'
     ),
    (
        gen_random_uuid(),
        'user@historytalk.com',
        '$2a$10$dKXT/D53pTf3OjnpWwKtOuAf3HWkEL6qggrPVpRz3wuRa.unajzPy', -- Hash của chữ '123456789'
        'STAFF',
        'STAFF1'
    )
    ON CONFLICT (email) DO NOTHING;