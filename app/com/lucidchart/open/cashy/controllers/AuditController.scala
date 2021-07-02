package com.lucidchart.open.cashy.controllers

import javax.inject.Inject
import com.lucidchart.open.cashy.config.Buckets
import com.lucidchart.open.cashy.models.AuditModel
import com.lucidchart.open.cashy.request.AuthAction
import com.lucidchart.open.cashy.views
import play.api.mvc.{Action, ControllerComponents, AbstractController}

class AuditController @Inject() (
    authAction: AuthAction,
    auditModel: AuditModel,
    components: ControllerComponents
)(implicit
    buckets: Buckets
) extends AbstractController(components)
    with play.api.i18n.I18nSupport {

  /**
    * Get a page of audit records
    */
  def audit(page: Int) =
    authAction.authenticatedUser { implicit user =>
      Action { implicit request =>
        Ok(views.html.audit.index(auditModel.getAuditPage(page)))
      }
    }
}
