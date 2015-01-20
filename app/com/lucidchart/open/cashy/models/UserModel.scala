package com.lucidchart.open.cashy.models

import play.api.Play.current
import play.api.db._
import com.lucidchart.open.relate.interp._
import com.lucidchart.open.relate.SqlResult

case class User(
  id: Long,
  googleId: String,
  email: String
)

object UserModel extends UserModel
class UserModel {

  private val userParser = { row: SqlResult =>
    User(
      row.long("id"),
      row.string("google_id"),
      row.string("email")
    )
  }

  def findById(userId: Long): Option[User] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `id` = $userId""".asSingleOption(userParser)
    }
  }

  def findByIds(userIds: List[Long]): List[User] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `id` IN ($userIds)""".asList(userParser)
    }
  }


  def findByGoogleId(googleId: String): Option[User] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `google_id` = $googleId""".asSingleOption(userParser)
    }
  }

  def createUser(googleId: String, email: String): User = {
    DB.withConnection { implicit connection =>
      sql"""INSERT INTO `users`
        (`google_id`, `email`)
        VALUES ($googleId, $email)""".execute()
      findByGoogleId(googleId).getOrElse {
        throw new Exception("Mysql insert failed")
      }
    }
  }

}