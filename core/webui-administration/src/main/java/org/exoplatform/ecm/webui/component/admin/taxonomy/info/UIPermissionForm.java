/*
 * Copyright (C) 2003-2007 eXo Platform SAS.
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
package org.exoplatform.ecm.webui.component.admin.taxonomy.info;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;

import org.exoplatform.ecm.permission.info.UIPermissionInputSet;
import org.exoplatform.ecm.webui.selector.UIGroupMemberSelector;
import org.exoplatform.ecm.webui.selector.UISelectable;
import org.exoplatform.ecm.webui.utils.LockUtil;
import org.exoplatform.ecm.webui.utils.PermissionUtil;
import org.exoplatform.ecm.webui.utils.Utils;
import org.exoplatform.services.jcr.access.AccessControlEntry;
import org.exoplatform.services.jcr.access.PermissionType;
import org.exoplatform.services.jcr.access.SystemIdentity;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPopupContainer;
import org.exoplatform.webui.core.lifecycle.UIFormLifecycle;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;
import org.exoplatform.webui.event.Event.Phase;
import org.exoplatform.webui.form.UIForm;

/**
 * Created by The eXo Platform SARL Author : nqhungvn
 * nguyenkequanghung@yahoo.com July 3, 2006 10:07:15 AM Editor : tuanp
 * phamtuanchip@yahoo.de Oct 13, 2006
 */
@ComponentConfig(
    lifecycle = UIFormLifecycle.class,
    template = "system:/groovy/webui/form/UIFormWithTitle.gtmpl",
    events = {
      @EventConfig(listeners = UIPermissionForm.SaveActionListener.class),
      @EventConfig(phase = Phase.DECODE, listeners = UIPermissionForm.ResetActionListener.class),
      @EventConfig(phase = Phase.DECODE, listeners = UIPermissionForm.CloseActionListener.class),
      @EventConfig(phase = Phase.DECODE, listeners = UIPermissionForm.SelectUserActionListener.class),
      @EventConfig(phase = Phase.DECODE, listeners = UIPermissionForm.SelectMemberActionListener.class),
      @EventConfig(phase = Phase.DECODE, listeners = UIPermissionForm.AddAnyActionListener.class)
    }
)

public class UIPermissionForm extends UIForm implements UISelectable {

  public static final String PERMISSION   = "permission";

  public static final String POPUP_SELECT = "SelectUserOrGroup";

  public static final String SELECT_GROUP_ID = "TaxoSelectUserOrGroup";
  private static final Log LOG  = ExoLogger.getLogger("admin.UIPermissionForm");
  private Node               currentNode;

  public UIPermissionForm() throws Exception {
    addChild(new UIPermissionInputSet(PERMISSION));
    setActions(new String[] { "Save", "Reset", "Close" });
  }

  private void refresh() {
    reset();
    checkAll(false);
  }

  private void checkAll(boolean check) {
    UIPermissionInputSet uiInputSet = getChildById(PERMISSION) ;
    for (String perm : PermissionType.ALL) {
      uiInputSet.getUIFormCheckBoxInput(perm).setChecked(check);
    }
  }

  protected boolean isEditable(Node node) throws Exception {
    return PermissionUtil.canChangePermission(node);
  }
  public void fillForm(String user, ExtendedNode node) throws Exception {
    UIPermissionInputSet uiInputSet = getChildById(PERMISSION) ;
    refresh() ;
    uiInputSet.getUIStringInput(UIPermissionInputSet.FIELD_USERORGROUP).setValue(user) ;

    if(user.equals(Utils.getNodeOwner(node))) {
      for (String perm : PermissionType.ALL) {
        uiInputSet.getUIFormCheckBoxInput(perm).setChecked(true) ;
      }
    } else {
      List<AccessControlEntry> permsList = node.getACL().getPermissionEntries() ;
      Iterator perIter = permsList.iterator() ;
      StringBuilder userPermission = new StringBuilder() ;
      while(perIter.hasNext()) {
        AccessControlEntry accessControlEntry = (AccessControlEntry)perIter.next() ;
        if(user.equals(accessControlEntry.getIdentity())) {
          userPermission.append(accessControlEntry.getPermission()).append(" ");
        }
      }
      for (String perm : PermissionType.ALL) {
        boolean isCheck = userPermission.toString().contains(perm) ;
        uiInputSet.getUIFormCheckBoxInput(perm).setChecked(isCheck) ;
      }
    }

  }

  protected void lockForm(boolean isLock) {
    UIPermissionInputSet uiInputSet = getChildById(PERMISSION);
    if (isLock) {
      setActions(new String[] { "Reset", "Close" });
      uiInputSet.setActionInfo(UIPermissionInputSet.FIELD_USERORGROUP, null);
    } else {
      setActions(new String[] { "Save", "Reset", "Close" });
      uiInputSet.setActionInfo(UIPermissionInputSet.FIELD_USERORGROUP, new String[] { "SelectUser",
          "SelectMember", "AddAny" });
    }
    for (String perm : PermissionType.ALL) {
      uiInputSet.getUIFormCheckBoxInput(perm).setEnable(!isLock);
    }
  }

  private String  getExoOwner(Node node) throws Exception {
    return Utils.getNodeOwner(node) ;
  }

  public void doSelect(String selectField, Object value) {
    try {
      ExtendedNode node = (ExtendedNode)this.getCurrentNode();
      checkAll(false);
      fillForm(value.toString(), node) ;
      lockForm(value.toString().equals(getExoOwner(node)));
      getUIStringInput(selectField).setValue(value.toString());
    } catch (Exception e) {
      LOG.error("Unexpected error", e);
    }
  }

  static public class ResetActionListener extends EventListener<UIPermissionForm> {
    public void execute(Event<UIPermissionForm> event) throws Exception {
      UIPermissionForm uiForm = event.getSource();
      uiForm.lockForm(false) ;
      uiForm.refresh() ;
      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm.getParent());
    }
  }
  static public class SaveActionListener extends EventListener<UIPermissionForm> {
    public void execute(Event<UIPermissionForm> event) throws Exception {
      UIPermissionForm uiForm = event.getSource();
      Node currentNode = uiForm.getCurrentNode();
      UIPermissionManager uiParent = uiForm.getParent();
      UIApplication uiApp = uiForm.getAncestorOfType(UIApplication.class);
      String userOrGroup = uiForm.getChild(UIPermissionInputSet.class).getUIStringInput(
          UIPermissionInputSet.FIELD_USERORGROUP).getValue();
      List<String> permsList = new ArrayList<String>();
      List<String> permsRemoveList = new ArrayList<String>();
      if (currentNode.isLocked()) {
        String lockToken = LockUtil.getLockToken(currentNode);
        if (lockToken != null)
          currentNode.getSession().addLockToken(lockToken);
      }
      if (!currentNode.isCheckedOut()) {
        uiApp.addMessage(new ApplicationMessage("UIActionBar.msg.node-checkedin",
                                                null,
                                                ApplicationMessage.WARNING));
        event.getRequestContext().addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
        return;
      }
      for (String perm : PermissionType.ALL) {
        if (uiForm.getUIFormCheckBoxInput(perm).isChecked()) permsList.add(perm);
        else permsRemoveList.add(perm);
      }
      if(uiForm.getUIFormCheckBoxInput(PermissionType.ADD_NODE).isChecked() ||
          uiForm.getUIFormCheckBoxInput(PermissionType.REMOVE).isChecked() ||
          uiForm.getUIFormCheckBoxInput(PermissionType.SET_PROPERTY).isChecked())
      {
        if(!permsList.contains(PermissionType.READ))
          permsList.add(PermissionType.READ) ;
      }

      if (Utils.isNameEmpty(userOrGroup)) {
        uiApp.addMessage(new ApplicationMessage("UIPermissionForm.msg.userOrGroup-required",
                                                null,
                                                ApplicationMessage.WARNING));
        event.getRequestContext().addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
        return;
      }
      if (permsList.size() == 0) {
        uiApp.addMessage(new ApplicationMessage("UIPermissionForm.msg.checkbox-require",
                                                null,
                                                ApplicationMessage.WARNING));
        event.getRequestContext().addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
        return;
      }
      String[] permsArray = permsList.toArray(new String[permsList.size()]);
      ExtendedNode node = (ExtendedNode) currentNode;
      if (PermissionUtil.canChangePermission(node)) {
        if (node.canAddMixin("exo:privilegeable")){
          node.addMixin("exo:privilegeable");
          node.setPermission(Utils.getNodeOwner(node),PermissionType.ALL);
        }
        for (String perm : permsRemoveList) {
          try {
            node.removePermission(userOrGroup, perm);
          } catch (AccessDeniedException ade) {
            uiApp.addMessage(new ApplicationMessage("UIPermissionForm.msg.access-denied",
                                                    null,
                                                    ApplicationMessage.WARNING));
            event.getRequestContext().addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
            return;
          }
        }
        if(PermissionUtil.canChangePermission(node)) node.setPermission(userOrGroup, permsArray);
        node.save();
        uiParent.getChild(UIPermissionInfo.class).updateGrid();
      } else {
        uiApp.addMessage(new ApplicationMessage("UIPermissionForm.msg.not-change-permission", null,
            ApplicationMessage.WARNING));
        event.getRequestContext().addUIComponentToUpdateByAjax(uiApp.getUIPopupMessages());
        return;
      }

      uiForm.refresh();
      currentNode.getSession().save();
      event.getRequestContext().addUIComponentToUpdateByAjax(uiParent);
    }
  }

  static public class SelectUserActionListener extends EventListener<UIPermissionForm> {
    public void execute(Event<UIPermissionForm> event) throws Exception {
      UIPermissionForm uiForm = event.getSource();
      ((UIPermissionManager)uiForm.getParent()).initUserSelector();
      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm.getParent());
    }
  }

  static public class AddAnyActionListener extends EventListener<UIPermissionForm> {
    public void execute(Event<UIPermissionForm> event) throws Exception {
      UIPermissionForm uiForm = event.getSource();
      UIPermissionInputSet uiInputSet = uiForm.getChildById(UIPermissionForm.PERMISSION);
      uiInputSet.getUIStringInput(UIPermissionInputSet.FIELD_USERORGROUP).setValue(
          SystemIdentity.ANY);
      uiForm.checkAll(false);
      uiInputSet.getUIFormCheckBoxInput(PermissionType.READ).setChecked(true);
      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm.getParent());
    }
  }

  static public class SelectMemberActionListener extends EventListener<UIPermissionForm> {
    public void execute(Event<UIPermissionForm> event) throws Exception {
      UIPermissionForm uiForm = event.getSource();
      UIGroupMemberSelector uiGroupMemberSelector = uiForm.createUIComponent(UIGroupMemberSelector.class,
                                                                             null,
                                                                             UIPermissionForm.SELECT_GROUP_ID);
      uiGroupMemberSelector.setSourceComponent(uiForm,
                                               new String[] { UIPermissionInputSet.FIELD_USERORGROUP });
      uiForm.getAncestorOfType(UIPermissionManager.class).initPopupPermission(uiGroupMemberSelector);
      event.getRequestContext().addUIComponentToUpdateByAjax(uiForm.getParent());
    }
  }

  static public class CloseActionListener extends EventListener<UIPermissionForm> {
    public void execute(Event<UIPermissionForm> event) throws Exception {
      UIPopupContainer uiPopupContainer = event.getSource().getAncestorOfType(UIPopupContainer.class);
      uiPopupContainer.deActivate() ;
      event.getRequestContext().addUIComponentToUpdateByAjax(uiPopupContainer) ;
    }
  }

  public Node getCurrentNode() {
    return currentNode;
  }

  public void setCurrentNode(Node currentNode) {
    this.currentNode = currentNode;
  }
}
