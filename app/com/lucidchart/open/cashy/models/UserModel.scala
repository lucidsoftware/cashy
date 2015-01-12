package com.lucidchart.open.cashy.models

import play.api.Play.current
import play.api.db._
import com.lucidchart.open.relate.interp._

case class User(
  id: Long,
  googleId: String,
  email: String
)

object UserModel extends UserModel
class UserModel {

  def findById(userId: Long): Option[User] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `id` = $userId""".asSingleOption { row =>
          User(row.long("id"), row.string("google_id"), row.string("email"))
      }
    }
  }


  def findByGoogleId(googleId: String): Option[User] = {
    DB.withConnection { implicit connection =>
      sql"""SELECT `id`, `google_id`, `email`
        FROM `users`
        WHERE `google_id` = $googleId""".asSingleOption { row =>
          User(row.long("id"), row.string("google_id"), row.string("email"))
      }
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