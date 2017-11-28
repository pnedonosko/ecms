package org.exoplatform.services.wcm.search;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.cluster.StartableClusterAware;
import org.exoplatform.commons.search.index.IndexingOperationProcessor;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.RootContainer;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.impl.core.query.SearchManager;
import org.exoplatform.services.jcr.impl.core.query.SystemSearchManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.scheduler.JobSchedulerService;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.search.connector.FileindexingConnector;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.servlet.ServletContext;
import java.util.concurrent.*;

/**
 * Startable service to index all files in Elasticsearch.The process:
 *  - add REINDEX_ALL operation in indexing queue for files
 *  - process the indexing queue for files (which will process the REINDEX_ALL operation) to fill it with all files to index in ES
 *  - launch hot reindexing of JCR workspaces (collaboration and system) asynchronously
 */
public class FileESMigration implements StartableClusterAware {

  private static final Log LOG = ExoLogger.getLogger(FileESMigration.class);

  public static final String FILE_ES_INDEXATION_KEY = "FILE_ES_INDEXATION";

  public static final String FILE_ES_INDEXATION_DONE_KEY = "FILE_ES_INDEXATION_DONE";

  public static final String FILE_JCR_COLLABORATION_REINDEXATION_DONE_KEY = "FILE_JCR_COLLABORATION_REINDEXATION_DONE";

  public static final String FILE_JCR_SYSTEM_REINDEXATION_DONE_KEY = "FILE_JCR_SYSTEM_REINDEXATION_DONE";

  private IndexingService indexingService;

  private SettingService settingService;

  private RepositoryService repositoryService;

  private IndexingOperationProcessor indexingOperationProcessor;

  private JobSchedulerService jobSchedulerService;

  private Executor executor = Executors.newSingleThreadExecutor();

  public FileESMigration(IndexingService indexingService, SettingService settingService, RepositoryService repositoryService, IndexingOperationProcessor indexingOperationProcessor, JobSchedulerService jobSchedulerService) {
    this.indexingService = indexingService;
    this.settingService = settingService;
    this.repositoryService = repositoryService;
    this.indexingOperationProcessor = indexingOperationProcessor;
    this.jobSchedulerService = jobSchedulerService;
  }

  @Override
  public void start() {
    final boolean indexationInESDone = isIndexationInESDone();
    final boolean jcrReindexationDone = isJCRReindexationDone();

    if(!indexationInESDone || !jcrReindexationDone) {
      PortalContainer.addInitTask(PortalContainer.getInstance().getPortalContext(), new RootContainer.PortalContainerPostInitTask() {
        @Override
        public void execute(ServletContext context, PortalContainer portalContainer) {
          executor.execute(() -> {
            ExoContainerContext.setCurrentContainer(portalContainer);

            LOG.info("== Files ES migration - Starting migration of files in ES");

            if(!indexationInESDone) {
              printNumberOfFileToIndex();
              pushAllFilesInESIndexingQueue();
            }

            if(!jcrReindexationDone) {
              reindexJCR();
            }
          });
        }
      });
    }
  }

  @Override
  public boolean isDone() {
    return isIndexationInESDone() && isJCRReindexationDone();
  }

  public void reindexJCR() {
    try {
      SearchManager searchManager = (SearchManager) repositoryService.getCurrentRepository().getWorkspaceContainer("collaboration").getComponent(SearchManager.class);

      if(!isJCRCollaboraionReindexationDone()) {
        LOG.info("== Files ES migration - Starting reindexation of JCR collaboration workspace");
        CompletableFuture<Boolean> reindexCollaborationWSResult = searchManager.reindexWorkspace(false, 0);
        reindexCollaborationWSResult.thenAccept(successful -> {
          if (successful && !Thread.currentThread().isInterrupted()) {
            LOG.info("== Files ES migration - Reindexation of JCR collaboration workspace done");
            settingService.set(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_JCR_COLLABORATION_REINDEXATION_DONE_KEY, SettingValue.create(true));
          } else {
            if(Thread.currentThread().isInterrupted()) {
              LOG.error("== Files ES migration - Reindexation of JCR collaboration workspace has been interrupted. Reindex it by restarting the server");
            } else {
              LOG.error("== Files ES migration - Reindexation of JCR collaboration workspace failed. " +
                      "Check logs to fix the issue, then reindex it by restarting the server");
            }
          }
        });
      }

      if(!isJCRSystemReindexationDone()) {
        LOG.info("== Files ES migration - Starting reindexation of JCR system workspace");
        SystemSearchManager systemSearchManager = (SystemSearchManager) repositoryService.getCurrentRepository().getWorkspaceContainer("system").getComponent(SystemSearchManager.class);
        CompletableFuture<Boolean> reindexSystemWSResult = systemSearchManager.reindexWorkspace(false, 0);
        reindexSystemWSResult.thenAccept(successful -> {
          if (successful && !Thread.currentThread().isInterrupted()) {
            LOG.info("== Files ES migration - Reindexation of JCR system workspace done");
            settingService.set(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_JCR_SYSTEM_REINDEXATION_DONE_KEY, SettingValue.create(true));
          } else {
            if(Thread.currentThread().isInterrupted()) {
              LOG.error("== Files ES migration - Reindexation of JCR system workspace has been interrupted. Reindex it by restarting the server");
            } else {
              LOG.error("== Files ES migration - Reindexation of JCR system workspace failed. " +
                      "Check logs to fix the issue, then reindex it by restarting the server");
            }
          }
        });
      }
    } catch (RepositoryException e) {
      LOG.error("== Files ES migration - Error while reindexing JCR collaboration and system workspaces", e);
    }
  }

  public void pushAllFilesInESIndexingQueue() {
    LOG.info("== Files ES migration - Starting pushing all files in indexation queue");
    indexingService.reindexAll(FileindexingConnector.TYPE);
    try {
      // process the reindexAll operation synchronously to make sure it is done before the JCR workspace reindexation (otherwise JCR queries will not retrieve nodes)
      processReindexingOperation();
      settingService.set(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_ES_INDEXATION_DONE_KEY, SettingValue.create(true));
      LOG.info("== Files ES migration - Push of all files in indexation queue done");
    } catch(Exception e) {
      LOG.error("== Files ES migration - Error while indexing all files in ES", e);
    }
  }

  @ExoTransactional
  public void processReindexingOperation() throws Exception {
    try {
      // Pause ESBulkIndexer job to avoid concurrent executions in the process() operation
      LOG.info("== Files ES migration - Pause files scheduled indexation");
      jobSchedulerService.pauseJob("ESBulkIndexer-" + FileindexingConnector.TYPE, "ElasticSearch");
      indexingOperationProcessor.processByEntityType(FileindexingConnector.TYPE);
    } finally {
      LOG.info("== Files ES migration - Resume files scheduled indexation");
      jobSchedulerService.resumeJob("ESBulkIndexer-" + FileindexingConnector.TYPE, "ElasticSearch");
    }
  }

  private boolean isIndexationInESDone() {
    SettingValue<?> done = settingService.get(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_ES_INDEXATION_DONE_KEY);
    return done != null && done.getValue().equals("true");
  }

  private boolean isJCRReindexationDone() {
    return isJCRCollaboraionReindexationDone() && isJCRSystemReindexationDone();
  }

  private boolean isJCRCollaboraionReindexationDone() {
    SettingValue<?> done = settingService.get(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_JCR_COLLABORATION_REINDEXATION_DONE_KEY);
    return done != null && done.getValue().equals("true");
  }

  private boolean isJCRSystemReindexationDone() {
    SettingValue<?> done = settingService.get(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_JCR_SYSTEM_REINDEXATION_DONE_KEY);
    return done != null && done.getValue().equals("true");
  }

  private void printNumberOfFileToIndex() {
    try {
      Session session = WCMCoreUtils.getSystemSessionProvider().getSession("collaboration", repositoryService.getCurrentRepository());
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery("select * from " + NodetypeConstant.NT_FILE, Query.SQL);
      QueryResult result = query.execute();
      LOG.info("== Files ES migration - Number of files to index : " + result.getNodes().getSize());
    } catch (RepositoryException e) {
      LOG.error("== Files ES migration - Error while counting all nt:file to index", e);
    }
  }
}
