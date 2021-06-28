package com.lucidchart.open.cashy.controllers

import javax.inject.Inject
import com.lucidchart.open.cashy.models.AuditModel
import com.lucidchart.open.cashy.request.AuthAction
import com.lucidchart.open.cashy.views
import play.api.i18n.MessagesApi
import play.api.mvc.Action

class AuditController @Inject() (val messagesApi: MessagesApi) extends AppController with play.api.i18n.I18nSupport {

  /**
   * Get a page of audit records
   */
  def audit(page: Int) = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      Ok(views.html.audit.index(AuditModel.getAuditPage(page)))
    }
  }
}
