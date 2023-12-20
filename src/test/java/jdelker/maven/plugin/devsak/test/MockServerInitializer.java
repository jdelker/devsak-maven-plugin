/*
 * Copyright 2023 delker.
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
package jdelker.maven.plugin.devsak.test;

import java.util.Base64;
import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.PluginExpectationInitializer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
import static org.mockserver.model.Header.header;


public class MockServerInitializer implements PluginExpectationInitializer {

  // BASIC AUTH - base64 encoded from "user01:goodpass"
  private final static String BASIC_AUTH = 
          "Basic " + Base64.getEncoder().encodeToString("user01:goodpass".getBytes());
  
  @Override
  public void initializeExpectations(MockServerClient client) {

    // Require Basic Auth for all methods except GET
    client.when(
            request()
                    .withMethod(not("GET"))
                    .withHeaders(
                            header(not("Authorization")),
                            header(
                                    string("Authorization"),
                                    not(BASIC_AUTH)
                            )
                    )
    )
            .respond(
                    response()
                            .withStatusCode(HttpStatusCode.UNAUTHORIZED_401.code())
            );

    // Single file PUT
    client.when(
            request()
                    .withMethod("PUT")
                    .withPath("/it-put-file/file1.txt")
    )
            .respond(
                    response()
                            .withStatusCode(HttpStatusCode.NO_CONTENT_204.code())
            );

    // Single file POST
    client.when(
            request()
                    .withMethod("POST")
                    .withPath("/it-post-file/file1.txt")
    )
            .respond(
                    response()
                            .withStatusCode(HttpStatusCode.CREATED_201.code())
            );

    // Multiple files PUT
    client.when(
            request()
                    .withMethod("PUT")
                    .withPath("/it-put-files/.*")
    )
            .respond(
                    response()
                            .withStatusCode(HttpStatusCode.NO_CONTENT_204.code())
            );

    // Multiple files POST
    client.when(
            request()
                    .withMethod("POST")
                    .withPath("/it-post-files/.*")
    )
            .respond(
                    response()
                            .withStatusCode(HttpStatusCode.CREATED_201.code())
            );

  }

}
