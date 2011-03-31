/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 */
package net.bpelunit.framework.control.deploy.activebpel;

import java.util.Map;

import javax.wsdl.extensions.ExtensibilityElement;
import javax.xml.namespace.QName;

import net.bpelunit.framework.control.util.BPELUnitConstants;

/**
 * Class which contains the information stored in a WSDL <code>plnk:partnerLinkType</code>
 * element, according to the WS-BPEL 2.0 specification.
 *
 * @author Antonio García-Domínguez
 */
public class PartnerLinkType implements ExtensibilityElement {

	// QName for the plnk:partnerLinkType WSDL extensibility element
	public static final QName ELEMENT_TYPE
		= new QName(BPELUnitConstants.WSBPEL2_PLT_NAMESPACE, "partnerLinkType");

	private QName fName;
	private Map<String, QName> fRoleMap;

	public PartnerLinkType(QName name, Map<String, QName> roleMap) {
		this.fName = name;
		this.fRoleMap = roleMap;
	}

	@Override
	public QName getElementType() {
		return ELEMENT_TYPE;
	}

	@Override
	public Boolean getRequired() {
		return false;
	}

	@Override
	public void setElementType(QName arg0) {
		// ignore
	}

	@Override
	public void setRequired(Boolean arg0) {
		// ignore
	}

	public QName getName() {
		return fName;
	}

	public Map<String, QName> getRoleMap() {
		return fRoleMap;
	}
}
