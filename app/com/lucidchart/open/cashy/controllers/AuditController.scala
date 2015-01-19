package com.lucidchart.open.cashy.controllers

import com.lucidchart.open.cashy.models.AuditModel
import com.lucidchart.open.cashy.request.AuthAction
import com.lucidchart.open.cashy.views
import play.api.mvc.Action

object AuditController extends AuditController
class AuditController extends AppController {

  /**
   * Get a page of audit records
   */
  def audit(page: Int) = AuthAction.authenticatedUser { implicit user =>
    Action { implicit request =>
      Ok(views.html.audit.index(AuditModel.getAuditPage(page)))
    }
  }
}
