package org.exoplatform.services.wcm.publication;

import org.exoplatform.services.ecm.publication.PublicationPlugin;
import org.exoplatform.services.ecm.publication.PublicationService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.wcm.core.NodeLocation;

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

  WCMComposer               wcmComposer = null;

  PublicationService        publicationService;

  WCMComposerImpl           wcmComposerService;

  WCMComposerImpl           wcmComposerImplSpy;

  String                    workspace;

  String                    folderPath;

  NodeLocation              nodeLocation;

  HashMap<String, String>   filters;

  private PublicationPlugin plugin_;

  public void setUp() throws Exception {
    super.setUp();
    wcmComposer = container.getComponentInstanceOfType(WCMComposer.class);
    publicationService = WCMCoreUtils.getService(PublicationService.class);
    wcmComposerService = WCMCoreUtils.getService(WCMComposerImpl.class);
    plugin_ = new DumpPublicationPlugin();
    plugin_.setName("Simple");
    plugin_.setDescription("Simple");
    publicationService.addPublicationPlugin(plugin_);
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

  public void testgetPaginatedContentsShouldRemoveDuplicatedNodes() throws Exception {
    populateMultiLangContent();
    Result result = wcmComposerImplSpy.getPaginatedContents(nodeLocation, filters, sessionProvider);
    assertEquals(1, result.getNodes().size());
  }

  public void testgetContentsShouldRemoveDuplicatedNodes() throws Exception {
    populateMultiLangContent();
    List<Node> nodes = wcmComposerImplSpy.getContents(workspace, folderPath, filters, sessionProvider);
    assertEquals(1, nodes.size());
  }
  
  public void populateMultiLangContent() throws Exception {
    HashMap<String, String> context = new HashMap<String, String>();
    context.put("visibility", "true");

    filters = new HashMap<>();
    folderPath = "repository:collaboration:/sites content/live";
    nodeLocation = NodeLocation.getNodeLocationByExpression(folderPath);
    workspace = nodeLocation.getWorkspace();
    Node rootNode = session.getRootNode();// getNode("/sites content/live");

    Node nodeone_en = rootNode.addNode("nodeone_en", NodetypeConstant.EXO_WEBCONTENT);
    nodeone_en.setProperty(NodetypeConstant.EXO_LANGUAGE, "en");
    nodeone_en.setProperty(NodetypeConstant.EXO_TITLE, "node one en");
    publicationService.enrollNodeInLifecycle(nodeone_en, plugin_.getLifecycleName());
    publicationService.changeState(nodeone_en, PUBLISHED, context);

    Node nodeone_fr = rootNode.addNode("nodeone_fr", NodetypeConstant.EXO_WEBCONTENT);
    nodeone_fr.setProperty(NodetypeConstant.EXO_LANGUAGE, "fr");
    nodeone_fr.setProperty(NodetypeConstant.EXO_TITLE, "node one fr");
    publicationService.enrollNodeInLifecycle(nodeone_fr, plugin_.getLifecycleName());
    publicationService.changeState(nodeone_fr, PUBLISHED, context);
    NodeIterator iter = rootNode.getNodes();
    wcmComposerImplSpy = Mockito.spy(wcmComposerService);
    int i = 0;
    while (i < 5) {
      iter.next();
      i++;
    }
    List<Node> l1 = new ArrayList<Node>() {
      {
        add(nodeone_fr);
      }
    };

    List<Node> l2 = new ArrayList<Node>() {
      {
        add(nodeone_en);
      }
    };

    Mockito.doReturn(iter)
           .when(wcmComposerImplSpy)
           .getViewableContents(Mockito.any(String.class),
                                Mockito.any(String.class),
                                Mockito.any(HashMap.class),
                                Mockito.any(SessionProvider.class),
                                Mockito.any(Boolean.class));
    Mockito.doReturn(l1).when(wcmComposerImplSpy).getRealTranslationNodes(nodeone_en);
    Mockito.doReturn(l2).when(wcmComposerImplSpy).getRealTranslationNodes(nodeone_fr);
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

}
