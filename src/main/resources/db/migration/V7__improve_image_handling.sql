-- Ensure proper encoding for text
SET client_encoding = 'UTF8';

-- Make sure profile_image column in user_table can properly store binary data
ALTER TABLE user_table ALTER COLUMN profile_image TYPE bytea USING profile_image::bytea;

-- Add index to improve image retrieval performance
CREATE INDEX idx_user_profile_image ON user_table (id) WHERE profile_image IS NOT NULL;

-- Add metadata columns for better image handling if they don't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                  WHERE table_name = 'user_table' AND column_name = 'profile_image_content_type') THEN
        ALTER TABLE user_table ADD COLUMN profile_image_content_type VARCHAR(100);
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                  WHERE table_name = 'user_table' AND column_name = 'profile_image_name') THEN
        ALTER TABLE user_table ADD COLUMN profile_image_name VARCHAR(255);
    END IF;
END $$;

-- Comment for documentation
COMMENT ON COLUMN user_table.profile_image IS 'Binary content of the profile image';
COMMENT ON COLUMN user_table.profile_image_content_type IS 'MIME type of the profile image';
COMMENT ON COLUMN user_table.profile_image_name IS 'Original filename of the profile image';