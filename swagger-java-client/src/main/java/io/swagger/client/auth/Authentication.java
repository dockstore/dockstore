package io.swagger.client.auth;

import io.swagger.client.Pair;

import java.util.Map;
import java.util.List;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-02T16:12:35.652-05:00")
public interface Authentication {
  /** Apply authentication settings to header and query params. */
  void applyToParams(List<Pair> queryParams, Map<String, String> headerParams);
}
