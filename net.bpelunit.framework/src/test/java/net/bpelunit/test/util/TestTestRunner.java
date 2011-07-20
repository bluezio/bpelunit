/**
 * This file belongs to the BPELUnit utility and Eclipse plugin set. See enclosed
 * license file for more information.
 * 
 */
package net.bpelunit.test.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.bpelunit.framework.BPELUnitRunner;
import net.bpelunit.framework.base.BPELUnitBaseRunner;
import net.bpelunit.framework.control.result.ITestResultListener;
import net.bpelunit.framework.exception.ConfigurationException;
import net.bpelunit.framework.exception.DeploymentException;
import net.bpelunit.framework.exception.SpecificationException;
import net.bpelunit.framework.model.test.TestCase;
import net.bpelunit.framework.model.test.TestSuite;
import net.bpelunit.framework.model.test.report.ITestArtefact;

import org.apache.commons.io.FileUtils;

/**
 * 
 * BPELUnit Runner specialized for tests
 * 
 * @version $Id: TestTestRunner.java,v 1.6 2006/07/15 14:52:05 phil Exp $
 * @author Philip Mayer
 * 
 */
public class TestTestRunner extends BPELUnitBaseRunner implements ITestResultListener {

	private int fProblems = 0;

	private int fPassed = 0;

	private TestSuite fSuite;

	@Override
	public void configureInit() throws ConfigurationException {
		setHomeDirectory(FileUtils.toFile(BPELUnitBaseRunner.class.getClass().getResource("/")).getAbsolutePath());
	}

	public TestTestRunner(File fBPTS, Map<String, String> options) throws ConfigurationException, SpecificationException {
		super();

		options.put(BPELUnitRunner.SKIP_UNKNOWN_EXTENSIONS, "true");
		options.put(BPELUnitRunner.GLOBAL_TIMEOUT, "10000");

		initialize(options);
		fSuite = loadTestSuite(fBPTS);
		fSuite.addResultListener(this);
	}

	public TestTestRunner(File fBPTS) throws ConfigurationException, SpecificationException {
		this(fBPTS, new HashMap<String, String>());
	}

	public TestTestRunner(String path, String bpts) throws ConfigurationException, SpecificationException {
		this(new File(path, bpts));
	}

	public TestTestRunner(String path, String bpts, Map<String, String> options) throws ConfigurationException, SpecificationException {
		this(new File(path, bpts), options);
	}

	public void testRun() throws DeploymentException {

		try {
			fSuite.setUp();
			fSuite.run();
		} finally {
				fSuite.shutDown();
		}
	}

	public int getProblems() {
		return fProblems;
	}

	public int getPassed() {
		return fPassed;
	}

	public void progress(ITestArtefact test) {

	}

	public void testCaseEnded(TestCase test) {
		if (test.hasProblems())
			fProblems++;
		else
			fPassed++;
	}

	public void testCaseStarted(TestCase test) {

	}

	@Override
	public void configureLogging() throws ConfigurationException {
		// do nothing.
	}

	public TestSuite getTestSuite() {
		return fSuite;
	}

}

