/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import io.dockstore.common.BenchmarkTest;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static junit.framework.TestCase.assertTrue;

/**
 * Focuses on creating a large amount of tools to test indexing
 *
 * @author gluu
 */
@Category(BenchmarkTest.class)
@Ignore("more like benchmarking than a test per say")
public class AdvancedIndexingBenchmarkIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        /** do nothing, do not load sample data */
    }

    private static final int TOOL_COUNT = 10;
    private static final int MAX_LABELS_PER_TOOL = 5;
    private static final int MAX_AUTHORS = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedIndexingBenchmarkIT.class);



    private static final String LEXICON = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345674890";
    private static final java.util.Random RAND = new java.util.Random();
    private static final Set<String> IDENTIFIERS = new HashSet<>();
    private DockstoreWebserviceApplication application;
    private Session session;
    private ArrayList<String> fixedStringLabels;
    private ArrayList<String> fixedAuthors;
    private ArrayList<String> fixedOrganization;
    private List<Long> indexTimes;
    private SessionFactory sessionFactory;
    private javax.ws.rs.client.Client client;

    public String randomIdentifier() {
        StringBuilder builder = new StringBuilder();
        while (builder.toString().length() == 0) {
            int length = RAND.nextInt(5) + 5;
            for (int i = 0; i < length; i++) {
                builder.append(LEXICON.charAt(RAND.nextInt(LEXICON.length())));
            }
            if (IDENTIFIERS.contains(builder.toString())) {
                builder = new StringBuilder();
            }
        }
        return builder.toString();
    }

    @Before
    public void setUp() throws Exception {
        com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        client = ClientBuilder.newClient();
        client.register(jacksonJsonProvider);
        application = SUPPORT.getApplication();
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
        fixedStringLabels = randomlyGenerateFixedAuthors();
        fixedAuthors = randomlyGenerateFixedAuthors();
        fixedOrganization = randomlyGenerateFixedAuthors();
        indexTimes = new ArrayList<>();
    }

    @After
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void testCreate10000Tools() throws Exception {
        this.sessionFactory = application.getHibernate().getSessionFactory();
        try {
            Transaction transaction = this.sessionFactory.openSession().getTransaction();
            transaction.begin();

            TokenDAO tokenDAO = new TokenDAO(sessionFactory);
            User user = new User();
            user.setIsAdmin(true);
            user.setAvatarUrl("https://avatars3.githubusercontent.com/u/24548904?v=4");
            user.setCompany("OICR");
            user.setUsername("travistest");
            Token token = new Token();
            token.setUserId(1);
            token.setUsername("travistest");
            token.setContent("iamafakedockstoretoken");
            token.setTokenSource("dockstore");
            tokenDAO.create(token);
            Token token2 = new Token();
            token2.setUserId(1);
            token2.setUsername("travistest");
            token2.setContent("iamafakegithubtoken");
            token2.setTokenSource("github.com");
            tokenDAO.create(token2);
            UserDAO userDAO = new UserDAO(sessionFactory);
            userDAO.create(user);

            transaction.commit();
        } finally {
            session.close();
        }

        session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

        for (int i = 0; i < TOOL_COUNT; i++) {
            createTool();
        }
        Response response = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/published").request().get();
        List<Tool> tools = response.readEntity(new GenericType<List<Tool>>() {
        });
        int actualToolCount = tools.size();
                assertTrue("Supposed to have " + TOOL_COUNT
                        + " tools.  Instead got " + actualToolCount + " tools.", actualToolCount == TOOL_COUNT);
                LOGGER.error("Amount of tools created: " + String.valueOf(actualToolCount));
        for (Long indexTime : indexTimes) {
            LOGGER.error(String.valueOf(indexTime));
        }
    }

    private void buildIndex() {
        Response response = client.target("http://localhost:" + SUPPORT.getLocalPort() + DockstoreWebserviceApplication.GA4GH_API_PATH + "/extended/tools/index").request()
                .post(null);
        long startTime = System.nanoTime();
        String output = response.readEntity(String.class);
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);
        Long convertedDuration = new Long(duration);
        indexTimes.add(convertedDuration);
    }

    private void refresh(long id) {
        Response registerManualResponse = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/" + id + "/refresh")
                .request().header(HttpHeaders.AUTHORIZATION, "Bearer iamafakedockstoretoken").get();
        Tool tool = registerManualResponse.readEntity(Tool.class);
    }

    private void refreshAndBuildIndex(long id) {
        refresh(id);
//        buildIndex();
        addLabels(id);
    }

    private void addLabels(long id) {
        Response registerPutLabelResponse = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/" + id + "/labels")
                .queryParam("labels", randomlyGeneratedQueryLabels()).request()
                .header(HttpHeaders.AUTHORIZATION, "Bearer iamafakedockstoretoken")
                .put(Entity.entity("asdf", MediaType.APPLICATION_JSON_TYPE));
        Tool registeredTool = registerPutLabelResponse.readEntity(Tool.class);
        assertTrue(id == registeredTool.getId());
    }

    // Directly injecting into database to avoid authentication issues
    private void createTool() {
        Tool tool = randomlyGenerateTool();
        Response registerManualResponse = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/registerManual").request()
                .header(HttpHeaders.AUTHORIZATION, "Bearer iamafakedockstoretoken")
                .post(Entity.entity(tool, MediaType.APPLICATION_JSON_TYPE));

        Tool registeredTool = registerManualResponse.readEntity(Tool.class);
        assertTrue(registeredTool.getName().equals(tool.getName()));
        refreshAndBuildIndex(registeredTool.getId());
    }

    private String randomlyGeneratedQueryLabels() {
        String labels;
        SortedSet<String> setLabels = new TreeSet<>();
        for (int i = 0; i < RAND.nextInt(MAX_LABELS_PER_TOOL); i++) {
            setLabels.add(randomLabel());
        }
        String[] arrayLabels = setLabels.toArray(new String[setLabels.size()]);
        labels = String.join(", ", arrayLabels);
        return labels;
    }

    private ArrayList<String> randomlyGenerateFixedAuthors() {
        Set<String> set = new HashSet();
        while (set.size() != MAX_AUTHORS) {
            set.add(randomIdentifier());
        }
        ArrayList<String> authors = new ArrayList<>(set);
        assertTrue(authors.size() == MAX_AUTHORS);
        return authors;
    }

    private String randomLabel() {
        String label = fixedStringLabels.get(RAND.nextInt(fixedStringLabels.size()));
        return label;
    }

    private String randomAuthor() {
        String author = fixedAuthors.get(RAND.nextInt(fixedAuthors.size()));
        return author;
    }

    private String randomOrganization() {
        String organization = fixedOrganization.get(RAND.nextInt(fixedOrganization.size()));
        return organization;
    }

    private Tool randomlyGenerateTool() {
        Tool tool = new Tool();
        tool.setAuthor(randomAuthor());
        tool.setDescription((randomIdentifier()));
        tool.setEmail(randomIdentifier());
        tool.setLastUpdated(new Date());
        tool.setGitUrl("https://github.com/" + randomIdentifier() + "/" + randomIdentifier());
        tool.setMode(ToolMode.MANUAL_IMAGE_PATH);
        tool.setName(randomIdentifier());
        tool.setToolname(randomIdentifier());
        tool.setNamespace(randomOrganization());
        tool.setRegistry(randomlyGeneratedRegistry());
        tool.setLastBuild(new Date());
        // Setting it always true because otherwise the build index won't do anything
        tool.setIsPublished(true);
        tool.setDefaultDockerfilePath(randomIdentifier());
        tool.setDefaultCwlPath(randomIdentifier());
        tool.setDefaultWdlPath(randomIdentifier());
        tool.setPrivateAccess(RAND.nextBoolean());
        tool.setToolname(randomIdentifier());
        return tool;
    }

    private String randomlyGeneratedRegistry() {
        // Can't use Quay.io or else tool creation will fail
        Registry[] registries = { Registry.AMAZON_ECR, Registry.DOCKER_HUB, Registry.GITLAB };
        int length = registries.length;
        int random = RAND.nextInt(length);
        assertTrue(random >= 0 && random < length);
        if (random == 0) {
            return "test.dkr.ecr.test.amazonaws.com";
        } else {
            return registries[random].toString();
        }
    }
}



