package net.bpelunit.framework.control.deploy.activebpel;

import java.util.HashMap;
import java.util.Map;

import javax.wsdl.Definition;
import javax.wsdl.WSDLException;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.ExtensionDeserializer;
import javax.wsdl.extensions.ExtensionRegistry;
import javax.xml.namespace.QName;

import net.bpelunit.framework.control.util.BPELUnitConstants;
import net.bpelunit.framework.control.util.ParseUtil;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class PartnerLinkTypeExtensionDeserializer
		implements ExtensionDeserializer {

	@SuppressWarnings("rawtypes")
	@Override
	public ExtensibilityElement unmarshall(Class klazz,
			QName elementType, Element element, Definition def,
			ExtensionRegistry extreg) throws WSDLException
	{
		final String rawName = element.getAttribute("name");
		final QName name = ParseUtil.stringToQName(rawName, element);
		final Map<String, QName> roleMap = new HashMap<String, QName>();

		NodeList children = element.getChildNodes();
		for (int iChild = 0; iChild < children.getLength(); ++iChild) {
			Node childNode = children.item(iChild);
			if (!(childNode instanceof Element)) continue;

			Element childElement = (Element)childNode;
			if (!BPELUnitConstants.WSBPEL2_PLT_NAMESPACE.equals(childElement.getNamespaceURI())
				|| !"role".equals(childElement.getLocalName()))
			{
				continue;
			}

			final String roleName = childElement.getAttribute("name");
			final String roleRawType = childElement.getAttribute("portType");
			final QName roleType = ParseUtil.stringToQName(roleRawType, childElement);
			roleMap.put(roleName, roleType);
		}

		return new PartnerLinkType(name, roleMap);
	}

}