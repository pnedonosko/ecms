/*
 * Copyright (C) 2003-2013 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.services.wcm.search.connector;

import org.exoplatform.commons.api.search.data.SearchContext;
import org.exoplatform.commons.api.search.data.SearchResult;
import org.exoplatform.commons.search.es.ElasticSearchServiceConnector;
import org.exoplatform.commons.search.es.client.ElasticSearchingClient;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.cms.documents.DocumentService;
import org.exoplatform.services.cms.impl.Utils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.search.base.EcmsSearchResult;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.json.simple.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Search connector for files
 */
public class FileSearchServiceConnector extends ElasticSearchServiceConnector {
  
  private static final Log LOG = ExoLogger.getLogger(FileSearchServiceConnector.class.getName());

  private DocumentService documentService;

  public FileSearchServiceConnector(InitParams initParams, ElasticSearchingClient client) {
    super(initParams, client);
    this.documentService = CommonsUtils.getService(DocumentService.class);
  }

  @Override
  protected String getSourceFields() {
    List<String> fields = Arrays.asList("name",
            "title",
            "repository",
            "workspace",
            "path",
            "author",
            "createdDate",
            "lastUpdatedDate",
            "fileType",
            "fileSize");

    return fields.stream().map(field -> "\"" + field + "\"").collect(Collectors.joining(","));
  }

  @Override
  protected SearchResult buildHit(JSONObject jsonHit, SearchContext searchContext) {
    SearchResult searchResult = super.buildHit(jsonHit, searchContext);

    JSONObject hitSource = (JSONObject) jsonHit.get("_source");
    String workspace = (String) hitSource.get("workspace");
    String nodePath = (String) hitSource.get("path");
    String fileType = (String) hitSource.get("fileType");
    String fileSize = (String) hitSource.get("fileSize");
    String createdDate = (String) hitSource.get("createdDate");

    String detail = getFormattedFileSize(fileSize) + " - " + getFormattedDate(createdDate);

    SearchResult ecmsSearchResult = new EcmsSearchResult(getUrl(nodePath),
            getPreviewUrl(jsonHit, searchContext),
            searchResult.getTitle(),
            searchResult.getExcerpt(),
            detail,
            getImageUrl(workspace, nodePath),
            searchResult.getDate(),
            searchResult.getRelevancy(),
            fileType,
            nodePath);

    return ecmsSearchResult;
  }

  protected String getUrl(String nodePath) {
    String url = "";
    try {
      url = documentService.getLinkInDocumentsApp(nodePath);
    } catch (Exception e) {
      LOG.error("Cannot get url of document " + nodePath, e);
    }
    return url;
  }

  protected String getFormattedDate(String createdDate) {
    try {
      Long createdDateTime = Long.parseLong(createdDate);
      DateTimeFormatter df = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT).withZone(ZoneId.systemDefault());
      return df.format(Instant.ofEpochMilli(createdDateTime));
    } catch (Exception e) {
      LOG.error("Cannot format date for timestamp " + createdDate, e);
      return "";
    }
  }

  protected String getFormattedFileSize(String fileSize) {
    try {
      Long size = Long.parseLong(fileSize);
      return Utils.formatSize(size);
    } catch (Exception e) {
      LOG.error("Cannot format file size " + fileSize, e);
      return "";
    }
  }

  protected String getPreviewUrl(JSONObject jsonHit, SearchContext context) {
    JSONObject hitSource = (JSONObject) jsonHit.get("_source");

    String id = (String) hitSource.get("_id");
    String author = (String) hitSource.get("author");
    String title = (String) hitSource.get("title");
    String repository = (String) hitSource.get("repository");
    String workspace = (String) hitSource.get("workspace");
    String nodePath = (String) hitSource.get("path");
    String fileType = (String) hitSource.get("fileType");

    String restContextName =  WCMCoreUtils.getRestContextName();

    StringBuffer downloadUrl = new StringBuffer();
    downloadUrl.append('/').append(restContextName).append("/jcr/").
            append(WCMCoreUtils.getRepository().getConfiguration().getName()).append('/').
            append(workspace).append(nodePath);

    StringBuilder url = new StringBuilder("javascript:require(['SHARED/documentPreview'], function(documentPreview) {documentPreview.init({doc:{");
    url.append("id:'").append(id).append("',");
    url.append("fileType:'").append(fileType).append("',");
    url.append("title:'").append(title).append("',");
    String linkInDocumentsApp = "";
    try {
      linkInDocumentsApp = documentService.getLinkInDocumentsApp(nodePath);
    } catch (Exception e) {
      LOG.error("Cannot get link in document app for node " + nodePath, e);
    }
    url.append("path:'").append(nodePath)
            .append("', repository:'").append(repository)
            .append("', workspace:'").append(workspace)
            .append("', downloadUrl:'").append(downloadUrl.toString())
            .append("', openUrl:'").append(linkInDocumentsApp)
            .append("'}");
    if(author != null) {
      url.append(",author:{username:'").append(author).append("'}");
    }
    //add void(0) to make firefox execute js
    url.append("})});void(0);");

    return url.toString();
  }

  protected String getImageUrl(String workspace, String nodePath) {
    try {
      String path = nodePath.replaceAll("'", "\\\\'");
      String encodedPath = URLEncoder.encode(path, "utf-8");
      encodedPath = encodedPath.replaceAll ("%2F", "/");    //we won't encode the slash characters in the path
      String restContextName = WCMCoreUtils.getRestContextName();
      String thumbnailImage = "/" + restContextName + "/thumbnailImage/medium/" +
                              WCMCoreUtils.getRepository().getConfiguration().getName() + 
                              "/" + workspace + encodedPath;
      return thumbnailImage;
    } catch (UnsupportedEncodingException e) {
      LOG.error("Cannot encode path " + nodePath, e);
      return "";
    }
  }
}
