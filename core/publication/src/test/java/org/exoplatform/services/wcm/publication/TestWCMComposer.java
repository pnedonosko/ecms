package org.exoplatform.services.wcm.publication;

import org.exoplatform.services.ecm.publication.PublicationPlugin;
import org.exoplatform.services.ecm.publication.PublicationService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.wcm.core.NodeLocation;
import org.exoplatform.portal.webui.util.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.jcr.Node;
import javax.jcr.NodeIterator;

import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.mockito.Mockito;

import static org.exoplatform.services.wcm.publication.PublicationDefaultStates.PUBLISHED;
import static org.exoplatform.services.wcm.publication.WCMComposer.*;

public class TestWCMComposer extends BasePublicationTestCase {

  WCMComposerImpl           wcmComposer = null;
  
  String                    workspace;

  String                    folderPath;

  NodeLocation              nodeLocation;

  HashMap<String, String>   filters;

  private PublicationPlugin plugin_;

  Node                      nodeone_en;

  Node                      nodeone_fr;

  public void setUp() throws Exception {
    super.setUp();
    wcmComposer = WCMCoreUtils.getService(WCMComposerImpl.class);
    applySystemSession();
  }

	/**
	 * test getContent for an authorized node
	 * @throws Exception
	 */
	public void testGetContentAuthorized() throws Exception {
		// Given
		HashMap<String, String> filters = new HashMap<>();

		String nodeIdentifier = "/sites content";

		// When
		Node node = wcmComposer.getContent(COLLABORATION_WS, nodeIdentifier, filters, sessionProvider);

		// Then
		assertNotNull(node);
	}

	/**
	 * test getContent for an non authorized node
	 * @throws Exception
	 */
	public void testGetContentNotAuthorized() throws Exception {
		// Given
		HashMap<String, String> filters = new HashMap<>();

		String nodeIdentifier = "/exo:application";

		// When
		Node node = wcmComposer.getContent(COLLABORATION_WS, nodeIdentifier, filters, sessionProvider);

		// Then
		assertNull(node);
	}

	/**
	 * test getPaginatedContents result size for an authorized node
	 * @throws Exception
	 */
	public void testShouldReturnPublicContentsWhenAdminUser() throws Exception {
		// Given
		HashMap<String, String> filters = new HashMap<>();
		String folderPath = "repository:collaboration:/sites content/live/web contents/site artifacts";
		NodeLocation  nodeLocation = NodeLocation.getNodeLocationByExpression(folderPath);

		// When
		Result result = wcmComposer.getPaginatedContents(nodeLocation, filters, sessionProvider);

		// Then
		assertEquals(1, result.getNumTotal());
	}

	public void testShouldReturnPublicContentsWhenPublicModeAndNonAdminUser() throws Exception {
		// When
		applyUserSession("marry", "gtn", "collaboration");
		HashMap<String, String> filters = new HashMap<>();
		filters.put(FILTER_MODE, MODE_LIVE);
		filters.put(FILTER_VISIBILITY, VISIBILITY_PUBLIC);
		String folderPath = "repository:collaboration:/sites content/live/web contents/site artifacts";
		NodeLocation  nodeLocation = NodeLocation.getNodeLocationByExpression(folderPath);

		// When
		Result result = wcmComposer.getPaginatedContents(nodeLocation, filters, sessionProvider);

		// Then
		assertEquals(1, result.getNumTotal());
	}

	/**
	 * test getPaginatedContents result size when FILTER_TOTAL is set
	 * @throws Exception
	 */
	public void testPaginatedContentsWithFilter() throws Exception{
		// Given
		HashMap<String, String> filters = new HashMap<>();
		String folderPath = "repository:collaboration:/sites content/live";
		NodeLocation  nodeLocation = NodeLocation.getNodeLocationByExpression(folderPath);
		//test if FILTER_TOTAL value is already set
		filters.put(WCMComposer.FILTER_TOTAL,"2");

		// When
		Result result = wcmComposer.getPaginatedContents(nodeLocation, filters, sessionProvider);

		// Then
		assertEquals(2, result.getNumTotal());
	}
	
  public void tearDown() throws Exception {
    super.tearDown();
  }

}