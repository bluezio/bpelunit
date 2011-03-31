/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 * 
 */

package net.bpelunit.framework.control.ext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.Service;
import javax.xml.namespace.QName;

import net.bpelunit.framework.control.util.JDomHelper;
import net.bpelunit.framework.control.util.ParseUtil;
import net.bpelunit.framework.exception.DeploymentException;
import net.bpelunit.framework.exception.EndPointException;
import net.bpelunit.framework.model.Partner;
import net.bpelunit.framework.model.ProcessUnderTest;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import de.schlichtherle.io.File;
import de.schlichtherle.io.FileWriter;

public abstract class GenericDeployment implements IDeployment {

	private static final String SERVICE_ELEMENT = "service";
	private static final String NAME_ATTR = "name";
	private static final String PORT_ELEMENT = "port";
	private static final String LOCATION_ATTR = "location";

	private Partner[] fPartners;
	private Partner fPut;
	private Map<QName, String> fServiceToWsdlMapping = new HashMap<QName, String>();
	private String fArchive;

	public GenericDeployment(ProcessUnderTest put, File archive) throws DeploymentException {
		this(put.getPartners().entrySet().toArray(new Partner[0]), archive.getPath());
	}

	public GenericDeployment(Partner[] partners, String archive)
			throws DeploymentException {
		this.fPartners = partners;
		this.fArchive = archive;
		this.fPut = findPUT();

		try {
			populateServiceToWsdlMapping(new File(archive));
		} catch (IOException e) {
			throw new DeploymentException(
					"Problem instantiating Deployment class", e);
		}
	}

	// ********* IDeployment implementation methods ****************

	/**
	 * This method replaces the end point URL of a partner service WSDL to the
	 * simulated URL of the partner in the framework. This involves in finding
	 * the corresponding WSDL of the partner and changing its service end point
	 * URL to the simulated URL. If any additional deployer specific steps have
	 * to be carried out in order to change the end point in a particular
	 * deployer this method may be overridden to include additional steps.
	 */
	public void replaceEndpoints(PartnerLink pl, Partner p)
			throws EndPointException {

		/*
		 * if(p instanceof ProcessUnderTest){ String simulatedURL=p. }
		 */

		// get the simulated URL from the Partner
		String simulatedURL = p.getSimulatedURL();

		// get the service QName and port from the PartnerLink
		QName partnerService = pl.getServiceQName();
		String partnerPort = pl.getPortName();

		// get the the path of the WSDL which is defining the corresponding
		// service from the service name to WSDL path mapping
		String serviceWsdl = fServiceToWsdlMapping.get(partnerService);

		// change the WSDL document
		Document document;
		try {
			document = ParseUtil.getJDOMDocument(serviceWsdl);
		} catch (IOException e) {
			throw new EndPointException(
					"An I/O error occurred when reading the WSDL.", e);
		}

		Element definition = document.getRootElement();

		Iterator<Element> services = JDomHelper.getDescendants(definition,
				new ElementFilter(SERVICE_ELEMENT));

		while (services.hasNext()) {
			Element service = services.next();
			Iterator<Element> ports = JDomHelper.getDescendants(service, new ElementFilter(
					PORT_ELEMENT));

			if (partnerPort != null) {
				while (ports.hasNext()) {
					Element port = ports.next();
					String portName = port.getAttributeValue(NAME_ATTR);

					if (portName.equals(partnerPort)) {
						Element address = (Element) port.getChildren()
								.iterator().next();
						address.removeAttribute(LOCATION_ATTR);
						address.setAttribute(LOCATION_ATTR, simulatedURL);
					}
				}
			} else {
				while (ports.hasNext()) {
					Element port = ports.next();
					Element address = (Element) port.getChildren().iterator()
							.next();
					address.removeAttribute(LOCATION_ATTR);
					address.setAttribute(LOCATION_ATTR, simulatedURL);
				}
			}
		}

		FileWriter writer = null;
		try {
			writer = new FileWriter(serviceWsdl);
			XMLOutputter xmlOutputter = new XMLOutputter(Format
					.getPrettyFormat());
			xmlOutputter.output(document, writer);
		} catch (IOException e) {
			throw new EndPointException(
					"An I/O error occurred when writing the WSDL.", e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}

	}

	public String getArchive() {
		return this.fArchive;
	}

	public Partner[] getPartners() {
		return this.fPartners;
	}

	// ********** protected assessor methods *******************

	protected Partner getProcessUnderTest() {
		return this.fPut;
	}

	protected Map<QName, String> getServiceToWsdlMapping() {
		return this.fServiceToWsdlMapping;
	}

	// ************ private helper methods *****************************

	private ProcessUnderTest findPUT() {
		for (Partner p : fPartners) {
			if (p instanceof ProcessUnderTest) {
				return (ProcessUnderTest) p;
			}
		}

		return null;
	}

	/*
	 * This maps the services that a WSDL defines with the WSDL file itself.
	 * Given the service QName it makes possible to lookup the WSDL file which
	 * defines the corresponding service.
	 */
	private void populateServiceToWsdlMapping(File dir) throws IOException {

		if (dir == null) {
			return;
		}

		if (!dir.exists()) {
			throw new IOException("Could not find directory: " + dir.toString());
		}

		for (File file : (File[]) dir.listFiles()) {
			if (file.isDirectory()) {
				populateServiceToWsdlMapping(file);
			} else {
				if (file.getName().endsWith(".wsdl")) {
					Definition definition = ParseUtil.getWsdlDefinition(file
							.getAbsolutePath(), true, true);
					Map<QName, Service> services = getDefinitions(definition);

					for (QName service : services.keySet()) {
						fServiceToWsdlMapping.put(service, file
								.getAbsolutePath());
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Map<QName, Service> getDefinitions(Definition definition) {
		return definition.getServices();
	}
}
