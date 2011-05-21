/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 */
package net.bpelunit.framework.control.deploy.activebpel;

import net.bpelunit.framework.control.ext.GenericDeployment;
import net.bpelunit.framework.control.ext.PartnerLink;
import net.bpelunit.framework.coverage.exceptions.ArchiveFileException;
import net.bpelunit.framework.exception.DeploymentException;
import net.bpelunit.framework.model.Partner;
import net.bpelunit.framework.model.ProcessUnderTest;

/**
 * ActiveBPEL deployment archive generator.
 * 
 * @author Antonio García-Domínguez
 */
public class ActiveBPELDeployment extends GenericDeployment {

	public ActiveBPELDeployment(Partner[] partners, String archive)
			throws DeploymentException {
		super(partners, archive);
	}

	public ActiveBPELDeployment(ProcessUnderTest processUnderTest, String archive) throws DeploymentException {
		this(processUnderTest.getPartners().values().toArray(new Partner[0]), archive);
	}

	@Override
	public void addLoggingService(String wsdl) throws ArchiveFileException {
		// TODO Auto-generated method stub

	}

	@Override
	public PartnerLink[] getPartnerLinks() throws DeploymentException {
		// TODO Auto-generated method stub
		return null;
	}

}
