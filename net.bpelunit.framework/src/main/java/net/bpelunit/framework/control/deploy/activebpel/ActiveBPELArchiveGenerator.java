/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 */
package net.bpelunit.framework.control.deploy.activebpel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.wsdl.Binding;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.wsdl.Service;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;

import net.bpelunit.framework.control.ext.PartnerLink;
import net.bpelunit.framework.control.util.BPELUnitConstants;
import net.bpelunit.framework.control.util.Pair;
import net.bpelunit.framework.control.util.ParseUtil;
import net.bpelunit.framework.control.util.XPathTool;
import net.bpelunit.framework.exception.DeploymentException;
import net.bpelunit.framework.model.ProcessUnderTest;

import org.apache.log4j.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Class which generates .bpr deployment archives from the .bpel file
 * and its dependencies.
 *
 * @author Antonio García-Domínguez
 */
class ActiveBPELArchiveGenerator {

	private static final Logger LOGGER = Logger.getLogger(ActiveBPELArchiveGenerator.class);

	private static final String BPEL_PROCESS_IN_PDD_PREFIX = "bpelproc";

	/** Default filename for the deployment catalog required by ActiveBPEL 4.1. */
	public final static String CATALOGXML_FILENAME  = "catalog.xml";
	/** Target namespace of the ActiveBPEL 4.1 deployment catalog schema. */
	public final static String CATALOGXML_NAMESPACE = "http://schemas.active-endpoints.com/catalog/2006/07/catalog.xsd";

	/** Default filename for the deployment descriptor required by ActiveBPEL 4.1. */
	public final static String ORIGINALPDD_FILENAME = "process.pdd";
	/** Target namespace of the ActiveBPEL 4.1 deployment descriptor schema. */
	public final static String PDD_NAMESPACE        = "http://schemas.active-endpoints.com/pdd/2006/08/pdd.xsd";

	/** XPath expression used to extract imports from BPEL/WSDL/XSD */
	private final static String FIND_IMPORTS_XPATH_EXPR
		= "xsd:import/@schemaLocation | wsdl:import/@location | bpel2:import/@location | (wsdl:types/xsd:schema/xsd:import/@schemaLocation)";
	private static final String FIND_BPEL_PARTNER_LINKS_XPATH_EXPR
		= "bpel2:process/bpel2:partnerLinks/bpel2:partnerLink";

	private static XPathTool xpathTool;
	private static DocumentBuilderFactory docBuilderFactory;
	private static DocumentBuilder docBuilder;
	private static WSDLReader wsdlReader;

	private File fBpelFile, fBprFile, fCatalogXML, fProcessDescriptor;
	private ProcessUnderTest fPUT;

	// Set of XML Schema files we need to include in the .bpr
	private Set<File> fXsdFiles = new HashSet<File>();

	// Map from file to parsed BPEL document (we need to handle imports)
	private Map<File, Document> fParsedBpelFiles = new HashMap<File, Document>();

	// Map from file to parsed WSDL document
	private Map<File, Definition> fParsedWsdlFiles = new HashMap<File, Definition>();

	public ActiveBPELArchiveGenerator(ProcessUnderTest put, File bpel, File bpr) {
		this.fBpelFile = bpel;
		this.fBprFile  = bpr;
		this.fPUT = put;
	}

	public void generate() throws DeploymentException {
		try {
			LOGGER.debug("Reading dependencies from " + fBpelFile.getCanonicalPath());
			readDependencies(fBpelFile);
			LOGGER.debug("Writing catalog.xml");
			writeCatalogXML();
			LOGGER.debug("Writing process.pdd");
			writeProcessDescriptor();
			LOGGER.debug("Packing BPR " + fBprFile.getName());
			packBPR();
		}
		catch (DeploymentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new DeploymentException("Could not generate the .bpr file", e);
		}
	}

	private void readDependencies(File fRoot)
		throws ParserConfigurationException, SAXException, IOException, XPathExpressionException, DeploymentException, WSDLException
	{
		Document doc = getDocumentBuilder().parse(fRoot);
	
		// Add this file to the proper set
		final Element docElement = doc.getDocumentElement();
		final String namespaceURI = docElement.getNamespaceURI();
		if (BPELUnitConstants.WSBPEL2_NAMESPACE.equals(namespaceURI)) {
			fParsedBpelFiles.put(fRoot, doc);
		}
		else if (BPELUnitConstants.XML_SCHEMA_NAMESPACE.equals(namespaceURI)) {
			fXsdFiles.add(fRoot);
		}
		else if (BPELUnitConstants.WSDL_NAMESPACE.equals(namespaceURI)) {
			Definition parsedWsdl = getWsdlReader().readWSDL(fRoot.getCanonicalPath(), doc);
			fParsedWsdlFiles.put(fRoot, parsedWsdl);
		}
		else {
			throw new DeploymentException(
				"Unknown namespace when generating .bpr file: " + namespaceURI);
		}
	
		// Loop through the imports
		for (Node node : getXPathTool().evaluateAsList(FIND_IMPORTS_XPATH_EXPR, docElement)) {
			if (node instanceof Attr) {
				final Attr attrNode = (Attr)node;
				final String attrValue = attrNode.getValue();
				File fDependency = new File(attrValue);
				if (!fDependency.isAbsolute()) {
					fDependency = new File(fRoot.getCanonicalFile().getParentFile(), attrValue);
				}
				readDependencies(fDependency);
			}
		}
	}

	private void writeCatalogXML()
		throws IOException, ParserConfigurationException,
			TransformerFactoryConfigurationError, TransformerException
	{
		fCatalogXML = File.createTempFile("catalog", ".xml");
		LOGGER.debug("catalog.xml will be generated in " + fCatalogXML.getCanonicalPath());

		Document doc = getDocumentBuilder().newDocument();
		Element eCatalog = doc.createElementNS(CATALOGXML_NAMESPACE, "catalog");
		doc.appendChild(eCatalog);
	
		for (File wsdl : fParsedWsdlFiles.keySet()) {
			Element eWSDLEntry = doc.createElementNS(CATALOGXML_NAMESPACE, "wsdlEntry");
	
			String wsdlFilename = wsdl.getName();
			eWSDLEntry.setAttribute("location", String.format("project:/wsdl/%s", wsdlFilename));
			eWSDLEntry.setAttribute("classpath", String.format("wsdl/%s", wsdlFilename));
	
			eCatalog.appendChild(eWSDLEntry);
		}
		for (File xsd : fXsdFiles) {
			Element eSchemaEntry = doc.createElementNS(CATALOGXML_NAMESPACE, "schemaEntry");
	
			String xsdFilename = xsd.getName();
			eSchemaEntry.setAttribute("location", String.format("project:/wsdl/%s", xsdFilename));
			eSchemaEntry.setAttribute("classpath", String.format("wsdl/%s", xsdFilename));
	
			eCatalog.appendChild(eSchemaEntry);
		}
	
		dumpXML(doc, fCatalogXML);
		LOGGER.debug("catalog.xml was generated successfully in " + fCatalogXML.getCanonicalPath());
	}

	private void writeProcessDescriptor()
		throws DOMException, DeploymentException, WSDLException, ParserConfigurationException,
			IOException, TransformerFactoryConfigurationError, TransformerException, XPathExpressionException
	{
		fProcessDescriptor = File.createTempFile("process", ".pdd");
		LOGGER.debug("process.pdd will be generated in " + fProcessDescriptor.getCanonicalPath());

		final String bpelPathInPDD = "bpel/" + fBpelFile.getName();
		Document doc = getDocumentBuilder().newDocument();

		final QName procQName = getProcessQName();
		Element eProcess = doc.createElementNS(PDD_NAMESPACE, "process");
		eProcess.setAttributeNS(
				XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
				XMLConstants.XMLNS_ATTRIBUTE + ":" + BPELUnitConstants.WSBPEL2_PREFIX,
				BPELUnitConstants.WSBPEL2_NAMESPACE);
		eProcess.setAttributeNS(
				XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
				XMLConstants.XMLNS_ATTRIBUTE + ":" + BPEL_PROCESS_IN_PDD_PREFIX,
				procQName.getNamespaceURI());
		eProcess.setAttribute("persistenceType", "full");
		eProcess.setAttribute("location", bpelPathInPDD);
		eProcess.setAttribute("name",
				BPEL_PROCESS_IN_PDD_PREFIX + ":" + procQName.getLocalPart());
		doc.appendChild(eProcess);

		Element ePartnerLinks = doc.createElementNS(PDD_NAMESPACE, "partnerLinks");
		eProcess.appendChild(ePartnerLinks);
		for (Document bpelDoc : fParsedBpelFiles.values()) {
			for (PartnerLink pl : getPartnerLinks(bpelDoc)) {
				Element ePartnerLink = doc.createElementNS(PDD_NAMESPACE, "partnerLink");
				ePartnerLinks.appendChild(ePartnerLink);
				ePartnerLink.setAttribute("name", pl.getName());

				if (pl.getPartnerRole() != null) {
					Element ePartnerRole = doc.createElementNS(PDD_NAMESPACE, "partnerRole");
					ePartnerLink.appendChild(ePartnerRole);
					ePartnerRole.setAttribute("endpointReference", "static");
					ePartnerRole.setAttribute("invokeHandler", "default:Address");

					Element eEndpointReference
						= doc.createElementNS(BPELUnitConstants.WSA_NAMESPACE, "EndpointReference");
					ePartnerRole.appendChild(eEndpointReference);
					eEndpointReference.setAttributeNS(
						XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:s",
						pl.getService().getQName().getNamespaceURI());

					Element eAddress = doc.createElementNS(BPELUnitConstants.WSA_NAMESPACE, "Address");
					eEndpointReference.appendChild(eAddress);
					eAddress.setTextContent(pl.getPortAddress());

					Element eServiceName = doc.createElementNS(BPELUnitConstants.WSA_NAMESPACE, "ServiceName");
					eEndpointReference.appendChild(eServiceName);
					eServiceName.setAttribute("PortName", pl.getPort().getName());
					eServiceName.setTextContent("s:" + pl.getServiceQName().getLocalPart());
				}

				if (pl.getMyRole() != null) {
					Element eMyRole = doc.createElementNS(PDD_NAMESPACE, "myRole");
					ePartnerLink.appendChild(eMyRole);					
					eMyRole.setAttribute("allowedRoles", "");
					eMyRole.setAttribute("binding",
							"document".equals(pl.getSoapStyle()) ? "MSG" : "RPC-LIT");
					eMyRole.setAttribute("service", pl.getServiceQName().getLocalPart());
				}
			}
		}

		// Ahora quedan las referencias a los WSDL
		Element eReferences = doc.createElementNS(PDD_NAMESPACE, "references");
		eProcess.appendChild(eReferences);
		for (File wsdl : fParsedWsdlFiles.keySet()) {
			Element eWSDL = doc.createElementNS(PDD_NAMESPACE, "wsdl");
			eWSDL.setAttribute("location", "project:/wsdl/" + wsdl.getName());
			eWSDL.setAttribute("namespace", getWsdlNamespace(wsdl));
			eReferences.appendChild(eWSDL);
		}

		dumpXML(doc, fProcessDescriptor);
		LOGGER.debug("process.pdd was successfully created in " + fProcessDescriptor.getCanonicalPath());
	}

	private void packBPR() throws IOException {
		ZipOutputStream zipOS = new ZipOutputStream(new FileOutputStream(fBprFile));

		for (File wsdlFile : fParsedWsdlFiles.keySet()) {
			addFileToZip(zipOS, wsdlFile, "wsdl/" + wsdlFile.getName());
		}
		for (File fichXSD : fXsdFiles) {
			addFileToZip(zipOS, fichXSD, "wsdl/" + fichXSD.getName());
		}
		addFileToZip(zipOS, fProcessDescriptor, "process.pdd");
		addFileToZip(zipOS, fProcessDescriptor, "META-INF/catalog.xml");
		addFileToZip(zipOS, fBpelFile, "bpel/" + fBpelFile.getName());

		zipOS.flush();
		zipOS.close();
	}

	/**
	 * Note: this includes <b>all</b> partner links, including those provided
	 * by the WS-BPEL process itself. To filter those to be provided by BPELUnit,
	 * test if {@link PartnerLink#getPartnerRole()} returns a non-<code>null</code>
	 * value.
	 */
	protected Collection<PartnerLink> getPartnerLinks(Document bpelDoc) throws XPathExpressionException, DOMException, DeploymentException {
		// Query the WS-BPEL composition for partner links
		final ArrayList<PartnerLink> lPartnerLinks = new ArrayList<PartnerLink>();

		// Convert the DOM representation to our internal data model
		for (Node nPartnerLink : getXPathTool().evaluateAsList(FIND_BPEL_PARTNER_LINKS_XPATH_EXPR, bpelDoc)) {
			final Node nAtribType = nPartnerLink.getAttributes().getNamedItem("partnerLinkType");
			final QName type = ParseUtil.stringToQName(nAtribType.getNodeValue(), nAtribType);
			final NamedNodeMap nAtribs = nPartnerLink.getAttributes();

			String name   = nAtribs.getNamedItem("name").getNodeValue();
			String myRole = getAttributeIfDefined(nAtribs, "myRole");
			String partnerRole = getAttributeIfDefined(nAtribs, "partnerRole");

			PartnerLinkType plt = findPartnerLinkType(type);
			if (myRole != null) {
				QName myPortTypeName = plt.getRoleMap().get(myRole);
				PartnerLink partnerLink
					= computeFullPartnerLink(name, myPortTypeName);
				partnerLink.setMyRole(myRole);
				lPartnerLinks.add(partnerLink);
			}
			if (partnerRole != null) {
				QName partnerPortTypeName = plt.getRoleMap().get(myRole);
				PartnerLink partnerLink
					= computeFullPartnerLink(name, partnerPortTypeName);
				partnerLink.setPartnerRole(partnerRole);
				lPartnerLinks.add(partnerLink);
			}
		}

		return lPartnerLinks;
	}

	/**
	 * Finds a matching service/port pair, from a roleName/portTypeName pair.
	 * Tries to find out as much information as possible about the resulting
	 * partner link.
	 */
	protected PartnerLink computeFullPartnerLink(
			String roleName, QName portTypeName)
		throws DeploymentException
	{
		PortType portType = findPortType(portTypeName);
		Pair<Service, Port> serviceAndPort = findService(portType);
		PartnerLink partnerLink
			= new PartnerLink(roleName,
					serviceAndPort.getLeft(),
					serviceAndPort.getRight());
		return partnerLink;
	}

	protected Pair<Service, Port> findService(PortType portType) throws DeploymentException {
		// Look for the bindings with the provided port type
		List<Binding> lBindings = new ArrayList<Binding>();
		for (Definition def : fParsedWsdlFiles.values()) {
			for (Object o : def.getBindings().values()) {
				Binding b = (Binding)o;
				if (b.getPortType().equals(portType)) {
					lBindings.add(b);
				}
			}
		}

		// Look for the services using any of those bindings
		for (Definition def : fParsedWsdlFiles.values()) {
			for (Object o : def.getServices().values()) {
				Service service = (Service)o;
				for (Object oP : service.getPorts().values()) {
					Port port = (Port)oP;
					if (lBindings.contains(port.getBinding())) {
						return new Pair<Service, Port>(service, port);
					}
				}
			}
		}

		throw new DeploymentException("Could not find a service with a port of type " + portType);
	}

	protected PortType findPortType(QName myPortType)
			throws DeploymentException {
		PortType portType = null;
		for (Definition def : fParsedWsdlFiles.values()) {
			portType = def.getPortType(myPortType);
			if (portType != null) {
				break;
			}
		}
		if (portType == null) {
			throw new DeploymentException("Port type " + portType + " was not found");
		}
		return portType;
	}

	protected PartnerLinkType findPartnerLinkType(final QName type) throws DeploymentException {
		for (Definition def : this.fParsedWsdlFiles.values()) {
			List<?> lExtensibility = def.getExtensibilityElements();
			for (Object o : lExtensibility) {
				if (!(o instanceof PartnerLinkType)) {
					continue;
				}

				PartnerLinkType plt = (PartnerLinkType)o;
				if (type.equals(plt.getName())) {
					break;
				}
			}
		}
		throw new DeploymentException("Partner link type " + type + " was not found");
	}

	protected String getAttributeIfDefined(final NamedNodeMap nAtribs,
			String attributeName) {
		String myRole = null;
		Node attributeNode = nAtribs.getNamedItem(attributeName);
		if (attributeNode != null) {
			myRole = attributeNode.getNodeValue();
		}
		return myRole;
	}

	protected QName getProcessQName() throws XPathExpressionException {
		Document bpelDoc = fParsedBpelFiles.get(fBpelFile);
		final Element procElem = bpelDoc.getDocumentElement();
		final String rawName = procElem.getAttribute("name");
		return ParseUtil.stringToQName(rawName, procElem);
	}

	protected String getWsdlNamespace(File wsdl) throws WSDLException, DeploymentException {
		if (fParsedWsdlFiles.containsKey(wsdl)) {
			return fParsedWsdlFiles.get(wsdl).getTargetNamespace(); 
		}
		throw new DeploymentException("Could not find target namespace for: " + wsdl.getPath());
	}

	/**
	 * Dumps a DOM document into a file, using an identity XSLT transform.
	 */
	protected void dumpXML(Document doc, File destFile)
		throws TransformerFactoryConfigurationError, FileNotFoundException, TransformerException
	{
		Transformer idTransformer = TransformerFactory.newInstance().newTransformer();
		idTransformer.transform(new DOMSource(doc), new StreamResult(new FileOutputStream(destFile)));
	}

	/**
	 * Adds the file passed as an argument to the zip file
	 *
	 * @param zipOS
	 *            The destination ZIP file
	 * @param file
	 *            The source file
	 * @param entryName
	 *            Name for the entry in the ZIP file
	 * @throws IOException
	 *             It is thrown if the source file can not be opened
	 */
	protected void addFileToZip(ZipOutputStream zipOS, File file, String entryName) throws IOException {
		zipOS.putNextEntry(new ZipEntry(entryName));

		byte[] buf = new byte[2048];
		FileInputStream is = new FileInputStream(file);
		int readBytes = -1;
		while ((readBytes = is.read(buf)) != -1) {
			zipOS.write(buf, 0, readBytes);
		}

		zipOS.closeEntry();
	}

	private static synchronized XPathTool getXPathTool() {
		if (xpathTool == null) {
			final Map<String, String> prefixNSMap = new HashMap<String, String>();
			prefixNSMap.put(XMLConstants.DEFAULT_NS_PREFIX, XMLConstants.NULL_NS_URI);
			prefixNSMap.put(XMLConstants.XMLNS_ATTRIBUTE, XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
			prefixNSMap.put(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI);
			prefixNSMap.put(BPELUnitConstants.XML_SCHEMA_PREFIX,  BPELUnitConstants.XML_SCHEMA_NAMESPACE);
			prefixNSMap.put(BPELUnitConstants.WSDL_PREFIX, BPELUnitConstants.WSDL_NAMESPACE);
			prefixNSMap.put(BPELUnitConstants.WSBPEL2_PREFIX, BPELUnitConstants.WSBPEL2_NAMESPACE);
			prefixNSMap.put(BPELUnitConstants.WSBPEL2_PLT_PREFIX, BPELUnitConstants.WSBPEL2_PLT_NAMESPACE);

			xpathTool = new XPathTool(new MapBasedNamespaceContext(prefixNSMap));
		}

		return xpathTool;
	}

	private static synchronized DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
		if (docBuilderFactory == null) {
			docBuilderFactory = DocumentBuilderFactory.newInstance();
			docBuilderFactory.setNamespaceAware(true);
			docBuilder = docBuilderFactory.newDocumentBuilder();
		}
		return docBuilder;
	}

	private static synchronized WSDLReader getWsdlReader() throws WSDLException {
		if (wsdlReader == null) {
			final ExtensionRegistry extensionRegistry = new ExtensionRegistry();
			wsdlReader = WSDLFactory.newInstance().newWSDLReader();
			wsdlReader.setExtensionRegistry(extensionRegistry);
			extensionRegistry.registerDeserializer(Definition.class,
					PartnerLinkType.ELEMENT_TYPE,
					new PartnerLinkTypeExtensionDeserializer());
		}
		return wsdlReader;
	}
}
