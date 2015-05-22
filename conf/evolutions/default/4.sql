# patch 4
# use md5 hash keys on assets

# --- !Ups

ALTER TABLE `assets` ADD COLUMN `bucket_hash` binary(8) NOT NULL;
ALTER TABLE `assets` ADD COLUMN `key_hash` binary(8) NOT NULL;
UPDATE `assets` SET `bucket_hash` = LOWER(HEX(SUBSTRING(UNHEX(MD5(`bucket`)),1,4)));
UPDATE `assets` SET `key_hash` = LOWER(HEX(SUBSTRING(UNHEX(MD5(`key`)),1,4)));
ALTER TABLE `assets` DROP COLUMN `id`;
ALTER TABLE `assets` ADD PRIMARY KEY(`bucket_hash`, `key_hash`);

# --- !Downs

ALTER TABLE `assets` DROP COLUMN `bucket_hash`;
ALTER TABLE `assets` DROP COLUMN `key_hash`;
ALTER TABLE `assets` ADD COLUMN `id` bigint NOT NULL AUTO_INCREMENT FIRST, ADD PRIMARY KEY(`id`);
