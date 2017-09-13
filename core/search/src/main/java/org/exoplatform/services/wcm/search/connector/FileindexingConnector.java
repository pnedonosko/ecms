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
import org.exoplatform.services.wcm.core.NodetypeUtils;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.json.JSONObject;

import javax.jcr.*;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.PropertyDefinition;
import javax.jcr.query.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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
            .append("    \"createdDate\" : {\"type\" : \"date\", \"format\": \"epoch_millis\"},\n")
            .append("    \"lastUpdatedDate\" : {\"type\" : \"date\", \"format\": \"epoch_millis\"},\n")
            .append("    \"fileType\" : {\"type\" : \"keyword\"},\n")
            .append("    \"fileSize\" : {\"type\" : \"long\"},\n")
            .append("    \"dc:title\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:creator\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:subject\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:description\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:publisher\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:contributor\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:date\" : {\"type\" : \"date\"},\n")
            .append("    \"dc:resourceType\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:format\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:identifier\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:source\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:language\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:relation\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:coverage\" : {\"type\" : \"text\"},\n")
            .append("    \"dc:rights\" : {\"type\" : \"text\"}\n")
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
      if(node.hasProperty(NodetypeConstant.EXO_TITLE)) {
        fields.put("title", node.getProperty(NodetypeConstant.EXO_TITLE).getString());
      }
      if(node.hasProperty(NodetypeConstant.EXO_OWNER)) {
        fields.put("author", node.getProperty(NodetypeConstant.EXO_OWNER).getString());
      }
      if(node.hasProperty("jcr:created")) {
        fields.put("createdDate", String.valueOf(node.getProperty("jcr:created").getDate().getTimeInMillis()));
      }

      Node contentNode = node.getNode(NodetypeConstant.JCR_CONTENT);
      if(contentNode != null) {
        if (contentNode.hasProperty(NodetypeConstant.JCR_MIMETYPE)) {
          fields.put("fileType", contentNode.getProperty(NodetypeConstant.JCR_MIMETYPE).getString());
        }
        InputStream fileStream = contentNode.getProperty(NodetypeConstant.JCR_DATA).getStream();
        byte[] fileBytes = IOUtils.toByteArray(fileStream);
        fields.put("file", Base64.getEncoder().encodeToString(fileBytes));

        fields.put("fileSize", String.valueOf(fileBytes.length));

        // Dublin Core metadata
        Map<String, String> dublinCoreMetadata = extractDublinCoreMetadata(contentNode);
        if(dublinCoreMetadata != null) {
          fields.putAll(dublinCoreMetadata);
        }
      }

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
      Query query = queryManager.createQuery("select jcr:uuid from " + NodetypeConstant.NT_FILE, Query.SQL);
      QueryResult result = query.execute();
      RowIterator rowIterator = result.getRows();
      while(rowIterator.hasNext()) {
        Row row = rowIterator.nextRow();
        allIds.add(row.getValue("jcr:uuid").getString());
      }
    } catch (RepositoryException e) {
      LOGGER.error("Error while fetching all nt:file nodes", e);
    }
    return allIds;
  }

  protected Map<String, String> extractDublinCoreMetadata(Node contentNode) throws RepositoryException {
    Map<String, String> dcFields = null;
    if (contentNode.isNodeType(NodetypeConstant.DC_ELEMENT_SET)) {
      dcFields = new HashMap<>();
      NodeTypeManager nodeTypeManager = repositoryService.getCurrentRepository().getNodeTypeManager();
      PropertyDefinition[] dcPropertyDefinitions = nodeTypeManager.getNodeType(NodetypeConstant.DC_ELEMENT_SET).getPropertyDefinitions();
      for (PropertyDefinition propertyDefinition : dcPropertyDefinitions) {
        String propertyName = propertyDefinition.getName();
        if (contentNode.hasProperty(propertyName)) {
          Property property = contentNode.getProperty(propertyName);
          String strValue;
          if(propertyDefinition.isMultiple()) {
            Value value = property.getValues()[0];
            if (property.getType() == PropertyType.DATE) {
              strValue = String.valueOf(value.getDate().toInstant().toEpochMilli());
            } else {
              strValue = value.getString();
            }
          } else {
            if (property.getType() == PropertyType.DATE) {
              strValue = String.valueOf(property.getDate().toInstant().toEpochMilli());
            } else {
              strValue = property.getString();
            }
          }
          dcFields.put(propertyName, strValue);
        }
      }
    }
    return dcFields;
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
