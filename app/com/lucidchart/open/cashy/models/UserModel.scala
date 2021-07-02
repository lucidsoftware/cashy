package com.lucidchart.open.cashy.models

import javax.inject.Inject
import play.api.db.Database
import com.lucidchart.relate._

case class User(
    id: Long,
    googleId: String,
    email: String
)

class UserModel @Inject() (db: Database) {

  private val userParser = { row: SqlRow =>
    User(
      row.long("id"),
      row.string("google_id"),
      row.string("email")
    )
  }

  def findById(userId: Long): Option[User] = {
    db.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `id` = $userId""".asSingleOption(userParser)
    }
  }

  def findByIds(userIds: List[Long]): List[User] = {
    db.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `id` IN ($userIds)""".asList(userParser)
    }
  }

  def findByGoogleId(googleId: String): Option[User] = {
    db.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `google_id` = $googleId""".asSingleOption(userParser)
    }
  }

  def createUser(googleId: String, email: String): User = {
    db.withConnection { implicit connection =>
      sql"""INSERT INTO `users`
        (`google_id`, `email`)
        VALUES ($googleId, $email)""".execute()
      findByGoogleId(googleId).getOrElse {
        throw new Exception("Mysql insert failed")
      }
    }
  }

}
