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
import io.apigee.buildTools.enterprise4g.utils.ServerProfile;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;



/**                                                                                                                                     ¡¡
 * Goal to upload 4g gateway  bundle on server
 * @author rmishra
 * @execute phase="install"
 * @goal targetserver
 * @phase install
 * 
 */

public class TargetServerMojo extends GatewayAbstractMojo
{

    public static String TARGETSERVERS_FILE   = "targetservers.list";	
    public static String CONFIG_FOLDER = "config";	
	
	public static final String DEPLOYMENT_FAILED_MESSAGE = "\n\n\n* * * * * * * * * * *\n\n"
			+ "This deployment could have failed for a variety of reasons.\n\n"
			+ "\n\n* * * * * * * * * * *\n\n\n";

	static Logger logger = LoggerFactory.getLogger(TargetServerMojo.class);

	private ServerProfile serverProfile;
	
	public TargetServerMojo() {
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

			serverProfile = super.getProfile();
			
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

    private void createTargetServerUsingListFile() throws IOException, MojoFailureException, Exception {
        File listFile = new File(super.getBaseDirectoryPath() + 
        						 File.separator + 
        						 CONFIG_FOLDER +
        						 File.separator + 
        						 serverProfile.getOrg() + 
        						 File.separator + 
        						 serverProfile.getEnvironment() + 
        						 File.separator + 
        						 TARGETSERVERS_FILE);
		logger.debug("List file path " + listFile);
        File targetServerListFileDirectory = listFile.getParentFile();
        List<String> targetServerFiles = FileUtils.readLines(listFile, null);
        processTarserv(targetServerFiles, targetServerListFileDirectory);
    }

    private void processTarserv(List<String> targetServerFiles, File targetServerListFileDirectory) throws IOException, MojoFailureException, Exception {
        for (String targetServerFileString : targetServerFiles) {
            if (isCommentOrBlank(targetServerFileString)) {
                continue;
            }
            File targetServerFile = new File(targetServerFileString);
            if (targetServerFile.isAbsolute()) {
                doUpdate(targetServerFile);
            } else {
                doUpdate(new File(targetServerListFileDirectory, targetServerFileString));
            }
        }
    }


    private boolean isCommentOrBlank(String productFileString) {
        if (productFileString.length() == 0 || productFileString.trim().length() == 0
                || productFileString.trim().startsWith("#")) {
            return true;
        }
        return false;
    }

	/**
	 * create Target Server values
	 */
	protected void doUpdate(File targetServerConfig) throws IOException, MojoFailureException, Exception {
		try {
			
			logger.info("\n\n=============Creating TargetServer================\n\n");
			// state = State.IMPORTING;
			bundleRevision = RestUtil.createTargetServer(super.getProfile(), targetServerConfig);
		
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

			createTargetServerUsingListFile();
			
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




