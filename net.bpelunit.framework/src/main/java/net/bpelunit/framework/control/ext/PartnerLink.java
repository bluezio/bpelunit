/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 * 
 */

package net.bpelunit.framework.control.ext;

import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.extensions.soap12.SOAP12Binding;
import javax.xml.namespace.QName;

/**
 * This class encapsulates the mapping between corresponding partner service
 * with the partnerlink. PartnerLink contains the service and additionally the
 * port within the service to which the partner link maps to.
 *
 * In some cases, PartnerLink may contain more information if available, such
 * as its partner link type or the pair of roles (both partnerRole and myRole).
 * 
 * @author chamith, Antonio García-Domínguez (extended with a
 * version using real WSDL4J objects, and not just their names).
 */
public class PartnerLink {

	private String fName, fPortName, fMyRoleName, fPartnerRoleName;
	private QName fServiceQName, fPartnerLinkTypeQName;
	private Service fService;
	private Port fPort;

	public PartnerLink(String name, QName service, String port) {
		this.fName = name;
		this.fServiceQName = service;
		this.fPortName = port;
	}

	public PartnerLink(String name, Service service, Port port) {
		this(name, service.getQName(), port.getName());
		this.fService = service;
		this.fPort = port;
	}

	public String getName() {
		return fName;
	}

	public QName getServiceQName() {
		return fServiceQName;
	}

	public String getPortName() {
		return fPortName;
	}

	public Service getService() {
		return fService;
	}

	public Port getPort() {
		return fPort;
	}

	public void setPartnerLinkType(QName partnerLinkType) {
		this.fPartnerLinkTypeQName = partnerLinkType;
	}

	public QName getPartnerLinkType() {
		return fPartnerLinkTypeQName;
	}

	public void setMyRole(String myRole) {
		this.fMyRoleName = myRole;
	}

	public String getMyRole() {
		return fMyRoleName;
	}

	public void setPartnerRole(String partnerRole) {
		this.fPartnerRoleName = partnerRole;
	}

	public String getPartnerRole() {
		return fPartnerRoleName;
	}

	public String getPortAddress() {
		if (fPort == null) return null;

		for (Object o : fPort.getExtensibilityElements()) {
			if (o instanceof SOAPAddress) {
				return ((SOAPAddress)o).getLocationURI();
			}
			else if (o instanceof SOAP12Address) {
				return ((SOAP12Address)o).getLocationURI();
			}
		}

		return null;
	}

	public String getSoapStyle() {
		if (fPort == null) return null;

		for (Object o : fPort.getBinding().getExtensibilityElements()) {
			if (o instanceof SOAPBinding) {
				return ((SOAPBinding)o).getStyle();
			}
			else if (o instanceof SOAP12Binding) {
				return ((SOAP12Binding)o).getStyle();
			}
		}

		return null;
	}
}
