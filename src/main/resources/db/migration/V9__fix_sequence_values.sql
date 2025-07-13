-- Reset sequences to the correct values based on existing data
-- This prevents primary key conflicts when inserting new records

-- For user_table
SELECT setval('user_table_id_seq', COALESCE((SELECT MAX(id) FROM user_table), 1), true);

-- For adress table
SELECT setval('adress_id_seq', COALESCE((SELECT MAX(id) FROM adress), 1), true);

-- For service_table
SELECT setval('service_table_seq', COALESCE((SELECT MAX(id) FROM service_table), 1), true);

-- For category
SELECT setval('category_seq', COALESCE((SELECT MAX(id) FROM category), 1), true);

-- For subcategory
SELECT setval('subcategory_seq', COALESCE((SELECT MAX(id) FROM subcategory), 1), true);

-- For image
SELECT setval('image_id_seq', COALESCE((SELECT MAX(id) FROM image), 1), true);

-- For consultation
SELECT setval('consultation_id_seq', COALESCE((SELECT MAX(id) FROM consultation), 1), true);

-- For keyword_table
SELECT setval('keyword_table_id_seq', COALESCE((SELECT MAX(id) FROM keyword_table), 1), true);

-- For links
SELECT setval('links_id_seq', COALESCE((SELECT MAX(id) FROM links), 1), true);

-- For search_history
SELECT setval('search_history_id_seq', COALESCE((SELECT MAX(id) FROM search_history), 1), true);

-- For search_result
SELECT setval('search_result_id_seq', COALESCE((SELECT MAX(id) FROM search_result), 1), true);

-- For token
SELECT setval('token_seq', COALESCE((SELECT MAX(id) FROM token), 1), true);

-- For verification_token
SELECT setval('verification_token_id_seq', COALESCE((SELECT MAX(id) FROM verification_token), 1), true);