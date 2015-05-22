# patch 3
# hidden assets and folders

# --- !Ups

ALTER TABLE `assets` ADD COLUMN `hidden` tinyint(1) NOT NULL DEFAULT 0;

CREATE TABLE `folders` (
  `bucket` varchar(100) NOT NULL,
  `bucket_hash` binary(8) NOT NULL,
  `key` varchar(255) NOT NULL,
  `key_hash` binary(8) NOT NULL,
  `created` datetime NOT NULL,
  `hidden` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`bucket_hash`,`key_hash`),
  INDEX `hidden` (`hidden`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

# --- !Downs

ALTER TABLE `assets` DROP COLUMN `hidden`;
DROP TABLE `folders`