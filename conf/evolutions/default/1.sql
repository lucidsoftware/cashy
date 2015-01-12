# patch 1
# initial setup - dashboards

# --- !Ups

CREATE TABLE `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `google_id` varchar(25) NOT NULL COMMENT 'Google sometimes refers to this as sub',
  `email` varchar(80) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

 CREATE TABLE `stored_credentials` (
  `key` varchar(255) NOT NULL,
  `value` blob,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;


# --- !Downs

DROP TABLE `users`;
DROP TABLE `stored_credentials`;
