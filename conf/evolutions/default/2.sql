# patch 2
# assets and audits

# --- !Ups

CREATE TABLE `assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bucket` varchar(100) NOT NULL,
  `key` varchar(255) NOT NULL,
  `user_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`user_id`) REFERENCES users(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `audits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data` text NOT NULL,
  `user_id` bigint NOT NULL,
  `created` datetime NOT NULL,
  `type` tinyint(3) NOT NULL,
  PRIMARY KEY (`id`),
  FOREIGN KEY (`user_id`) REFERENCES users(`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;


# --- !Downs

DROP TABLE `assets`;
DROP TABLE `audits`;
