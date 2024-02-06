/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.jetty.HttpConnectorFactory;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http.UriCompliance.Violation;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;

@JsonTypeName("custom-http")
public class CustomHttpConnectorFactory extends HttpConnectorFactory {

    @Override
    protected HttpConnectionFactory buildHttpConnectionFactory(HttpConfiguration httpConfig) {
        UriCompliance custom = UriCompliance.DEFAULT.with("custom", Violation.AMBIGUOUS_EMPTY_SEGMENT);
        httpConfig.setUriCompliance(custom);
        return super.buildHttpConnectionFactory(httpConfig);
    }
}
