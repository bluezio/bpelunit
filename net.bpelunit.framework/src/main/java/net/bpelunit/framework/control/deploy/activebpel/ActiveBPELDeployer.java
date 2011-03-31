/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 * 
 */
package net.bpelunit.framework.control.deploy.activebpel;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.soap.SOAPException;

import net.bpelunit.framework.control.ext.IBPELDeployer;
import net.bpelunit.framework.control.ext.IBPELDeployer.IBPELDeployerCapabilities;
import net.bpelunit.framework.control.ext.IDeployment;
import net.bpelunit.framework.exception.DeploymentException;
import net.bpelunit.framework.model.ProcessUnderTest;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.log4j.Logger;

/**
 * ActiveBPEL Deployer - deploys a process to an ActiveBPEL server.
 * 
 * By default, the application server running ActiveBPEL is considered to be
 * listening at http://localhost:8080, and deployment archives are stored at
 * $CATALINA_HOME/bpr, where $CATALINA_HOME is an environment variable that
 * needs to be set to the home directory of the application server.
 *
 * @author Philip Mayer, Antonio Garcia-Dominguez
 */
@IBPELDeployerCapabilities(canDeploy = true, canMeasureTestCoverage = true)
public class ActiveBPELDeployer implements IBPELDeployer {

	/*
	 * Encapsulates the results from an HTTP request: status code and response
	 * body
	 */
	private static class RequestResult {
		public int statusCode;
		public String responseBody;
	}

	// Strings which enclose the number of deployment errors in the summary
	private static final String ERRCOUNT_START = "&lt;deploymentSummary numErrors=&quot;";
	private static final String ERRCOUNT_END   = "&quot";

	/* By default, ActiveBPEL is assumed to be at localhost:8080 */
	private static final int DEFAULT_ENGINE_PORT = 8080;
	private static final String DEFAULT_ENGINE_HOST = "localhost";
	private static final String DEFAULT_ENGINE_PROTOCOL = "http";

	/* Default URLs for the deployment and administration web services */
	static final String DEFAULT_DEPLOYMENT_PATH = "/active-bpel/services/DeployBPRService";
	static final String DEFAULT_ADMIN_PATH = "/active-bpel/services/ActiveBpelAdmin";

	/*
	 * Name of the environment variable which will be used to build the
	 * deployment directory if no configuration has been specified.
	 */
	static final String DEFAULT_APPSERVER_DIR_ENVVAR = "CATALINA_HOME";

	private Logger fLogger = Logger.getLogger(getClass());

	// Path to the deployed BPR file
	private String fDeployedFile;
	// Directory where ActiveBPEL places the deployed BPRs
	private String fDeploymentDirectory;
	/* Original BPR, as created by the user (when specifying BPRFile)
	   or by BPELUnit (when specifying BPELFile) */
	private File fDeploymentArchive;
	/* BPEL file to be used for creating the BPR file, if the BPELFile
	   option is used. */
	private File fBpelFile;
	/* Indicate whether the BPR has been already generated or not,
	 * in case we are generating it automatically. */
	private boolean fDeploymentArchiveIsGenerated = false;

	/* Where In The World Is ActiveBPEL? */
	private ProcessUnderTest put;
	private int fEnginePort = DEFAULT_ENGINE_PORT;
	private String fEngineHost = DEFAULT_ENGINE_HOST;
	private String fEngineProto = DEFAULT_ENGINE_PROTOCOL;
	private String fDeploymentServicePath = DEFAULT_DEPLOYMENT_PATH;
	private String fAdminServicePath = DEFAULT_ADMIN_PATH;

	/* For unit testing */
	static int _terminatedProcessCount = 0;

	@IBPELDeployerOption
	public void setBPRFile(String bprFile) {
		this.fDeploymentArchive = new File(bprFile);
	}

	@IBPELDeployerOption
	public void setBPELFile(String bpelFile) {
		this.fBpelFile = new File(bpelFile);
	}

	@IBPELDeployerOption(testSuiteSpecific = false)
	public void setDeploymentDirectory(String deploymentDirectory) {
		if (deploymentDirectory != null) {
			this.fDeploymentDirectory = deploymentDirectory;
		}
	}

	@IBPELDeployerOption(testSuiteSpecific = false, defaultValue = "" + DEFAULT_ENGINE_PORT)
	public void setEnginePort(String port) {
		if (port != null) {
			setEnginePort(Integer.valueOf(port));
		}
	}

	/* Additional version, for accessing BPELUnit programmatically. */
	public void setEnginePort(int port) {
		this.fEnginePort  = port;
	}

	@IBPELDeployerOption(testSuiteSpecific = false, defaultValue = DEFAULT_ENGINE_HOST)
	public void setEngineHost(String host) {
		if (host != null) {
			this.fEngineHost = host;
		}
	}

	@IBPELDeployerOption(testSuiteSpecific = false, defaultValue = DEFAULT_ENGINE_PROTOCOL)
	public void setEngineProtocol(String proto) {
		if (proto != null) {
			this.fEngineProto = proto;
		}
	}

	@IBPELDeployerOption(defaultValue = DEFAULT_DEPLOYMENT_PATH)
	public void setDeploymentAdminServicePath(String deploymentAdminServiceURL) {
		if (deploymentAdminServiceURL != null) {
			this.fDeploymentServicePath = deploymentAdminServiceURL;
		}
	}

	@IBPELDeployerOption(defaultValue = DEFAULT_ADMIN_PATH)
	public void setAdministrationServicePath(String adminServiceURL) {
		if (adminServiceURL != null) {
			this.fAdminServicePath = adminServiceURL;
		}
	}

	public URL getDeploymentAdminServiceURL() throws DeploymentException {
		try {
			return new URL(fEngineProto, fEngineHost, fEnginePort, fDeploymentServicePath);
		} catch (MalformedURLException e) {
			throw new DeploymentException("Bad deployment service URL", e);
		}
	}

	public URL getAdministrationServiceURL() throws DeploymentException {
		try {
			return new URL(fEngineProto, fEngineHost, fEnginePort, fAdminServicePath);
		} catch (MalformedURLException e) {
			throw new DeploymentException("Bad administration service URL", e);
		}
	}

	public void deploy(String pathToTest, ProcessUnderTest put)
			throws DeploymentException {
		this.put = put;

		fLogger.info("ActiveBPEL deployer got request to deploy " + put);

		if (fDeploymentDirectory == null
				&& System.getenv(DEFAULT_APPSERVER_DIR_ENVVAR) != null) {
			fDeploymentDirectory = System.getenv(DEFAULT_APPSERVER_DIR_ENVVAR)
					+ File.separator + "bpr";
		}
		check(fDeploymentDirectory, "deployment directory path");

		// changed the way the archive location is obtained.
		String archivePath = getArchiveLocation(put);
		File uploadingFile = new File(archivePath);
		if (!uploadingFile.exists()) {
			throw new DeploymentException(
				"ActiveBPEL deployer could not find BPR file " + uploadingFile);
		}
		File resultingFile = new File(fDeploymentDirectory, uploadingFile.getName());

		// Upload it.
		RequestEntity re;
		try {
			re = new BPRDeployRequestEntity(uploadingFile);
		} catch (IOException e) {
			throw new DeploymentException(
					"An input/output error occured in ActivBPEL deployer when deploying: "
							+ e.getMessage());
		} catch (SOAPException e) {
			throw new DeploymentException(
					"An error occurred while creating SOAP message for ActiveBPEL deployment: "
							+ e.getMessage());
		}

		fLogger.info("ActiveBPEL deployer about to send SOAP request to deploy " + put);
		try {
			RequestResult result = sendRequestToActiveBPEL(
				getDeploymentAdminServiceURL().toExternalForm(), re);

			if (result.statusCode < 200 || result.statusCode > 299 || errorsInSummary(result.responseBody)) {
				throw new DeploymentException(
						"ActiveBPEL Server reported a Deployment Error: "
								+ result.responseBody);
			}

			// done.
			fDeployedFile = resultingFile.toString();
		} catch (HttpException e) {
			throw new DeploymentException(
					"Problem contacting the ActiveBPEL Server: "
							+ e.getMessage(), e);
		} catch (IOException e) {
			throw new DeploymentException(
					"Problem contacting the ActiveBPEL Server: "
							+ e.getMessage(), e);
		} finally {
			if (uploadingFile.exists()) {
				uploadingFile.delete();
			}
		}
	}

	public void undeploy(String path, ProcessUnderTest deployable)
			throws DeploymentException {
		// undeploy may be called even if deploy was not successful
		if (fDeployedFile == null)
			return;

		File bprFile = new File(fDeployedFile);
		if (fDeployedFile == null)
			throw new DeploymentException("Cannot undeploy BPR for Deployable "
					+ deployable + ": Metadata about file name not found.");
		if (!bprFile.exists())
			throw new DeploymentException("Cannot undeploy BPR for Deployable "
					+ deployable + ": File " + bprFile + " not found.");

		if (!bprFile.delete())
			throw new DeploymentException("Cannot undeploy BPR for Deployable "
					+ deployable + ": File " + bprFile
					+ " could not be deleted.");

		try {
			terminateAllRunningProcesses(deployable.getName());
		} catch (Exception e) {
			e.printStackTrace();
			throw new DeploymentException(e.getLocalizedMessage());
		}
	}

	public String getArchiveLocation(ProcessUnderTest put) throws DeploymentException {
		if (this.fDeploymentArchiveIsGenerated) {
			try {
				return fDeploymentArchive.getCanonicalPath();
			} catch (IOException e) {
				throw new DeploymentException(
					"Could not compute the canonical path for the BPR file", e);
			}
		}

		try {
			final String pathToTest = put.getBasePath();

			// If fBpelFile or fDeploymentArchive have relative paths, convert them
			// to absolute paths taking the directory of the .bpts file as reference
			if (fBpelFile != null && !fBpelFile.isAbsolute()) {
				fBpelFile = new File(pathToTest, fBpelFile.getPath());
			}
			if (fDeploymentArchive != null && !fDeploymentArchive.isAbsolute()) {
				fDeploymentArchive = new File(pathToTest, fDeploymentArchive.getPath());
			}

			// If the path to the deployment archive has not been specified,
			// derive it from the directory of the .bpts file and the name
			// of the BPEL file, replacing the .bpel extension by .bpr.
			if (fDeploymentArchive == null) {
				if (fBpelFile == null) {
					throw new DeploymentException(
						"Either the path to the .bpr file or the .bpel file needs to be set");
				}

				final String bprBasename 
					= removeExtension(fBpelFile.getName()) + ".bpr";
				fDeploymentArchive = new File(pathToTest, bprBasename);
			}

			// If the deployment archive does not exist or the BPEL file was specified,
			// create the deployment archive from the BPEL file
			if (fBpelFile != null || !fDeploymentArchive.exists()) {
				if (fBpelFile == null) {
					throw new DeploymentException(
						"The .bpr file does not exist, but the .bpel file has not been set");
				}
				createDeploymentArchive(put, fBpelFile, fDeploymentArchive);
				fDeploymentArchiveIsGenerated = true;
			}

			if (fDeploymentArchive.isAbsolute()) {
				// absolute paths are left as is
				return fDeploymentArchive.getCanonicalPath();
			} else {
				// relative paths are resolved from the directory of the .bpts
				return new File(pathToTest, fDeploymentArchive.getName()).getCanonicalPath();
			}
		} catch (IOException e) {
			// if the path cannot be cleaned up, just turn it into an absolute path
			return fDeploymentArchive.getAbsolutePath();
		}
	}

	public void setArchiveLocation(String archive) {
		setBPRFile(archive);
	}

	public IDeployment getDeployment(ProcessUnderTest put) throws DeploymentException {
		String sArchivePath = getArchiveLocation(put);
		return new ActiveBPELDeployment(put, new File(sArchivePath));
	}

	public void cleanUpAfterTestCase() throws Exception {
	    terminateAllRunningProcesses(put.getName());
	}

	private String removeExtension(String bprBasename) {
		final int lastDot = bprBasename.lastIndexOf('.');
		if (lastDot != -1) {
			bprBasename = bprBasename.substring(0, lastDot);
		}
		return bprBasename;
	}

	private void createDeploymentArchive(ProcessUnderTest put, File fBpel, File fBpr) throws DeploymentException {
		assert fBpel != null;
		assert fBpr  != null;
		assert fBpel.canRead();

		ActiveBPELArchiveGenerator bprGen
			= new ActiveBPELArchiveGenerator(put, fBpel, fBpr);
		bprGen.generate();
	}

	private void check(Object toCheck, String description)
			throws DeploymentException {
		if (toCheck == null)
			throw new DeploymentException(
					"ActiveBPEL deployment configuration is missing the "
							+ description + ".");
	}

	/**
	 * @param re SOAP request entity to be sent to ActiveBPEL.
	 * @return Response from the ActiveBPEL administration service.
	 * @throws IOException
	 * @throws HttpException
	 */
	private static RequestResult sendRequestToActiveBPEL(
			final String url, RequestEntity re)
			throws IOException, HttpException {
		PostMethod method = null;
		try {
			HttpClient client = new HttpClient(new NoPersistenceConnectionManager());
			method = new PostMethod(url);
			method.setRequestEntity(re);

			// Provide custom retry handler is necessary
			method.getParams().setParameter(
				HttpMethodParams.RETRY_HANDLER,
				new DefaultHttpMethodRetryHandler(1, false));
			method.addRequestHeader("SOAPAction", "");
			client.executeMethod(method);

			// We need to read the response body right now: if it is called
			// after the connection is released, it will only return null
			RequestResult result = new RequestResult();
			result.statusCode    = method.getStatusCode();
			result.responseBody  = method.getResponseBodyAsString();

			return result;
		}  finally {
			if (method != null) {
			    method.releaseConnection();
			}
		}
	}

	private void terminateAllRunningProcesses(String processName) throws Exception {
	    for (int pid : listRunningProcesses(processName)) {
	        terminateProcess(pid);
	    }
	}

	  private List<Integer> listRunningProcesses(String processName) throws Exception {
		try {
			ArrayList<Integer> vProcesses = new ArrayList<Integer>();
			RequestResult listResponse = sendRequestToActiveBPEL(
				getAdministrationServiceURL().toExternalForm(),
				new ProcessListRequestEntity(processName));

			if (listResponse.statusCode != 200) {
				throw new Exception(
					String.format(
						"Could not obtain the running process list: "
						+ "got status code %d\nResponse:\n%s",
						listResponse.statusCode,
						listResponse.responseBody));
			}

			// No need to perform XML parsing: we're only interested
			// in some simple elements
			Pattern patPID = Pattern.compile(
				"<[^>]*processId>\\s*([0-9]+)\\s*</[^>]+>");
			Matcher matcher = patPID.matcher(listResponse.responseBody);
			while (matcher.find()) {
				vProcesses.add(Integer.parseInt(matcher.group(1)));
			}

			return vProcesses;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new Exception(
				"Could not obtain the running process list: "
				+ e.toString(), e);
		}
	}

	private void terminateProcess(int pid) throws Exception {
		try {
			++_terminatedProcessCount;
			RequestResult response = sendRequestToActiveBPEL(
				getAdministrationServiceURL().toExternalForm(),
				new TerminateProcessRequestEntity(pid));
			if (response.statusCode != 200) {
				throw new Exception(
					String.format(
						"Could not kill process #%d: "
						+ "non-OK status code %d\nResponse:\n%s",
						pid,
						response.statusCode,
						response.responseBody));
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(
				String.format(
					"Could not kill process #%d: %s",
					pid, e.toString()), e);
		}
	}

	private boolean errorsInSummary(String responseBody) {
		int startErrorCount = responseBody.indexOf(ERRCOUNT_START);
		if (startErrorCount == -1) return false;
		startErrorCount += ERRCOUNT_START.length();

		final int endErrorCount = responseBody.indexOf(ERRCOUNT_END, startErrorCount);
		if (endErrorCount == -1) return false;

		final String sErrorCount
			= responseBody.substring(startErrorCount, endErrorCount);
		final int errorCount = Integer.parseInt(sErrorCount);

		return errorCount > 0;
	}

}


