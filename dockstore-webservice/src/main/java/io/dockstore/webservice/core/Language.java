package io.dockstore.webservice.core;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import io.swagger.annotations.ApiModelProperty;

@Embeddable
public class Language implements Serializable {
    @Column(name = "language", nullable = false, columnDefinition = "varchar(50)")
    @ApiModelProperty(value = "The supported language")
    private String language;

    @Column(name = "version", nullable = true, columnDefinition = "varchar(50)")
    @ApiModelProperty(value = "The version of the supported language")
    private String version;


}
