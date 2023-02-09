/*
 *    Copyright 2018 OICR
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import io.dockstore.common.BenchmarkTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.ToolTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
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
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Focuses on creating a large amount of tools to test indexing
 *
 * @author gluu
 */
@Tag(BenchmarkTest.NAME)
@Tag(ToolTest.NAME)
@Disabled("more like benchmarking than a test per say")
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class AdvancedIndexingBenchmarkIT extends BaseIT {

    private static final int TOOL_COUNT = 10;
    private static final int MAX_LABELS_PER_TOOL = 5;
    private static final int MAX_AUTHORS = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedIndexingBenchmarkIT.class);
    private static final String LEXICON = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345674890";
    private static final java.util.Random RAND = new java.util.Random();
    private static final Set<String> IDENTIFIERS = new HashSet<>();
    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();
    private DockstoreWebserviceApplication application;
    private Session session;
    private ArrayList<String> fixedStringLabels;
    private ArrayList<String> fixedAuthors;
    private ArrayList<String> fixedOrganization;
    private List<Long> indexTimes;
    private SessionFactory sessionFactory;
    private javax.ws.rs.client.Client client;

    /** do nothing, do not load sample data */
    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
    }

    private String randomIdentifier() {
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

    @BeforeEach
    public void setUp() {
        com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
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

    @AfterEach
    public void tearDown() {
        client.close();
    }

    @Test
    void testCreate10000Tools() {
        this.sessionFactory = application.getHibernate().getSessionFactory();
        try {
            Transaction transaction = this.sessionFactory.openSession().getTransaction();
            transaction.begin();

            TokenDAO tokenDAO = new TokenDAO(sessionFactory);
            User user = new User();
            user.setIsAdmin(true);
            user.setUsername("travistest");
            Token token = new Token();
            token.setUserId(1);
            token.setUsername("travistest");
            token.setContent("iamafakedockstoretoken");
            token.setTokenSource(TokenType.DOCKSTORE);
            tokenDAO.create(token);
            Token token2 = new Token();
            token2.setUserId(1);
            token2.setUsername("travistest");
            token2.setContent("iamafakegithubtoken");
            token2.setTokenSource(TokenType.GITHUB_COM);
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
        assertEquals(TOOL_COUNT, actualToolCount, "Supposed to have " + TOOL_COUNT + " tools.  Instead got " + actualToolCount + " tools.");
        LOGGER.error("Amount of tools created: " + String.valueOf(actualToolCount));
        for (Long indexTime : indexTimes) {
            LOGGER.error(String.valueOf(indexTime));
        }
    }

    private void refresh(long id) {
        Response registerManualResponse = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/" + id + "/refresh")
            .request().header(HttpHeaders.AUTHORIZATION, "Bearer iamafakedockstoretoken").get();
        Tool tool = registerManualResponse.readEntity(Tool.class);
    }

    private void refreshAndBuildIndex(long id) {
        refresh(id);
        addLabels(id);
    }

    private void addLabels(long id) {
        Response registerPutLabelResponse = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/" + id + "/labels")
            .queryParam("labels", randomlyGeneratedQueryLabels()).request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer iamafakedockstoretoken").put(Entity.entity("asdf", MediaType.APPLICATION_JSON_TYPE));
        Tool registeredTool = registerPutLabelResponse.readEntity(Tool.class);
        assertEquals(id, registeredTool.getId());
    }

    // Directly injecting into database to avoid authentication issues
    private void createTool() {
        Tool tool = randomlyGenerateTool();
        Response registerManualResponse = client.target("http://localhost:" + SUPPORT.getLocalPort() + "/containers/registerManual")
            .request().header(HttpHeaders.AUTHORIZATION, "Bearer iamafakedockstoretoken")
            .post(Entity.entity(tool, MediaType.APPLICATION_JSON_TYPE));

        Tool registeredTool = registerManualResponse.readEntity(Tool.class);
        assertEquals(registeredTool.getName(), tool.getName());
        refreshAndBuildIndex(registeredTool.getId());
    }

    private String randomlyGeneratedQueryLabels() {
        String labels;
        SortedSet<String> setLabels = new TreeSet<>();
        for (int i = 0; i < RAND.nextInt(MAX_LABELS_PER_TOOL); i++) {
            setLabels.add(randomLabel());
        }
        String[] arrayLabels = setLabels.toArray(new String[0]);
        labels = String.join(", ", arrayLabels);
        return labels;
    }

    private ArrayList<String> randomlyGenerateFixedAuthors() {
        Set<String> set = new HashSet<>();
        while (set.size() != MAX_AUTHORS) {
            set.add(randomIdentifier());
        }
        ArrayList<String> authors = new ArrayList<>(set);
        assertEquals(MAX_AUTHORS, authors.size());
        return authors;
    }

    private String randomLabel() {
        return fixedStringLabels.get(RAND.nextInt(fixedStringLabels.size()));
    }

    private String randomAuthor() {
        return fixedAuthors.get(RAND.nextInt(fixedAuthors.size()));
    }

    private String randomOrganization() {
        return fixedOrganization.get(RAND.nextInt(fixedOrganization.size()));
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



