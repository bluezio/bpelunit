package net.bpelunit.framework.control.deploy.activebpel;

import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;

final class MapBasedNamespaceContext implements
		NamespaceContext {
	private final Map<String, String> prefixNSMap;

	MapBasedNamespaceContext(Map<String, String> prefixNSMap) {
		this.prefixNSMap = prefixNSMap;
	}

	@Override
	public String getNamespaceURI(String prefix) {
		return prefixNSMap.get(prefix);
	}

	@Override
	public String getPrefix(String nsURI) {
		for (Map.Entry<String, String> entry : prefixNSMap.entrySet()) {
			if (entry.getValue().equals(nsURI)) {
				return entry.getKey();
			}
		}
		return null;
	}

	@Override
	public Iterator getPrefixes(String arg0) {
		return prefixNSMap.keySet().iterator();
	}
}