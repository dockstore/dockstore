/*
 * Copyright 2021 OICR and UCSC
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

package io.dockstore.webservice.core.database;

public class UserInfo {

    private String dockstoreUsername;
    private String thirdPartyUsername;
    private String thirdPartyEmail;
    private String tokenType;

    public UserInfo() {

    }

    public UserInfo(String dockstoreUsername, String thirdPartyUsername, String thirdPartyEmail, String tokenType) {
        this.dockstoreUsername = dockstoreUsername;
        this.thirdPartyUsername = thirdPartyUsername;
        this.thirdPartyEmail = thirdPartyEmail;
        this.tokenType = tokenType;
    }

    public String getDockstoreUsername() {
        return dockstoreUsername;
    }

    public void setDockstoreUsername(String dockstoreUsername) {
        this.dockstoreUsername = dockstoreUsername;
    }


    public String getThirdPartyUsername() {
        return thirdPartyUsername;
    }

    public void setThirdPartyUsername(String thirdPartyUsername) {
        this.thirdPartyUsername = thirdPartyUsername;
    }

    public String getThirdPartyEmail() {
        return thirdPartyEmail;
    }

    public void setThirdPartyEmail(String thirdPartyEmail) {
        this.thirdPartyEmail = thirdPartyEmail;
    }


    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

}
