package org.exoplatform.services.wcm.search.connector;

import org.apache.commons.io.IOUtils;
import org.exoplatform.commons.search.domain.Document;
import org.exoplatform.commons.search.index.impl.ElasticIndexingServiceConnector;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.access.AccessControlList;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Indexing Connector for Files
 */
public class FileindexingConnector extends ElasticIndexingServiceConnector {

  private static final Log LOGGER = ExoLogger.getExoLogger(FileindexingConnector.class);

  public static final String TYPE = "file";

  private RepositoryService repositoryService;

  public FileindexingConnector(InitParams initParams) {
    super(initParams);
    this.repositoryService = CommonsUtils.getService(RepositoryService.class);
  }

  @Override
  public boolean isNeedIngestPipeline() {
    return true;
  }

  @Override
  public String getPipelineName() {
    return "file";
  }

  @Override
  public String getMapping() {
    StringBuilder mapping = new StringBuilder()
            .append("{")
            .append("  \"properties\" : {\n")
            .append("    \"repository\" : {\"type\" : \"keyword\"},\n")
            .append("    \"workspace\" : {\"type\" : \"keyword\"},\n")
            .append("    \"path\" : {\"type\" : \"keyword\"},\n")
            .append("    \"author\" : {\"type\" : \"keyword\"},\n")
            .append("    \"permissions\" : {\"type\" : \"keyword\"},\n")
            .append("    \"createdDate\" : {\"type\" : \"date\", \"format\": \"\"epoch_millis},\n")
            .append("    \"lastUpdatedDate\" : {\"type\" : \"date\", \"format\": \"\"epoch_millis}},\n")
            .append("    \"fileType\" : {\"type\" : \"keyword\"},\n")
            .append("    \"fileSize\" : {\"type\" : \"long\"}\n")
            .append("  }\n")
            .append("}");

    return mapping.toString();
  }

  @Override
  public String getAttachmentProcessor() {
    StringBuilder processors = new StringBuilder()
            .append("{")
            .append("  \"description\" : \"File processor\",\n")
            .append("  \"processors\" : [{\n")
            .append("    \"attachment\" : {\n")
            .append("      \"field\" : \"file\",\n")
            .append("      \"indexed_chars\" : -1,\n")
            .append("      \"properties\" : [\"content\"]\n")
            .append("    }\n")
            .append("  },{\n")
            .append("    \"remove\" : {\n")
            .append("      \"field\" : \"file\"\n")
            .append("    }\n")
            .append("  }]\n")
            .append("}");

    return processors.toString();
  }

  @Override
  public Document create(String id) {
    try {
      Session session = WCMCoreUtils.getSystemSessionProvider().getSession("collaboration", repositoryService.getCurrentRepository());
      Node node = session.getNodeByUUID(id);

      if(node == null || node.getPrimaryNodeType().equals(NodetypeConstant.NT_FILE)) {
        return null;
      }

      Map<String, String> fields = new HashMap<>();
      fields.put("name", node.getName());
      fields.put("repository", ((ManageableRepository) session.getRepository()).getConfiguration().getName());
      fields.put("workspace", session.getWorkspace().getName());
      fields.put("path", node.getPath());
      if(node.hasProperty("exo:title")) {
        fields.put("title", node.getProperty("exo:title").getString());
      }
      Node contentNode = node.getNode("jcr:content");
      if(contentNode != null && contentNode.hasProperty("jcr:mimeType")) {
        fields.put("fileType", contentNode.getProperty("jcr:mimeType").getString());
      }
      if(node.hasProperty("exo:owner")) {
        fields.put("author", node.getProperty("exo:owner").getString());
      }
      if(node.hasProperty("jcr:created")) {
        fields.put("createdDate", String.valueOf(node.getProperty("jcr:created").getDate().getTimeInMillis()));
      }

      InputStream fileStream = contentNode.getProperty("jcr:data").getStream();
      byte[] fileBytes = IOUtils.toByteArray(fileStream);
      fields.put("file", Base64.getEncoder().encodeToString(fileBytes));

      fields.put("fileSize", String.valueOf(fileBytes.length));

      return new Document(TYPE, id, null, new Date(), computePermissions(node), fields);
    } catch (RepositoryException | IOException e) {
      LOGGER.error("Error while indexing file " + id, e);
    }

    return null;
  }

  @Override
  public Document update(String id) {
    return create(id);
  }

  @Override
  public List<String> getAllIds(int offset, int limit) {
    List<String> allIds = new ArrayList<>();
    try {
      Session session = WCMCoreUtils.getSystemSessionProvider().getSession("collaboration", repositoryService.getCurrentRepository());
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery("select * from nt:file", Query.SQL);
      QueryResult result = query.execute();
      NodeIterator nodeIterator = result.getNodes();
      while(nodeIterator.hasNext()) {
        Node node = nodeIterator.nextNode();
        allIds.add(node.getUUID());
      }
    } catch (RepositoryException e) {
      LOGGER.error("Error while fetching all nt:file nodes", e);
    }
    return allIds;
  }

  private Set<String> computePermissions(Node node) throws RepositoryException {
    Set<String> permissions = new HashSet<>();

    AccessControlList acl = ((ExtendedNode) node).getACL();
    //Add the owner
    permissions.add(acl.getOwner());
    //Add permissions
    if (acl.getPermissionEntries() != null) {
      permissions.addAll(acl.getPermissionEntries().stream().map(permission -> permission.getIdentity()).collect(Collectors.toSet()));
    }

    return permissions;
  }
}
