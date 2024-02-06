/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.helpers;

import io.dropwizard.jersey.errors.ErrorMessage;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.hibernate.TransactionException;

@Provider
public class TransactionExceptionMapper implements ExceptionMapper<TransactionException> {

    @Override
    public Response toResponse(TransactionException e) {

        return processResponse(e);
    }

    public static Response processResponse(Exception e) {
        StringBuilder builder = new StringBuilder();
        builder.append(e.getMessage()).append(' ');
        Throwable cause = e.getCause();
        while (cause != null) {
            builder.append(cause.getMessage());
            cause = cause.getCause();
            builder.append(' ');
        }
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity(new ErrorMessage(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), builder.toString()))
            .build();
    }
}
