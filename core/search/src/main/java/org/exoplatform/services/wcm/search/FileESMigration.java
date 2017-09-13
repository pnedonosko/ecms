package org.exoplatform.services.wcm.search;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.cluster.StartableClusterAware;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.search.connector.FileindexingConnector;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Startable service to index all files in Elasticsearch
 */
public class FileESMigration implements StartableClusterAware {

  private static final Log LOG = ExoLogger.getLogger(FileESMigration.class);

  public static final String FILE_ES_INDEXATION_KEY = "FILE_ES_INDEXATION";

  public static final String FILE_ES_INDEXATION_DONE_KEY = "FILE_ES_INDEXATION_DONE";

  private IndexingService indexingService;

  private SettingService settingService;

  private Executor executor = Executors.newSingleThreadExecutor();

  public FileESMigration(IndexingService indexingService, SettingService settingService) {
    this.indexingService = indexingService;
    this.settingService = settingService;
  }

  @Override
  public void start() {
    if(!isIndexationDone()) {
      LOG.info("Starting indexation of all files");
      ExoContainer container = ExoContainerContext.getCurrentContainer();
      executor.execute(() -> {
        ExoContainerContext.setCurrentContainer(container);
        indexingService.reindexAll(FileindexingConnector.TYPE);
      });
      settingService.set(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_ES_INDEXATION_DONE_KEY, SettingValue.create(true));
    }
  }

  @Override
  public boolean isDone() {
    return isIndexationDone();
  }

  private boolean isIndexationDone() {
    SettingValue<Boolean> done = (SettingValue<Boolean>) settingService.get(Context.GLOBAL, Scope.GLOBAL.id(FILE_ES_INDEXATION_KEY), FILE_ES_INDEXATION_DONE_KEY);
    return done != null && done.getValue();
  }
}
