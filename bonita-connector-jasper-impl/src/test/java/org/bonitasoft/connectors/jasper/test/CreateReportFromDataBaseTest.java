/**
 * Copyright (C) 2009-2012 BonitaSoft S.A.
 * BonitaSoft, 31 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.bonitasoft.connectors.jasper.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bonitasoft.connectors.jasper.CreateReportFromDataBase;
import org.bonitasoft.engine.api.APIAccessor;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.document.DocumentNotFoundException;
import org.bonitasoft.engine.bpm.document.DocumentValue;
import org.bonitasoft.engine.bpm.document.impl.DocumentImpl;
import org.bonitasoft.engine.connector.Connector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.engine.connector.EngineExecutionContext;
import org.bonitasoft.engine.exception.BonitaException;
import org.bonitasoft.engine.io.IOUtil;
import org.bonitasoft.engine.test.annotation.Cover;
import org.bonitasoft.engine.test.annotation.Cover.BPMNConcept;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * @author Jordi Anguela, Yanyan Liu
 */
public class CreateReportFromDataBaseTest {

    // input parameters
    final String DB_DRIVER = "dbDriver";

    final String JDBC_URL = "jdbcUrl";

    final String USER = "user";

    final String PASSWORD = "password";

    final String JRXML_DOC = "jrxmlDocument";

    final String PARAMETERS = "parameters";

    final String OUTPUT_REPORT_DOC = "outputReportDocument";

    final String OUTPUT_FORMAT = "outputFormat";

    private static final String WRONG_DB_DRIVER = "com.mysql.jdbc.DriverWRONG";

    private static final String WRONG_JRXML_DOC = "wrongJrxml";

    private static final String WRONG_JDBC_URL = "jdbc:mysql://argyweb.com/wrong_database";

    private static final String WRONG_USERNAME = "wrong_user_name";

    private static final String WRONG_PASSWORD = "wrong_password";

    protected static final Logger LOG = Logger.getLogger(CreateReportFromDataBaseTest.class.getName());

    EngineExecutionContext engineExecutionContext;

    APIAccessor apiAccessor;

    ProcessAPI processAPI;

    @Rule
    public TestRule testWatcher = new TestWatcher() {

        @Override
        public void starting(final Description d) {
            LOG.warning("==== Starting test: " + this.getClass().getName() + "." + d.getMethodName() + "() ====");
        }

        @Override
        public void failed(final Throwable e, final Description d) {
            LOG.warning("==== Failed test: " + this.getClass().getName() + "." + d.getMethodName() + "() ====");
        }

        @Override
        public void succeeded(final Description d) {
            LOG.warning("==== Succeeded test: " + this.getClass().getName() + "." + d.getMethodName() + "() ====");
        }

    };

    @BeforeClass
    public static void setup() throws IOException {
        final File root = File.createTempFile("tmp", ".txt").getParentFile();
        for (File f : root.listFiles()) {
            if (f.getName().matches(".*jasperReport.*(pdf|xml|html)")) {
                f.delete();
            }
            if (f.getName().matches(".*jasperReport.*html_files")) {
                if (f.isDirectory()) {
                    for (File fSon : f.listFiles())
                        fSon.delete();
                    f.delete();
                }
            }
            root.delete();
        }

    }

    /**
     * test good parameters that will not cause fault
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            jira = "ENGINE-687", story = "Tests that parameters of the connector are validated")
    @Test
    // @Ignore("Need a report1.jrxml + a filled up database....")
    public void testGoodParameters() throws Exception {
        getMockedContext();
        final Connector connector = getWorkingConnector("pdf");
        connector.validateInputParameters();
    }

    /**
     * test null parameter that will cause fault
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails if driver is null")
    @Test(expected = ConnectorValidationException.class)
    public void testNullParameter() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final String wrongDbDriver = null;
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(DB_DRIVER, wrongDbDriver);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test wrong database driver. make sure provide a wrong database driver
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails driver is invalid")
    @Test(expected = ConnectorValidationException.class)
    public void testWrongDbDriver() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final String wrongDbDriver = WRONG_DB_DRIVER;
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(DB_DRIVER, wrongDbDriver);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test wrong jrxml doc.
     * 
     * @throws BonitaException
     * 
     */

    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails if jrxmlDoc i not the name of a valid jrxml document")
    @Test(expected = ConnectorValidationException.class)
    public void testWrongJrxmlDocument() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final String wrongJrxmlDoc = WRONG_JRXML_DOC;
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(JRXML_DOC, wrongJrxmlDoc);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test wrong JDBC Url. please provide a wrong JDBC Url in config.properties
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails db url is invalid")
    @Test(expected = ConnectorValidationException.class)
    public void testWrongJdbcUrl() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final String wrongJdbcUrl = WRONG_JDBC_URL;
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(JDBC_URL, wrongJdbcUrl);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test wrong database username. please provide a wrong user name in config.properties
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails if db username is invalid")
    @Test(expected = ConnectorValidationException.class)
    public void testWrongDbUser() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        connector.validateInputParameters();
        final String wrongUserName = WRONG_USERNAME;
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(USER, wrongUserName);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test wrong database password. please provide a wrong password in config.properties
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails if db password is invalid")
    @Test(expected = ConnectorValidationException.class)
    public void testWrongDbPassword() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final String wrongPassword = WRONG_PASSWORD;
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(PASSWORD, wrongPassword);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test wrong output format. other format except "xml", "html" and "pdf" will cause an error.
     * 
     * @throws BonitaException
     */
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Tests that validation fails if format is not supported (pdf,html,xml)")
    @Test(expected = ConnectorValidationException.class)
    public void testOutputFormat() throws Exception {
        getMockedContext();

        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final Map<String, Object> inputs = new HashMap<String, Object>();
        String outputFormat = "xml";
        inputs.put(OUTPUT_FORMAT, outputFormat);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        outputFormat = "html";
        inputs.put(OUTPUT_FORMAT, outputFormat);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();

        outputFormat = "other_format";
        inputs.put(OUTPUT_FORMAT, outputFormat);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
    }

    /**
     * test create a report fail.
     */
    @Test
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report" },
            exceptions = ConnectorException.class, jira = "ENGINE-687", story = "Test that if failure to generate reports produces an exception")
    public void testCreateAReportFail() throws Exception {
        getMockedContext();
        final CreateReportFromDataBase connector = getWorkingConnector("pdf");
        final Map<String, Object> inputs = new HashMap<String, Object>();
        inputs.put(PASSWORD, "wrong_password");
        connector.setInputParameters(inputs);
        try {
            connector.execute();
            fail();
        } catch (final ConnectorException e) {
            Assert.assertTrue(true);
        }
    }

    /**
     * test create a report successfully.
     * 
     * @throws Exception
     */
    @Test
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report, html" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Generation of a jasper html report")
    public void testCreateAReportHtml() throws Exception {

        getMockedContext();

        CreateReportFromDataBase connector = getWorkingConnector("hTmL");
        connector.validateInputParameters();
        connector.execute();
        byte[] reportContent = ((DocumentValue) connector.getResult()).getContent();
        File contentFile = File.createTempFile("jasperReportHtml", ".html.zip");
        FileOutputStream fos = new FileOutputStream(contentFile.getCanonicalPath());
        fos.write(reportContent);
        fos.close();
        System.out.println("Rapport : " + contentFile.getCanonicalPath());
        assertTrue(contentFile.isFile());
        assertTrue(contentFile.getName().contains(".zip"));
        assertTrue(contentFile.lastModified() > System.currentTimeMillis() - 60000);
        assertTrue(contentFile.length() > 500L);
    }

    /**
     * test create a report successfully.
     * 
     * @throws Exception
     */
    @Test
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report, xml" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Generation of a jasper xml report")
    public void testCreateAReportXml() throws Exception {

        getMockedContext();

        CreateReportFromDataBase connector = getWorkingConnector("xml");
        connector.validateInputParameters();
        connector.execute();
        byte[] reportContent = ((DocumentValue) connector.getResult()).getContent();
        File contentFile = File.createTempFile("jasperReport", ".xml");
        FileOutputStream fos = new FileOutputStream(contentFile.getCanonicalPath());
        fos.write(reportContent);
        fos.close();
        System.out.println("Rapport : " + contentFile.getCanonicalPath());
        assertTrue(contentFile.isFile());
        assertTrue(contentFile.getName().contains("xml"));
        assertTrue(contentFile.lastModified() > System.currentTimeMillis() - 60000);
        assertTrue(contentFile.length() > 1000L);

    }

    /**
     * test create a report successfully.
     * 
     * @throws Exception
     */
    @Test
    @Cover(classes = { CreateReportFromDataBase.class }, concept = BPMNConcept.CONNECTOR, keywords = { "Jasper Report, pdf" },
            exceptions = ConnectorValidationException.class, jira = "ENGINE-687", story = "Generation of a jasper pdf report")
    public void testCreateAReportPdf() throws Exception {

        getMockedContext();

        CreateReportFromDataBase connector = getWorkingConnector("PDF");
        connector.validateInputParameters();
        connector.execute();
        byte[] reportContent = ((DocumentValue) connector.getResult()).getContent();
        File contentFile = File.createTempFile("jasperReport", ".pdf");
        FileOutputStream fos = new FileOutputStream(contentFile.getCanonicalPath());
        fos.write(reportContent);
        fos.close();
        System.out.println("Rapport : " + contentFile.getCanonicalPath());
        assertTrue(contentFile.isFile());
        assertTrue(contentFile.getName().contains("pdf"));
        assertTrue(contentFile.lastModified() > System.currentTimeMillis() - 60000);
        assertTrue(contentFile.length() > 1000L);

    }

    private void getMockedContext() throws Exception {
        final File root = new File(".");
        final File file = new File(root, "src/test/resources/report1.jrxml");
        byte[] fileContent = IOUtil.getAllContentFrom(file);

        DocumentImpl document = new DocumentImpl();
        document.setCreationDate(new Date());
        document.setId(1);
        document.setProcessInstanceId(1);
        document.setName("jrxml");
        document.setFileName("report.jrxml");
        document.setContentMimeType("application/xml");
        document.setContentStorageId("1L");
        document.setHasContent(true);

        engineExecutionContext = mock(EngineExecutionContext.class);
        apiAccessor = mock(APIAccessor.class);
        processAPI = mock(ProcessAPI.class);
        when(apiAccessor.getProcessAPI()).thenReturn(processAPI);
        when(engineExecutionContext.getProcessInstanceId()).thenReturn(1L);
        when(processAPI.getLastDocument(1L, "jrxml")).thenReturn(document);
        when(processAPI.getLastDocument(1L, WRONG_JRXML_DOC)).thenThrow(
                new DocumentNotFoundException(new Throwable("Document not found : " + WRONG_JRXML_DOC)));
        when(processAPI.getDocumentContent("1L")).thenReturn(fileContent);
    }

    private CreateReportFromDataBase getWorkingConnector(String format) throws Exception {

        final CreateReportFromDataBase connector = new CreateReportFromDataBase();
        final Map<String, Object> inputs = new HashMap<String, Object>();

        // Database access information
        inputs.put(DB_DRIVER, "org.hsqldb.jdbcDriver");
        inputs.put(JDBC_URL, "jdbc:hsqldb:mem:database");
        inputs.put(USER, "sa");
        inputs.put(PASSWORD, "");
        // inputs.put(DB_DRIVER, "com.mysql.jdbc.Driver");
        // inputs.put(JDBC_URL, "jdbc:mysql://localhost/jasper");
        // inputs.put(USER, "jasperUser");
        // inputs.put(PASSWORD, "jasperPwd");

        // Report settings parameters
        // The report1.jrxml file used needs to work that the database contains :
        // - A table named address with this 5 fields :id(integer) firstname(varchar), lastname(varchar), street(varchar), city(varchar)
        inputs.put(JRXML_DOC, "jrxml");
        inputs.put(OUTPUT_REPORT_DOC, "report_" + OUTPUT_FORMAT);
        final List<List<String>> parametersList = new ArrayList<List<String>>();
        final List<String> parameter2List = new ArrayList<String>();
        parameter2List.add("param2");
        parameter2List.add("1");
        parametersList.add(parameter2List);
        inputs.put(PARAMETERS, parametersList);
        inputs.put(OUTPUT_FORMAT, format);

        // System.out.println("Paramètres d'entrée :\n" + inputs.toString());
        connector.setExecutionContext(engineExecutionContext);
        connector.setAPIAccessor(apiAccessor);
        connector.setInputParameters(inputs);

        return connector;
    }

    @Before
    public void createTable() throws Exception {
        Class.forName("org.hsqldb.jdbcDriver");
        Connection conn = DriverManager.getConnection(
                "jdbc:hsqldb:mem:database",
                "sa",
                "");
        Statement statement = conn.createStatement();
        statement.execute("create table address (" +
                "id INTEGER," +
                "firstname VARCHAR(50)," +
                "lastname VARCHAR(50)," +
                "street VARCHAR(50)," +
                "city VARCHAR(50)" +
                ");"
                );
        statement.execute("insert into address values (1, 'Sherlock', 'Holmes', '221B Baker Street ', 'London')");
        statement.execute("insert into address values (2, 'Bruce', 'Wayne', 'Wayne Manor', 'Gotham')");
        statement.execute("insert into address values (3, 'Henry Walton', 'Jones', '38 Adler Avenue', 'Fairfield')");
        conn.close();
    }

    @After
    public void deleteTable() throws ClassNotFoundException, SQLException {
        Class.forName("org.hsqldb.jdbcDriver");
        Connection conn = DriverManager.getConnection(
                "jdbc:hsqldb:mem:database",
                "sa",
                "");
        Statement statement = conn.createStatement();

        statement.execute("drop table address");

        conn.close();
    }
}
