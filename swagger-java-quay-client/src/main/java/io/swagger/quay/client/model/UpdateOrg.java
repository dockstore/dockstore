/*
 *    Copyright 2016 OICR
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

package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of updates for an existing organization
 **/
@ApiModel(description = "Description of updates for an existing organization")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:13:48.378-04:00")
public class UpdateOrg   {
  
  private Boolean invoiceEmail = null;
  private String email = null;
  private Integer tagExpiration = null;

  
  /**
   * Whether the organization desires to receive emails for invoices
   **/
  @ApiModelProperty(value = "Whether the organization desires to receive emails for invoices")
  @JsonProperty("invoice_email")
  public Boolean getInvoiceEmail() {
    return invoiceEmail;
  }
  public void setInvoiceEmail(Boolean invoiceEmail) {
    this.invoiceEmail = invoiceEmail;
  }

  
  /**
   * Organization contact email
   **/
  @ApiModelProperty(value = "Organization contact email")
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }
  public void setEmail(String email) {
    this.email = email;
  }

  
  /**
   * minimum: 0.0
   * maximum: 2592000.0
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("tag_expiration")
  public Integer getTagExpiration() {
    return tagExpiration;
  }
  public void setTagExpiration(Integer tagExpiration) {
    this.tagExpiration = tagExpiration;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateOrg {\n");
    
    sb.append("    invoiceEmail: ").append(StringUtil.toIndentedString(invoiceEmail)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("    tagExpiration: ").append(StringUtil.toIndentedString(tagExpiration)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
