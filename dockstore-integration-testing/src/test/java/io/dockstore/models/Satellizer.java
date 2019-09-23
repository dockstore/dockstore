/*
 *    Copyright 2019 OICR
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

package io.dockstore.models;

/**
 * This is the model that the UI currently uses.
 * When the UI changes the satellizer object, this class should be regenerated.
 * https://www.site24x7.com/tools/json-to-java.html and some small modifications was used to generate this class from a sample JSON
 *
 * @author gluu
 * @since 28/06/19
 */
@SuppressWarnings({"checkstyle:MemberName", "checkstyle:ParameterName", "checkstyle:MethodName"})
public class Satellizer {
    private AuthorizationData authorizationData;
    private OauthData oauthData;
    private UserData userData;

    // Getter Methods

    public AuthorizationData getAuthorizationData() {
        return authorizationData;
    }

    public void setAuthorizationData(AuthorizationData authorizationDataObject) {
        this.authorizationData = authorizationDataObject;
    }

    public OauthData getOauthData() {
        return oauthData;
    }

    // Setter Methods

    public void setOauthData(OauthData oauthDataObject) {
        this.oauthData = oauthDataObject;
    }

    public UserData getUserData() {
        return userData;
    }

    public void setUserData(UserData userDataObject) {
        this.userData = userDataObject;
    }

    public class UserData {
        private boolean register;

        // Getter Methods

        public boolean getRegister() {
            return register;
        }

        // Setter Methods

        public void setRegister(boolean register) {
            this.register = register;
        }
    }

    public class OauthData {
        private String state;
        private String code;
        private String scope;

        // Getter Methods

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getCode() {
            return code;
        }

        // Setter Methods

        public void setCode(String code) {
            this.code = code;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }

    public class AuthorizationData {
        private String response_type;
        private String client_id;
        private String redirect_uri;
        private String state;
        private String scope;
        private String display;

        // Getter Methods

        public String getResponse_type() {
            return response_type;
        }

        public void setResponse_type(String response_type) {
            this.response_type = response_type;
        }

        public String getClient_id() {
            return client_id;
        }

        public void setClient_id(String client_id) {
            this.client_id = client_id;
        }

        public String getRedirect_uri() {
            return redirect_uri;
        }

        public void setRedirect_uri(String redirect_uri) {
            this.redirect_uri = redirect_uri;
        }

        // Setter Methods

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }
}
