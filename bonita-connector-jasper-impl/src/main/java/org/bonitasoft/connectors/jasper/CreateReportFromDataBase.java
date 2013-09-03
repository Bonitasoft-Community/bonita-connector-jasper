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
package org.bonitasoft.connectors.jasper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.bonitasoft.engine.bpm.document.Document;
import org.bonitasoft.engine.bpm.document.DocumentNotFoundException;
import org.bonitasoft.engine.bpm.document.DocumentValue;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.bonitasoft.engine.io.IOUtil;
import org.bonitasoft.engine.session.InvalidSessionException;

/**
 * @author Jordi Anguela, Yanyan Liu
 */
public class CreateReportFromDataBase extends AbstractConnector {

	// input parameters
	private static final String DB_DRIVER = "dbDriver";

	private static final String JDBC_URL = "jdbcUrl";

	private static final String USER = "user";

	private static final String PASSWORD = "password";

	private static final String JRXML_DOC = "jrxmlDocument";

	private static final String PARAMETERS = "parameters";

	private static final String OUTPUT_FORMAT = "outputFormat";

	// output
	private static final String REPORT_DOC_VALUE = "reportDocValue";

	// Data base configuration
	private String dbDriver;

	private String jdbcUrl;

	private String user;

	private String password;

	// Report settings
	private String jrxmlDocument;

	private byte[] jrxmlContent;

	private Map<String, String> parameters = null;

	private String outputFormat;

	private Logger LOGGER = Logger.getLogger(this.getClass().getName());

	private static Connection conn = null;
	private enum OutputFormat {
		html, pdf, xml

	}


	public Object getResult() {
		return getOutputParameters().get(REPORT_DOC_VALUE);
	}

	@SuppressWarnings("unchecked")
	private void initInputs() {
		dbDriver = (String) getInputParameter(DB_DRIVER);
		LOGGER.info(DB_DRIVER + " " + dbDriver);
		jdbcUrl = (String) getInputParameter(JDBC_URL);
		LOGGER.info(JDBC_URL + " " + jdbcUrl);

		user = (String) getInputParameter(USER);
		LOGGER.info(USER + " " + user);

		password = (String) getInputParameter(PASSWORD);
		LOGGER.info(PASSWORD + " ******");

		jrxmlDocument = (String) getInputParameter(JRXML_DOC);
		LOGGER.info(JRXML_DOC + " " + jrxmlDocument);

		outputFormat = (String) getInputParameter(OUTPUT_FORMAT);
		LOGGER.info(OUTPUT_FORMAT + " " + outputFormat);

		final List<List<Object>> parametersList = (List<List<Object>>) getInputParameter(PARAMETERS);
		parameters = new HashMap<String, String>();
		if (parametersList != null) {
			// System.out.println("initInputs - parameters list :" + parametersList.toString());
			for (List<Object> rows : parametersList) {
				if (rows.size() == 2) {
					Object keyContent = rows.get(0);
					Object valueContent = rows.get(1);
					LOGGER.info("Parameter " + keyContent + " " + valueContent);
					if (keyContent != null && valueContent != null) {
						final String key = keyContent.toString();
						final String value = valueContent.toString();
						parameters.put(key, value);
					}
				}
			}
		}
	}

	@Override
	public void validateInputParameters() throws ConnectorValidationException {
		initInputs();
		final List<String> errors = new ArrayList<String>();
        if (jrxmlDocument == null || jrxmlDocument.trim().length() == 0) {
            errors.add("jrxmlDocument cannot be empty!");
        }

		Long processInstanceId = getExecutionContext().getProcessInstanceId();
		try {
			Document document = getAPIAccessor().getProcessAPI().getLastDocument(processInstanceId, jrxmlDocument);
			if (!document.hasContent() || !document.getContentFileName().matches(".*\\.jrxml")) {
				errors.add("the jrxmlDocument " + document.getName() + " must have for content a jrxml file compatible with jasper v5");
			}
			else {
				jrxmlContent = getAPIAccessor().getProcessAPI().getDocumentContent(document.getContentStorageId());
			}
		} catch (Exception e) {
			errors.add(jrxmlDocument + " is not the name of a document defined in the process");
		}

		outputFormat = outputFormat.trim();
		if (!OutputFormat.html.name().equalsIgnoreCase(outputFormat) && !OutputFormat.pdf.name().equalsIgnoreCase(outputFormat)
				&& !OutputFormat.xml.name().equalsIgnoreCase(outputFormat)) {
			errors.add(outputFormat + " is not supported. Accepted outputFormats are : 'html', 'pdf' or 'xml' !");
		}
		if (!errors.isEmpty()) {
			throw new ConnectorValidationException(this, errors);
		}

		// Load JDBC driver
		// Check that jrxmlFile exists
		// Test database connection
		if(dbDriver != null && !dbDriver.isEmpty() && jdbcUrl != null && !jdbcUrl.isEmpty()){
			try {
				databaseValidations(dbDriver, jrxmlDocument, jdbcUrl, user, password);
			} catch (final ClassNotFoundException e) {
				errors.add("dbDriver JDBC Driver not found!");
			} catch (final DocumentNotFoundException dnfe) {
				errors.add("jrxmlDocument '" + jrxmlDocument + "' not found!");
			} catch (final SQLException e) {
				errors.add("jdbcUrlCannot connect to database. Check 'jdbcUrl', 'user' and 'password' parameters. Message: " + e.getMessage());
			} catch (InvalidSessionException ise) {
				errors.add("InvalidSessionException" + ise.getMessage());
			} catch (IOException ioe) {
				errors.add("IOException" + ioe.getMessage());
			}
		}
		if (!errors.isEmpty()) {
			throw new ConnectorValidationException(this, errors);
		}
	}

	@Override
	protected void executeBusinessLogic() throws ConnectorException {
		try {
			createJasperReportFromDataBase(dbDriver, jdbcUrl, user, password, jrxmlDocument, parameters, outputFormat);
		} catch (final Exception e) {
			throw new ConnectorException(e);
		}
	}

	/**
	 * validate the database
	 * 
	 * @throws InvalidSessionException
	 * @throws IOException
	 * @throws ConnectorValidationException
	 */
	public void databaseValidations(final String dbDriver, final String jrxmlDocument, final String jdbcUrl, final String user, final String password)
			throws ClassNotFoundException, DocumentNotFoundException, SQLException, InvalidSessionException, IOException, ConnectorValidationException {

		// Load JDBC driver
		try {
			Class.forName(dbDriver);
		} catch (final ClassNotFoundException e) {
			if (LOGGER.isLoggable(Level.WARNING)) {
				LOGGER.warning("JDBC Driver not found. dbDriver=" + dbDriver);
			}
			throw e;
		}

		// Test database connection. this method is just for validation. no need close conn in finally code block.
		try {
			conn = DriverManager.getConnection(jdbcUrl, user, password);
			conn.setAutoCommit(false);
		} catch (final SQLException e) {
			if (LOGGER.isLoggable(Level.WARNING)) {
				LOGGER.warning("Connection error: " + e.getMessage());
			}
			try {
				if (conn != null) {
					conn.close();
				}
			} catch (final Exception e1) {
				if (LOGGER.isLoggable(Level.WARNING)) {
					LOGGER.warning("Exception during finally. Message: " + e1.getMessage());
				}
			}
			throw e;
		}

	}

	private byte[] makeZip(String dir, List<String> filesList) throws IOException {
		File outFolder = File.createTempFile("htmlZip", ".zip");
		outFolder.deleteOnExit();
		ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFolder)));
		for (String strFile : filesList) {
			byte[] data = new byte[1000];
			out.putNextEntry(new ZipEntry(strFile.replaceFirst(".*" + dir + ".{1}", "")));
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(strFile));
			int count;
			while ((count = in.read(data)) != -1)
			{
				out.write(data, 0, count);
			}
			in.close();
			out.closeEntry();
		}

		out.flush();
		out.close();
		return IOUtil.getAllContentFrom(outFolder);
	}

	private List<String> selectZipFiles(File srcDir, String pattern, Long delay) throws Exception {
		List<String> fList = new ArrayList<String>();
		if (!srcDir.isDirectory()) {
			throw new Exception(srcDir + " must be a directory");
		}
		for (File f : srcDir.listFiles()) {
			if (f.getName().contains(pattern) && f.lastModified() > (System.currentTimeMillis() - delay)) {
				if (f.isDirectory()) {
					fList.addAll(selectZipFiles(f, "", 5000L));
				}
				else {
					fList.add(f.getCanonicalPath());
				}
			}
		}
		return fList;
	}

	public void createJasperReportFromDataBase(final String dbDriver, final String jdbcUrl, final String user, final String password,
			final String jrxmlDocument, final Map<String, String> parameters, final String outputFormat) throws Exception {
		if (LOGGER.isLoggable(Level.INFO)) {
			LOGGER.info("Creating a new Jasper Report from database");
		}

		try {
			final JasperReport report = JasperCompileManager.compileReport(new ByteArrayInputStream(jrxmlContent));
			final Map<String, Object> typedParameters = getTypedParameters(report, parameters);
			JasperPrint print = null;
			if(conn != null){
				 print = JasperFillManager.fillReport(report, typedParameters, conn);
			}else{
				 print = JasperFillManager.fillReport(report, typedParameters);
			}

			byte[] content;
			ByteArrayOutputStream outStream = new ByteArrayOutputStream();
			String mimeType = "";
			String suffix = "." + outputFormat;
			// Export file to selected document
			if (OutputFormat.pdf.name().equalsIgnoreCase(outputFormat)) {
				JasperExportManager.exportReportToPdfStream(print, outStream);
				content = outStream.toByteArray();
				mimeType = "application/pdf";
			}
			else if (OutputFormat.html.name().equalsIgnoreCase(outputFormat)) {
				File htmlFile = File.createTempFile("jasperReport", ".html");
				htmlFile.deleteOnExit();
				JasperExportManager.exportReportToHtmlFile(print, htmlFile.getCanonicalPath());
				List<String> filesForZip = selectZipFiles(htmlFile.getParentFile(), "html", 10000L);
				content = makeZip(htmlFile.getParentFile().getName(), filesForZip);
				mimeType = "application/zip";
				suffix = suffix + ".zip";
			}
			else if (OutputFormat.xml.name().equalsIgnoreCase(outputFormat)) {
				JasperExportManager.exportReportToXmlStream(print, outStream);
				content = outStream.toByteArray();
				mimeType = "application/xml";
			}
			else {
				final String errorMessage = outputFormat + " is not supported. Accepted outputFormats are : 'html', 'pdf' or 'xml' !";
				if (LOGGER.isLoggable(Level.WARNING)) {
					LOGGER.warning(errorMessage);
				}
				throw new IllegalArgumentException(errorMessage);
			}

			DocumentValue docValue = new DocumentValue(content, mimeType, "jasper_report" + suffix);
			setOutputParameter(REPORT_DOC_VALUE, docValue);
			// Visualize new report file
			// JasperViewer.viewReport(print, false);
		} catch (final Exception e) {
			if (LOGGER.isLoggable(Level.WARNING)) {
				LOGGER.warning(e.toString());
			}
			throw e;
		} finally {
			// Cleanup before exit.
			try {
				if (conn != null) {
					conn.rollback();
					conn.close();
				}
			} catch (final Exception e) {
				if (LOGGER.isLoggable(Level.WARNING)) {
					LOGGER.warning("Exception during finally. Message: " + e.getMessage());
				}
				throw e;
			}
		}
	}

	private Map<String, Object> getTypedParameters(final JasperReport report, final Map<String, String> parameters) {
		final Map<String, Object> typedParameters = new HashMap<String, Object>();
		for (final JRParameter param : report.getParameters()) {
			final String paramName = param.getName();
			final String paramType = param.getValueClassName();
			final String value = parameters.get(paramName);
			if (value != null && paramType != null) {
				if (paramType.equals(String.class.getName())) {
					typedParameters.put(paramName, value);
				} else if (paramType.equals(Integer.class.getName())) {
					try {
						final Integer typedValue = Integer.parseInt(value);
						typedParameters.put(paramName, typedValue);
					} catch (final NumberFormatException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + Integer.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(Short.class.getName())) {
					try {
						final Short typedValue = Short.parseShort(value);
						typedParameters.put(paramName, typedValue);
					} catch (final NumberFormatException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + Short.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(Long.class.getName())) {
					try {
						final Long typedValue = Long.parseLong(value);
						typedParameters.put(paramName, typedValue);
					} catch (final NumberFormatException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + Long.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(Double.class.getName())) {
					try {
						final Double typedValue = Double.parseDouble(value);
						typedParameters.put(paramName, typedValue);
					} catch (final NumberFormatException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + Double.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(Float.class.getName())) {
					try {
						final Float typedValue = Float.parseFloat(value);
						typedParameters.put(paramName, typedValue);
					} catch (final NumberFormatException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + Float.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(BigDecimal.class.getName())) {
					try {
						final BigDecimal typedValue = new BigDecimal(value);
						typedParameters.put(paramName, typedValue);
					} catch (final NumberFormatException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + BigDecimal.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(Date.class.getName())) {
					try {
						final Date typedValue = new SimpleDateFormat().parse(value);
						typedParameters.put(paramName, typedValue);
					} catch (final ParseException e) {
						throw new IllegalArgumentException("Invalid parameter type for " + paramName + ": " + Date.class.getName()
								+ " value is expected, current is " + value);
					}
				} else if (paramType.equals(Boolean.class.getName())) {
					final Boolean typedValue = Boolean.parseBoolean(value);
					typedParameters.put(paramName, typedValue);
				}
			}
		}
		return typedParameters;
	}

}
