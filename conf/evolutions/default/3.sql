# patch 2
# assets and audits

# --- !Ups

ALTER TABLE `assets` ADD COLUMN `hidden` tinyint(1) NOT NULL DEFAULT 0;

CREATE TABLE `folders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bucket` varchar(100) NOT NULL,
  `key` varchar(255) NOT NULL,
  `created` datetime NOT NULL,
  `hidden` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

# --- !Downs

ALTER TABLE `assets` DROP COLUMN `hidden`;
DROP TABLE `folders`