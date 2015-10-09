/**
 * Copyright (C) 2014 Apigee Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apigee.buildTools.enterprise4g.mavenplugin;

import io.apigee.buildTools.enterprise4g.rest.RestUtil;
import io.apigee.buildTools.enterprise4g.rest.RestUtil.Options;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.File;



/**                                                                                                                                     ¡¡
 * Goal to upload 4g gateway  bundle on server
 * @author rmishra
 * @execute phase="install"
 * @goal kvm
 * @phase install
 * 
 */

public class KVMMojo extends GatewayAbstractMojo
{

	
	
	public static final String DEPLOYMENT_FAILED_MESSAGE = "\n\n\n* * * * * * * * * * *\n\n"
			+ "This deployment could have failed for a variety of reasons.\n\n"
			+ "\n\n* * * * * * * * * * *\n\n\n";

	static Logger logger = LoggerFactory.getLogger(KVMMojo.class);
	
	public KVMMojo() {
		super();

	}

	enum State {
		START, INIT, IMPORTING, DEACTIVATING, ACTIVATING, DELETING, COMPLETE
	}

	enum BUILDOPTIONS {
		NULL,deployinactive,undeploy,delete
	}
	
	enum OPTIONS {
		inactive,force,validate,clean,update,override
	}
	
	State state = State.START;
	
	String activeRevision="";
	String bundleRevision="";
	
	BUILDOPTIONS buildOption;

	public void init() throws IOException, MojoFailureException,Exception {
		try {

			String options="";
			state = State.INIT;
			long delay=0;
			
			if (this.getBuildOption() != null) {
				String opt = this.getBuildOption();
				//To Support legacy 
				opt = opt.replace("-", "");
				buildOption=BUILDOPTIONS.valueOf(opt);
			}
			else {
				buildOption=BUILDOPTIONS.valueOf("NULL");
			}
			
			//Options.delay=0;
			if (this.getDelay() != null) {
				delay = this.getDelay();
				Options.delay=delay;
			}
			if (this.getOverridedelay() != null) {
				delay = this.getOverridedelay();
				Options.override_delay=delay;
			}
			
			options=super.getOptions();
			if (options != null) {
				String [] opts = options.split(",");
				for (String opt : opts) {
					switch (OPTIONS.valueOf(opt)) {
					case validate :
                        Options.validate=true;
                        break;
					case force:
						Options.force=true;
						break;
					case inactive:
						Options.inactive=true;
						break;
					case clean:
						Options.clean=true;
						break;
					case update:
						Options.update=true;
						break;
					case override:
						Options.override=true;
						break;
					default:
						break;	
					
					}
				}
			}
			
			
			
			logger.info("\n\n=============Initializing Maven Deployment================\n\n");
			
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				// This "throws Exception" bothers me so much
				throw e;
			}

	}

	private File findKVMConfig(Logger logger) throws MojoExecutionException {
		File configFile = new File(super.getBaseDirectoryPath() + File.separator + "logconfigurations.json");

		if (configFile.exists()) {
			return configFile;
		}

		logger.info("No config.json found. Skipping package configuration.");
		return null;
	}

	/**
	 * create KVM values
	 */
	protected void doUpdate(File kvmConfig) throws IOException, MojoFailureException, Exception {
		try {
			
			logger.info("\n\n=============Creating KVM================\n\n");
			// state = State.IMPORTING;
			bundleRevision = RestUtil.createKVM(super.getProfile(), kvmConfig);
		
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			// This "throws Exception" bothers me so much
			throw e;
		}
	}

	/** 
	 * Entry point for the mojo.
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {

		try {
			fixOSXNonProxyHosts();
			
			init();

			File kvmConfig = findKVMConfig(logger);
			doUpdate(kvmConfig);
			
			state = State.COMPLETE;
			
		} catch (MojoFailureException e) {
			processHelpfulErrorMessage(e);
		} catch (RuntimeException e) {
			processHelpfulErrorMessage(e);
		} catch (Exception e) {
			processHelpfulErrorMessage(e);
		} finally {
			
		}
	}

	private void processHelpfulErrorMessage(Exception e)
			throws MojoExecutionException {
		if (state == State.IMPORTING) {
			logger.error(DEPLOYMENT_FAILED_MESSAGE);
		}

		if (e instanceof MojoExecutionException) {
			throw (MojoExecutionException) e;
		} else {
			throw new MojoExecutionException("", e);
		}

	}

	
	
}




