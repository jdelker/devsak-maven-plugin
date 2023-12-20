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

import org.mockserver.client.MockServerClient;
import org.mockserver.client.initialize.PluginExpectationInitializer;

public class MockServerInitializer implements PluginExpectationInitializer {

  @Override
  public void initializeExpectations(MockServerClient client) {

  }

}
