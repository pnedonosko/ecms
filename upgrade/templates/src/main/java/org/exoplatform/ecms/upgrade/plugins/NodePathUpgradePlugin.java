package org.exoplatform.ecms.upgrade.plugins;

import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.NodeIterator;

import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.PrivilegedSystemHelper;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * 
 * This class is used to migrade nodes which path is changed. It will move these nodes to new position. We must
 * configured source node and target node in configuration.properties via two variable source-node and target-node.
 * source-node must contains the workspace name.
 * Ex: source-node=collaboration:/sites content/live
 *     target-node=/sites
 *
 */

public class NodePathUpgradePlugin extends UpgradeProductPlugin {
  
  private Log log = ExoLogger.getLogger(this.getClass());
  private RepositoryService repositoryService_;
  
  public NodePathUpgradePlugin(RepositoryService repoService, InitParams initParams){
    super(initParams);
    this.repositoryService_ = repoService;
  }
  
  @Override
  public void processUpgrade(String oldVersion, String newVersion) {
    log.info("Start " + this.getClass().getName() + ".............");
    String srcNode = PrivilegedSystemHelper.getProperty("source-node");
    String destNode = PrivilegedSystemHelper.getProperty("target-node");
    if ((srcNode == null) || ("".equals(srcNode)) || (destNode == null) || ("".equals(destNode))) {
      log.info("Source and target node must be set to run plugin!");
      return;
    }
    String workspace = srcNode.split(":")[0];
    String nodePath = srcNode.split(":")[1];
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try{
      Session session = sessionProvider.getSession(workspace, repositoryService_.getCurrentRepository());
      Node sourceNode = session.getRootNode().getNode(nodePath.substring(1));
      if (sourceNode.hasNodes()){
        NodeIterator iter = sourceNode.getNodes();
        while (iter.hasNext()){
          Node child = (Node) iter.next();
          log.info("Move " + nodePath + "/" + child.getName() + " to " + destNode + "/" + child.getName());
          session.move(nodePath + "/" + child.getName(), destNode + "/" + child.getName());
          session.save();
        }
        //Remove source node
        if (nodePath.substring(1).contains("/")){
          Node parentNode = session.getRootNode().getNode(nodePath.substring(1).split("/")[0]);
          parentNode.remove();
          session.save();
        } else {
          sourceNode.remove();
          session.save();
        }
      }
    }catch(Exception e){
      log.error("Unexpected error happens in moving nodes", e.getMessage());
    }finally {
      if (sessionProvider != null) {
        sessionProvider.close();
      }
    }
  }
  
  @Override
  public boolean shouldProceedToUpgrade(String previousVersion, String newVersion) {
    return true;
  }
}
