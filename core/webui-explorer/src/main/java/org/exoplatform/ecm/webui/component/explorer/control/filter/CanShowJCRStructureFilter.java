/*
 * Copyright (C) 2018 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ecm.webui.component.explorer.control.filter;

import org.exoplatform.services.jcr.impl.core.NodeImpl;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.ext.filter.UIExtensionFilter;
import org.exoplatform.webui.ext.filter.UIExtensionFilterType;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import java.util.Map;

public class CanShowJCRStructureFilter implements UIExtensionFilter {
 private static final Log LOG = ExoLogger.getLogger(CanShowJCRStructureFilter.class);

 public boolean accept(Map<String, Object> context) throws Exception {
   if (context == null) {
     return true;
   }

   Node currentNode = (Node) context.get(Node.class.getName());
   Node parentNode = currentNode;

   do {
     try {
       parentNode = parentNode.getParent();
     } catch(AccessDeniedException ex) {
       return false;
     } catch(Exception ex) {
       LOG.error("Error while getting parent of node " + parentNode.getPath(), ex);
       return false;
     }
   } while (!((NodeImpl) parentNode).isRoot());

   return true;
 }

 public UIExtensionFilterType getType() {
   return UIExtensionFilterType.MANDATORY;
 }

 public void onDeny(Map<String, Object> context) throws Exception {
 }

}