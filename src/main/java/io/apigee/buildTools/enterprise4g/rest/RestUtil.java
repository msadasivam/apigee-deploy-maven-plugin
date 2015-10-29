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
package io.apigee.buildTools.enterprise4g.rest;

import io.apigee.buildTools.enterprise4g.utils.PrintUtil;
import io.apigee.buildTools.enterprise4g.utils.ServerProfile;
import io.apigee.buildTools.enterprise4g.utils.StringToIntComparator;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.testing.http.MockHttpContent;
import com.google.api.client.util.GenericData;
import com.google.api.client.util.Key;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class RestUtil {

    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();
    static String versionRevision;
    static Logger logger = LoggerFactory.getLogger(RestUtil.class);
    static Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static final String STATE_UNDEPLOYED = "undeployed";
    public static final String STATE_DEPLOYED = "deployed";
    public static final String STATE_ERROR = "error";
    public static final String STATE_IMPORTED = "imported";


    public static class Options {

        public static boolean force;
        public static boolean clean;
        public static boolean update;
        public static boolean inactive;
        public static boolean override;
        public static boolean validate;
        public static long delay;
        public static long override_delay;

    }


    public static class AppRevision {
        @Key
        public String name;

        @Key
        public List<String> revision;
    }

    public static class ConfigVersion {
        @Key
        public int majorVersion;

        @Key
        public int minorVersion;
    }

    public static class Server {
        @Key
        public String status;
        @Key
        public List<String> type;
        @Key
        public String uUID;
    }

    public static class Configuration {
        @Key
        public String basePath;
        @Key
        public List<String> steps;
    }

    public static class Revision {
        @Key
        public Configuration configuration;
        @Key
        public String name;
        @Key
        public List<Server> server;
        @Key
        public String state;
    }

    public static class Environment {

        @Key
        public String name;
        @Key
        public List<Revision> revision;
    }

    public static class BundleDeploymentConfig {

        @Key
        public List<Environment> environment;
        @Key
        public String name;
        @Key
        public String organization;
    }

    public static class BundleActivationConfig {
        @Key
        public String aPIProxy;
        @Key
        public Configuration configuration;
        @Key
        public String environment;
        @Key
        public String name;
        @Key
        public String organization;
        @Key
        public String revision;
        @Key
        public String state;

        @Key
        public List<Server> server;

    }

    public static class SeamLessDeploymentStatus {
        @Key
        public String aPIProxy;
        @Key
        public List<BundleActivationConfig> environment;
        @Key
        public String organization;
    }


    public static class AppConfig {
        @Key
        public ConfigVersion configurationVersion;

        @Key
        public String contextInfo;

        @Key
        public long createdAt;

        @Key
        public String createdBy;

        @Key
        public long lastModifiedAt;

        @Key
        public String lastModifiedBy;

        @Key
        public List<String> policies;

        @Key
        public List<String> proxyEndpoints;

        @Key
        public List<String> resources;

        @Key
        public String revision;

        @Key
        public List<String> targetEndpoints;

        @Key
        public List<String> targetServers;

        @Key
        public String type;

        public String getRevision() {
            return revision;
        }

        public void setRevision(String revision) {
            this.revision = revision;
        }
    }

    public static class KVMEntry {
        @Key
        public String name;

        @Key
        public String value;
    }    

    public static class KVMResponse {
        @Key
        public String name;

        @Key
        public List<KVMEntry> entry;
    }

    static HttpRequestFactory REQUEST_FACTORY = HTTP_TRANSPORT
            .createRequestFactory(new HttpRequestInitializer() {
                // @Override
                public void initialize(HttpRequest request) {
                    request.setParser(JSON_FACTORY.createJsonObjectParser());
                    XTrustProvider.install();
                    FakeHostnameVerifier _hostnameVerifier = new FakeHostnameVerifier();
                    // Install the all-trusting host name verifier:
                    HttpsURLConnection.setDefaultHostnameVerifier(_hostnameVerifier);

                }
            });

    public static void getRevision(ServerProfile profile) throws IOException {

        // trying to construct the URL like
        // https://api.enterprise.apigee.com/v1/organizations/apigee-cs/apis/taskservice/
        // response is like
        // {
        // "name" : "taskservice1",
        // "revision" : [ "1" ]
        // }
        HttpRequest restRequest = REQUEST_FACTORY
                .buildGetRequest(new GenericUrl(profile.getHostUrl() + "/"
                        + profile.getApi_version() + "/organizations/"
                        + profile.getOrg() + "/apis/"
                        + profile.getApplication() + "/"));
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            AppRevision apprev = response.parseAs(AppRevision.class);
            Collections.sort(apprev.revision, new StringToIntComparator());
            setVersionRevision(apprev.revision.get(0));
            logger.info(PrintUtil.formatResponse(response, gson.toJson(apprev).toString()));
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
        }
    }

    // This function should do -
    // Return a revision if there is a active revision
    // Returns "" if there are no active revision

    public static String getDeployedRevision(ServerProfile profile)
            throws IOException {

        BundleDeploymentConfig deployment1 = null;

        try {

            HttpRequest restRequest = REQUEST_FACTORY
                    .buildGetRequest(new GenericUrl(profile.getHostUrl() + "/"
                            + profile.getApi_version() + "/organizations/"
                            + profile.getOrg() + "/apis/"
                            + profile.getApplication() + "/deployments"));
            restRequest.setReadTimeout(0);
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept("application/json");
            headers.setBasicAuthentication(profile.getCredential_user(),
                    profile.getCredential_pwd());
            restRequest.setHeaders(headers);


            logger.debug(PrintUtil.formatRequest(restRequest));
            HttpResponse response = restRequest.execute();
            deployment1 = response.parseAs(BundleDeploymentConfig.class);
            logger.debug(PrintUtil.formatResponse(response, gson.toJson(deployment1).toString()));


            if (deployment1 != null) {
                for (Environment env : deployment1.environment) {
                    if (env.name.equalsIgnoreCase(profile.getEnvironment()))
                        return env.revision.get(0).name;
                }
            }

        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        //This is not correct , it will always return the 1st env's deployed revision .
        //return deployment1.environment.get(0).revision.get(0).name;
        return "";


    }


    public static String uploadBundle(ServerProfile profile, String bundleFile)
            throws IOException {

        FileContent fContent = new FileContent("application/octet-stream",
                new File(bundleFile));
        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg() + "/apis?action=import&name="
                + profile.getApplication();
        if (Options.validate) {
            importCmd = importCmd + "&validate=true";
        }

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();


            // logger.info(response.parseAsString());
            AppConfig appconf = response.parseAs(AppConfig.class);
            setVersionRevision(appconf.revision);
            logger.info(PrintUtil.formatResponse(response, gson.toJson(appconf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();

    }


    public static String updateBundle(ServerProfile profile, String bundleFile, String revision)
            throws IOException {

        FileContent fContent = new FileContent("application/octet-stream",
                new File(bundleFile));
        //System.out.println("\n\n\nFile path: "+ new File(bundleFile).getCanonicalPath().toString());
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg() + "/apis/"
                + profile.getApplication() + "/revisions/"
                + revision+"?validate=true";

        if (Options.validate) {
            importCmd = importCmd + "&validate=true";
        }

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            AppConfig appconf = response.parseAs(AppConfig.class);
            setVersionRevision(appconf.revision);
            logger.info(PrintUtil.formatResponse(response, gson.toJson(appconf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();

    }


    public static String deactivateBundle(ServerProfile profile)
            throws IOException {
        //JsonHttpContent content = new JsonHttpContent(new JacksonFactory(), "{}") ;
        MockHttpContent content = new MockHttpContent();

        String existingRevision = "";
        BundleActivationConfig deployment1 = new BundleActivationConfig();
        try {

            existingRevision = getDeployedRevision(profile);

            if (existingRevision.length() > 0) //  there are active revisions
            // deployment then undeploy
            {

                logger.info("De-activating Version: " + existingRevision + " For Env Profile: " + profile.getEnvironment());
                String undeployCmd = profile.getHostUrl() + "/"
                        + profile.getApi_version() + "/organizations/"
                        + profile.getOrg() + "/environments/"
                        + profile.getEnvironment() + "/apis/"
                        + profile.getApplication() + "/revisions/"
                        + existingRevision
                        + "/deployments";


                HttpRequest undeployRestRequest = REQUEST_FACTORY.buildDeleteRequest(
                        new GenericUrl(undeployCmd));
                undeployRestRequest.setReadTimeout(0);
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept("application/json");
                headers.setContentType("application/x-www-form-urlencoded");
                headers.setBasicAuthentication(profile.getCredential_user(),
                        profile.getCredential_pwd());
                undeployRestRequest.setHeaders(headers);

                HttpResponse response = null;

                logger.info(PrintUtil.formatRequest(undeployRestRequest));
                response = undeployRestRequest.execute();
                deployment1 = response.parseAs(BundleActivationConfig.class);
                logger.info(PrintUtil.formatResponse(response, gson.toJson(deployment1).toString()));

                //Introduce Delay
                if (Options.delay != 0) {
                    try {
                        logger.info("Delay of " + Options.delay + " milli second");
                        Thread.sleep(Options.delay);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else {
                //If there are no existing active revisions
                deployment1.state = STATE_UNDEPLOYED;
            }

        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            //deployment1.state = "No application was in deployed state";
            deployment1.state = STATE_ERROR;

        } catch (Exception e) {
            logger.error(e.getMessage());
            //deployment1.state = "No application was in deployed state";
            deployment1.state = STATE_ERROR;
        } finally {


            //Rechecking only if we supply force option.  Checking if still any active revision exists
            if (Options.force) {

                String anyExistingRevision = "";

                try {
                    logger.info("Checking if any deployed version still exists for Env Profile: " + profile.getEnvironment());
                    anyExistingRevision = getDeployedRevision(profile);

                } catch (Exception e) {
                    //deployment1.state = "\nNo application is in deployed state\n";
                    logger.error("Application couldn't be undeployed :: " + anyExistingRevision);
                    deployment1.state = STATE_ERROR;
                }
                //check  if there is a any other existing revision and throw exception if its there
                if (anyExistingRevision.length() > 0) // Looks like still some active version exist
                {
                    logger.warn("Application couldn't be undeployed :: " + anyExistingRevision);
                    deployment1.state = STATE_ERROR;
                }

            }


        }

        return deployment1.state;

    }


    public static String refreshBundle(ServerProfile profile, String revision)
            throws IOException {

        String state = "";
        try {

            state = deactivateBundle(profile); // de-activating the existing
            // bundle
            if (!state.equals(STATE_UNDEPLOYED)) {
                throw new IOException("The bundle is not undeployed");
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new IOException("Error in undeploying bundle");
        }

        logger.info("Activating Version: " + revision + " For Env Profile: " + profile.getEnvironment());
        return activateBundleRevision(profile, revision);

    }


    public static String activateBundleRevision(ServerProfile profile, String revision)
            throws IOException {

        String state = "";

        //JsonHttpContent content = new JsonHttpContent(new JacksonFactory(), "{}") ;

        BundleActivationConfig deployment2 = new BundleActivationConfig();

        try {

            UrlEncodedContent urlEncodedContent = null;

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept("application/json");
            //headers.setContentType("application/x-www-form-urlencoded");
            headers.setBasicAuthentication(profile.getCredential_user(),
                    profile.getCredential_pwd());

            String deployCmd = profile.getHostUrl() + "/"
                    + profile.getApi_version() + "/organizations/"
                    + profile.getOrg() + "/environments/"
                    + profile.getEnvironment() + "/apis/"
                    + profile.getApplication() + "/revisions/" + revision
                    + "/deployments";

            if (Options.override) {
                GenericData data = new GenericData();
                data.set("override", "true");
                data.set("delay", "5");
                urlEncodedContent = new UrlEncodedContent(data);
            }


            HttpRequest deployRestRequest = REQUEST_FACTORY.buildPostRequest(
                    new GenericUrl(deployCmd), urlEncodedContent);
            deployRestRequest.setReadTimeout(0);
            deployRestRequest.setHeaders(headers);


            HttpResponse response = null;
            logger.info(PrintUtil.formatRequest(deployRestRequest));

            response = deployRestRequest.execute();

            if (Options.override) {
                SeamLessDeploymentStatus deployment3 = response.parseAs(SeamLessDeploymentStatus.class);
                Iterator<BundleActivationConfig> iter =   deployment3.environment.iterator();
                while (iter.hasNext()){
                    BundleActivationConfig config = iter.next();
                    if (config.environment.equalsIgnoreCase(profile.getEnvironment())) {
                        if (!config.state.equalsIgnoreCase("deployed"))
                         {
                             logger.info("\nWaiting to assert bundle activation.....");
                             Thread.sleep(10);
                             if (getDeployedRevision(profile).equalsIgnoreCase(revision))
                             {
                                 logger.info("\nDeployed revision is: " + getVersionRevision());
                                 return "deployed";
                             }
                             else
                                 logger.error("Deployment failed to activate");
                                 throw new MojoExecutionException("Deployment failed: Bundle did not activate within expected time. Please check deployment status manually before trying again");
                         }
                        else {
                            logger.info(PrintUtil.formatResponse(response, gson.toJson(deployment3).toString()));
                        }
                    }
                }

            }

            deployment2 = response.parseAs(BundleActivationConfig.class);
            logger.info(PrintUtil.formatResponse(response, gson.toJson(deployment2).toString()));
            logger.info("\nDeployed revision is: "+getVersionRevision());

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.debug("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }


        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new IOException(e);
        }

        return deployment2.state;

    }


    public static String deleteBundle(ServerProfile profile, String revision)
            throws IOException {
        // get the deployed revision
        String deployed_revision = "";

        try {
            deployed_revision = getDeployedRevision(profile);
        } catch (Exception e) {
            throw new IOException("Error fetching deployed revision");
        }


        if (deployed_revision.equals(revision)) // the same version is the active bundle deactivate first
        {
            deactivateBundle(profile);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setContentType("application/octet-stream");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        HttpRequest deleteRestRequest = REQUEST_FACTORY.buildDeleteRequest(
                new GenericUrl(profile.getHostUrl() + "/"
                        + profile.getApi_version() + "/organizations/"
                        + profile.getOrg() + "/apis/"
                        + profile.getApplication() + "/revisions/" + revision));
        deleteRestRequest.setReadTimeout(0);
        deleteRestRequest.setHeaders(headers);

        HttpResponse response = null;

        logger.info(PrintUtil.formatRequest(deleteRestRequest));
        response = deleteRestRequest.execute();

        //		String deleteResponse = response.parseAsString();
        AppConfig deleteResponse = response.parseAs(AppConfig.class);
        logger.info(PrintUtil.formatResponse(response, gson.toJson(deleteResponse).toString()));

        //Introduce Delay
        if (Options.delay != 0) {
            try {
                logger.info("Delay of " + Options.delay + " milli second");
                Thread.sleep(Options.delay);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return deleteResponse.getRevision();

    }

    public static String getVersionRevision() {
        return versionRevision;
    }

    public static void setVersionRevision(String versionRevision) {
        RestUtil.versionRevision = versionRevision;
    }

    /*********************************************************************************************** 
     * Cache Mojo 
     **/
    public static String createCache(ServerProfile profile, File cacheConfig)
            throws IOException {

        FileContent fContent = new FileContent("application/json", cacheConfig);
        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg() + "/environments/"
                + profile.getEnvironment() + "/caches";

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            logger.info("output " + response.getContentType());
            logger.info(response.parseAsString());
            // KVMResponse kvmConf = response.parseAs(KVMResponse.class);
            // logger.info(PrintUtil.formatResponse(response, gson.toJson(kvmConf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();

    }

    /*********************************************************************************************** 
     * Vault Mojo 
     **/
    public static String createVault(ServerProfile profile, File vaultConfig, boolean orgLevel)
            throws IOException {

        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg();

        String vaultName = FilenameUtils.removeExtension(vaultConfig.getName());
        Map<String, String> json = new HashMap<String, String>();
        json.put("name", vaultName);
        final HttpContent fContent = new JsonHttpContent(new JacksonFactory(), json);

        logger.info("vault name payload " + json.toString());

        if (orgLevel) {
            importCmd += "/vaults";
        } else {
            importCmd += "/environments/" + profile.getEnvironment() + "/vaults";
        }

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            logger.info("output " + response.getContentType());
            logger.info(response.parseAsString());
            // KVMResponse kvmConf = response.parseAs(KVMResponse.class);
            // logger.info(PrintUtil.formatResponse(response, gson.toJson(kvmConf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();

    }

    public static String createVaultEntries(ServerProfile profile, File vaultConfig, boolean orgLevel)
            throws IOException {

        FileContent fContent = new FileContent("application/json", vaultConfig);
        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg();

        String vaultName = FilenameUtils.removeExtension(vaultConfig.getName());

        if (orgLevel) {
            importCmd += "/vaults/" + vaultName + "/entries";
        } else {
            importCmd += "/environments/" + profile.getEnvironment() + "/vaults/" + vaultName + "/entries";
        }

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            logger.info("output " + response.getContentType());
            logger.info(response.parseAsString());
            // KVMResponse kvmConf = response.parseAs(KVMResponse.class);
            // logger.info(PrintUtil.formatResponse(response, gson.toJson(kvmConf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();
    }

    /*********************************************************************************************** 
     * APIProduct Mojo 
     **/
    public static String createAPIProduct(ServerProfile profile, File apiproductConfig)
            throws IOException {

        FileContent fContent = new FileContent("application/json", apiproductConfig);
        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg() + "/apiproducts";

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            logger.info("output " + response.getContentType());
            logger.info(response.parseAsString());
            // KVMResponse kvmConf = response.parseAs(KVMResponse.class);
            // logger.info(PrintUtil.formatResponse(response, gson.toJson(kvmConf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();

    }

    /*********************************************************************************************** 
     * KVM Mojo 
     **/
    public static String createKVM(ServerProfile profile, File kvmConfig, boolean orgLevel)
            throws IOException {

        FileContent fContent = new FileContent("application/json", kvmConfig);
        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg();

        if (orgLevel) {
            importCmd += "/keyvaluemaps/";
        } else {
            importCmd += "/environments/" + profile.getEnvironment() + "/keyvaluemaps/";
        }

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            logger.info("output " + response.getContentType());
            logger.info(response.parseAsString());
            // KVMResponse kvmConf = response.parseAs(KVMResponse.class);
            // logger.info(PrintUtil.formatResponse(response, gson.toJson(kvmConf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();
    }

    /*********************************************************************************************** 
     * TargetServer Mojo 
     **/
    public static String createTargetServer(ServerProfile profile, File targetServerConfig)
            throws IOException {

        FileContent fContent = new FileContent("application/json", targetServerConfig);
        //testing
        logger.debug("URL parameters API Version{}", (profile.getApi_version()));
        logger.debug("URL parameters URL {}", (profile.getHostUrl()));
        logger.debug("URL parameters Org{}", (profile.getOrg()));
        logger.debug("URL parameters App {}", (profile.getApplication()));

        //Forcefully validate before deployment
        String importCmd = profile.getHostUrl() + "/"
                + profile.getApi_version() + "/organizations/"
                + profile.getOrg() + "/environments/"
                + profile.getEnvironment() + "/targetservers";

        HttpRequest restRequest = REQUEST_FACTORY.buildPostRequest(
                new GenericUrl(importCmd), fContent);
        restRequest.setReadTimeout(0);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept("application/json");
        headers.setBasicAuthentication(profile.getCredential_user(),
                profile.getCredential_pwd());
        restRequest.setHeaders(headers);

        logger.info(PrintUtil.formatRequest(restRequest));

        try {
            HttpResponse response = restRequest.execute();
            logger.info("output " + response.getContentType());
            logger.info(response.parseAsString());
            // KVMResponse kvmConf = response.parseAs(KVMResponse.class);
            // logger.info(PrintUtil.formatResponse(response, gson.toJson(kvmConf).toString()));

            //Introduce Delay
            if (Options.delay != 0) {
                try {
                    logger.info("Delay of " + Options.delay + " milli second");
                    Thread.sleep(Options.delay);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (HttpResponseException e) {
            logger.error(e.getMessage());
            throw new IOException(e.getMessage());
        }

        return getVersionRevision();

    }

}
