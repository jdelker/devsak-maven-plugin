/*
 * Copyright 2023 jdelker.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Credits for this code goes to:
 *  - https://github.com/lopht/upload-maven-plugin/
 *  - https://github.com/sonatype/maven-upload-plugin
 */
package jdelker.maven.plugin.devsak;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.repository.Proxy;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

/**
 * Uploads file to remote repository.
 *
 */
@Mojo(name = "upload", defaultPhase = LifecyclePhase.DEPLOY)
public class UploadMojo
        extends AbstractMojo {

  /**
   * The path to the file to be uploaded.
   *
   */
  @Parameter(property = "upload.file")
  private File file;

  /**
   * Alternate parameter to specify a set of files to upload.
   *
   */
  @Parameter
  private FileSet fileSet;

  /**
   * The server Id in settings.xml with credentials to use.
   *
   */
  @Parameter(property = "upload.serverId")
  protected String serverId;

  /**
   * The base URL of the server, ie http://server.example.com/.
   *
   */
  @Parameter(property = "upload.serverUrl")
  protected String serverUrl;

  /**
   * The server path where the file will be uploaded, ie path/to/file.ext. Will
   * be appended to the <i>serverUrl</i> parameter.
   *
   */
  @Parameter(property = "upload.serverPath", defaultValue="/")
  private String serverPath;

  /**
   * If true, do not fail build when file is missing.
   *
   */
  @Parameter(property = "upload.ignoreMissing", defaultValue = "false")
  private boolean ignoreMissingFile;

  @Parameter(property = "session")
  protected MavenSession session;

  /**
   * Set to true to skip execution.
   *
   */
  @Parameter(property = "upload.skip", defaultValue = "false")
  protected boolean skip;

  /**
   * Set to true if the server requires credentials in the initial request.
   *
   */
  @Parameter(property = "upload.preemptiveAuth", defaultValue = "false")
  protected boolean preemptiveAuth;

  /**
   * Custom HTTP headers to add to each request.
   *
   */
  @Parameter
  protected Map<String, String> headers;

  /**
   * Use POST instead of PUT
   *
   */
  @Parameter(property = "upload.post", defaultValue = "false")
  protected boolean usePOST;

  @Component
  protected RepositorySystem repositorySystem;

  @Component
  protected ArtifactRepositoryLayout repositoryLayout;

  @Override
  public void execute()
          throws MojoExecutionException, MojoFailureException {

    if (skip) {
      getLog().info("Skipping execution per configuration");
      return;
    }

    List<File> filesToUpload = getFiles();

    ArtifactRepository repository = getArtifactRepository();

    CloseableHttpClient client = getHttpClient(repository);

    String url = getTargetUrl(repository);

    for (File f : filesToUpload) {
      if (ignoreMissingFile && !f.exists()) {
        getLog().info("File does not exist, ignoring " + f.getAbsolutePath());
        continue;
      }
      uploadFile(client, f, url);
    }
  }

  protected CloseableHttpClient getHttpClient(ArtifactRepository repository)
          throws MojoExecutionException {
    HttpClientBuilder clientBuilder = HttpClients.custom();
    CredentialsProvider credsProvider = null;

    Authentication authentication = repository.getAuthentication();
    if (authentication != null) {
      getLog().debug("Found credentials: username="
              + authentication.getUsername()
              + " password="
              + authentication.getPassword());
      credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(
              new AuthScope(AuthScope.ANY),
              new UsernamePasswordCredentials(authentication.getUsername(), authentication.getPassword()));
      clientBuilder.setDefaultCredentialsProvider(credsProvider);
    }

    Proxy proxy = repository.getProxy();
    if (proxy != null) {
      // NonProxyHosts is handled by ArtifactRepository, the Proxy will not be present here for NonProxyHosts
      if (proxy.getProtocol() == null || proxy.getProtocol().equalsIgnoreCase(Proxy.PROXY_HTTP)) {
        getLog().debug("Found Proxy configuration: " + proxy.getHost() + ":" + proxy.getPort());
        HttpHost proxyHost = new HttpHost(proxy.getHost(), proxy.getPort());
        clientBuilder.setProxy(proxyHost);

        if (proxy.getUserName() != null) {
          getLog().debug("Found proxy credentials: username=" + proxy.getUserName());
          // Add CredentialsProvider if one was not added for target host Authentication
          if (credsProvider == null) {
            credsProvider = new BasicCredentialsProvider();
            clientBuilder.setDefaultCredentialsProvider(credsProvider);
          }
          credsProvider.setCredentials(
                  new AuthScope(proxyHost),
                  new UsernamePasswordCredentials(proxy.getUserName(), proxy.getPassword()));
          clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
        }
      } else {
        throw new MojoExecutionException("Proxy protocol " + proxy.getProtocol() + " is not supported yet");
      }
    }
    return clientBuilder.build();
  }

  protected ArtifactRepository getArtifactRepository() {
    Objects.requireNonNull(serverUrl, "serverUrl must not be null");

    ArtifactRepositoryPolicy policy = new ArtifactRepositoryPolicy();
    ArtifactRepository repository
            = repositorySystem.createArtifactRepository(serverId, serverUrl, repositoryLayout, policy, policy);

    List<ArtifactRepository> repositories = new ArrayList<>();
    repositories.add(repository);

    // repositorySystem.injectMirror( artifactRepositories, session.getRequest().getMirrors() );
    repositorySystem.injectProxy(repositories, session.getRequest().getProxies());

    repositorySystem.injectAuthentication(repositories, session.getRequest().getServers());

    repository = repositories.get(0);
    return repository;
  }

  protected void uploadFile(CloseableHttpClient client, File file, String targetUrl)
          throws MojoExecutionException {
    getLog().info("Uploading " + file.getAbsolutePath() + " to " + targetUrl);
    HttpEntityEnclosingRequestBase request;
    if (usePOST) {
      request = new HttpPost(targetUrl);
    } else {
      request = new HttpPut(targetUrl);
    }
    CloseableHttpResponse response = null;
    try {
      // Set Content type
      ContentType contentType = null;
      if (file.getName().endsWith(".xml")) {
        contentType = ContentType.APPLICATION_XML;
      }

      // Add the file to the PUT request
      request.setEntity(new FileEntity(file, contentType));

      if (null != headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
          request.addHeader(entry.getKey(), entry.getValue());
        }
      }

      if (preemptiveAuth) {
        // Auth target host
        URL aURL = new URI(targetUrl).toURL();
        HttpHost target = new HttpHost(aURL.getHost(), aURL.getPort(), aURL.getProtocol());
        // Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        // Generate BASIC scheme object and add it to the local auth cache
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(target, basicAuth);
        // Add AuthCache to the execution context
        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAuthCache(authCache);
        // Execute request with pre-emptive authentication
        response = client.execute(request, localContext);
      } else {
        // Execute request, server will prompt for authentication if needed
        response = client.execute(request);
      }

      int status = response.getStatusLine().getStatusCode();
      if (status < 200 || status > 299) {
        String message = "Could not upload file: " + response.getStatusLine().toString();
        getLog().error(message);
        String responseBody = EntityUtils.toString(response.getEntity());
        if (responseBody != null) {
          getLog().info(responseBody);
        }
        throw new MojoExecutionException(message);
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Could not upload file: ", e);
    } catch (URISyntaxException e) {
      throw new MojoExecutionException("Invalid URL: " + targetUrl, e);
    } finally {
      request.releaseConnection();
    }
  }

  protected String getTargetUrl(ArtifactRepository repository) {
    StringBuilder sb = new StringBuilder(repository.getUrl());

    if (!repository.getUrl().endsWith("/") && !serverPath.startsWith("/")) {
      sb.append("/");
    }

    sb.append(serverPath);

    return sb.toString();
  }

  protected List<File> getFiles() throws MojoFailureException {

    List<File> fileList = new ArrayList<>();

    if (file != null) {
      fileList.add(file);
    }

    if (fileSet != null) {
      FileSetManager fileSetManager = new FileSetManager(getLog(), true);
      String[] files = fileSetManager.getIncludedFiles(fileSet);

      if (files == null || files.length == 0) {
        getLog().info("No files found from fileSet.");
      } else {
        for (String fileName : files) {
          File f = new File(fileSet.getDirectory(), fileName);
          fileList.add(f);
        }
      }
    }
    return fileList;
  }

}
