package org.exoplatform.editors.portlet;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.portlet.GenericPortlet;
import javax.portlet.PortletException;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.JavascriptManager;
import org.exoplatform.webui.application.WebuiRequestContext;

public class EditorsAdminPortlet extends GenericPortlet {

  /** The Constant LOG. */
  private static final Log LOG = ExoLogger.getLogger(EditorsAdminPortlet.class);
  
  protected static final String ERROR_RESOURCE_BASE = "editors.admin.error";

  @Override
  public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
    try {
      PortletRequestDispatcher prDispatcher = getPortletContext().getRequestDispatcher("/WEB-INF/pages/editors-admin.jsp");
      prDispatcher.include(request, response);

      String providersUrl = buildRestUrl(request.getScheme(),
                                         request.getServerName(),
                                         request.getServerPort(),
                                         "/documents/editors");
      String identitiesUrl = buildRestUrl(request.getScheme(),
                                         request.getServerName(),
                                         request.getServerPort(),
                                         "/identity/search");
      String settingsJson = new StringBuilder().append("{\"services\": {\"providers\": \"")
                                               .append(providersUrl)
                                               .append("\",")
                                               .append("\"identities\": \"")
                                               .append(identitiesUrl)
                                               .append("\",")
                                               .append("\"errorResourceBase\": \"")
                                               .append(ERROR_RESOURCE_BASE)
                                               .append("\"}}")
                                               .toString();

      JavascriptManager js = ((WebuiRequestContext) WebuiRequestContext.getCurrentInstance()).getJavascriptManager();
      js.require("SHARED/editorsadmin", "editorsadmin").addScripts("editorsadmin.init(" + settingsJson + ");");
    } catch (Exception e) {
      LOG.error("Error processing editors admin portlet for user " + request.getRemoteUser(), e);
    }
  }

  private String buildRestUrl(String protocol, String hostname, int port, String path) throws MalformedURLException {
    return new URL(protocol,
                   hostname,
                   port,
                   new StringBuilder("/").append(PortalContainer.getCurrentRestContextName()).append(path).toString()).toString();
  }
}
