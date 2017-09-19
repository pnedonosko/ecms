package org.exoplatform.services.wcm.search.connector;

import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.cms.documents.TrashService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodeLocation;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.picocontainer.Startable;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

/**
 *
 */
public class FileIndexerListener implements EventListener, Startable {
  private static final Log LOGGER = ExoLogger.getExoLogger(FileIndexerListener.class);

  private IndexingService indexingService;

  private RepositoryService repositoryService;

  private TrashService trashService;

  private String trashHomePath;

  public FileIndexerListener(IndexingService indexingService, RepositoryService repositoryService, TrashService trashService) {
    this.indexingService = indexingService;
    this.repositoryService = repositoryService;
    this.trashService = trashService;
  }

  @Override
  public void start() {
    try {
      Session session = repositoryService.getCurrentRepository().getSystemSession("collaboration");
      ObservationManager observationManager = session.getWorkspace().getObservationManager();
      observationManager.addEventListener(this,
              Event.NODE_ADDED | Event.NODE_REMOVED | Event.PROPERTY_ADDED | Event.PROPERTY_CHANGED | Event.PROPERTY_REMOVED,
              "/", true, null, null, false);
    } catch (RepositoryException e) {
      LOGGER.error("Cannot start to observe nodes", e);
    }
  }

  @Override
  public void stop() {

  }

  @Override
  public void onEvent(EventIterator eventIterator) {
    while(eventIterator.hasNext()) {
      Event event = eventIterator.nextEvent();

      try {
        Node node;

        switch(event.getType()) {
          case Event.NODE_ADDED:
            node = getNodeByPath(event.getPath());
            if(node != null && node.getPrimaryNodeType().getName().equals(NodetypeConstant.NT_FILE)) {
              if(isInTrash(node)) {
                indexingService.unindex(FileindexingConnector.TYPE, node.getUUID());
              } else {
                indexingService.index(FileindexingConnector.TYPE, node.getUUID());
              }
            }
            break;
          case Event.NODE_REMOVED:
            node = getNodeByPath(event.getPath());
            if(node != null && node.getPrimaryNodeType().getName().equals(NodetypeConstant.NT_FILE)) {
              indexingService.unindex(FileindexingConnector.TYPE, node.getUUID());
            }
            break;
          case Event.PROPERTY_ADDED:
          case Event.PROPERTY_CHANGED:
          case Event.PROPERTY_REMOVED:
            node = getNodeOfPropertyByPath(event.getPath());
            if(node != null && node.getPrimaryNodeType().getName().equals(NodetypeConstant.NT_FILE)) {
              indexingService.reindex(FileindexingConnector.TYPE, node.getUUID());
            }
            break;
        }
      } catch (RepositoryException e) {
        LOGGER.error("Error while indexing file", e);
      }
    }
  }

  protected Node getNodeByPath(String path) throws RepositoryException {
    return NodeLocation.getNodeByLocation(new NodeLocation(WCMCoreUtils.getRepository().getConfiguration().getName(), "collaboration", path));
  }

  protected Node getNodeOfPropertyByPath(String path) throws RepositoryException {
    return getNodeByPath(path.substring(0, path.lastIndexOf("/") - 1));
  }

  protected boolean isInTrash(Node node) throws RepositoryException {
    return node != null && getTrashHomePath() != null && node.getPath().startsWith(getTrashHomePath());
  }

  protected String getTrashHomePath() {
    if(this.trashHomePath == null) {
      Node trashHomeNode = trashService.getTrashHomeNode();
      if(trashHomeNode != null) {
        try {
          trashHomePath = trashHomeNode.getPath() + "/";
        } catch (RepositoryException e) {
          LOGGER.error("Cannot get Trash home path", e);
        }
      }
    }

    return this.trashHomePath;
  }
}
