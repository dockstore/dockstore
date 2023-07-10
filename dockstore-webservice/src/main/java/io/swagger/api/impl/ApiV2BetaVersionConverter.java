/*
 *    Copyright 2020 OICR
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
package io.swagger.api.impl;

import com.google.common.collect.Lists;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.openapi.model.Checksum;
import io.openapi.model.ImageData;
import io.swagger.model.DescriptorTypeV20beta;
import io.swagger.model.ExtendedFileWrapper;
import io.swagger.model.FileWrapperV20beta;
import io.swagger.model.MetadataV1;
import io.swagger.model.MetadataV20beta;
import io.swagger.model.ToolClassV20beta;
import io.swagger.model.ToolFileV20beta;
import io.swagger.model.ToolV20beta;
import io.swagger.model.ToolVersionV20beta;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts between the V2-final version of the GA4GH TRS to V2-beta.5
 *
 * @author gluu, dyuen
 * @since 21/12/17
 */
public final class ApiV2BetaVersionConverter {
    private static final Logger LOG = LoggerFactory.getLogger(ApiV2BetaVersionConverter.class);

    static {
        ConvertUtils.register(new ToolClassConverter(), ToolClassV20beta.class);
        ConvertUtils.register(new DescriptorTypeConverter(), DescriptorTypeV20beta.class);
    }

    private ApiV2BetaVersionConverter() {
        // utility class
    }

    public static Response convertToVersion(Response response) {
        Object object = response.getEntity();
        if (object instanceof List) {
            List<Object> arrayList = (List<Object>)object;
            List<Object> newArrayList = new ArrayList<>();
            for (Object innerObject : arrayList) {
                if (innerObject instanceof io.openapi.model.Tool tool) {
                    newArrayList.add(getTool(tool));
                } else if (innerObject instanceof io.openapi.model.ToolVersion toolVersion) {
                    newArrayList.add(getToolVersion(toolVersion));
                } else if (innerObject instanceof io.openapi.model.ExtendedFileWrapper) {
                    Object extendedWrapperConverted = getWrapper((io.openapi.model.ExtendedFileWrapper)innerObject);
                    newArrayList.add(extendedWrapperConverted);
                } else if (innerObject instanceof io.openapi.model.ToolFile toolFile) {
                    final ToolFileV20beta oldToolFile = getOldToolFile(toolFile);
                    newArrayList.add(oldToolFile);
                } else {
                    newArrayList.add(innerObject);
                }
            }
            return getResponse(newArrayList, response.getHeaders());
        } else if (object instanceof io.openapi.model.ToolVersion) {
            io.openapi.model.ToolVersion toolVersion = (io.openapi.model.ToolVersion)object;
            ToolVersionV20beta betaToolVersion = getToolVersion(toolVersion);
            return getResponse(betaToolVersion, response.getHeaders());
        } else if (object instanceof io.openapi.model.Tool tool) {
            ToolV20beta betaTool = getTool(tool);
            return getResponse(betaTool, response.getHeaders());
        } else if (object instanceof MetadataV20beta metadata) {
            MetadataV1 metadataV1 = new MetadataV1(metadata);
            return getResponse(metadataV1, response.getHeaders());
        } else if (object instanceof io.openapi.model.FileWrapper) {
            if (object instanceof io.openapi.model.ExtendedFileWrapper) {
                return getResponse(getWrapper((io.openapi.model.ExtendedFileWrapper)object), response.getHeaders());
            }
            return getResponse(object, response.getHeaders());
        }
        return response;
    }

    public static ToolV20beta getTool(io.openapi.model.Tool tool) {
        ToolV20beta betaTool = new ToolV20beta();
        try {
            BeanUtils.copyProperties(betaTool, tool);
            betaTool.setUrl(betaTool.getUrl().replace(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_FINAL, DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA));
            betaTool.setCheckerUrl(betaTool.getCheckerUrl().replace(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_FINAL, DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA));
            betaTool.setToolname(tool.getName());
            betaTool.setHasChecker(tool.isHasChecker());
            Set<String> authors = new HashSet<>();
            tool.getVersions().stream().map(io.openapi.model.ToolVersion::getAuthor).filter(Objects::nonNull).forEach(authors::addAll);
            if (authors.isEmpty()) {
                betaTool.setAuthor("Unknown author");
            } else {
                betaTool.setAuthor(String.join("", authors));
            }

            betaTool.setSigned(false);
            betaTool.setContains(Lists.newArrayList());
            // convert versions now
            betaTool.setVersions(new ArrayList<>());
            for (io.openapi.model.ToolVersion version : tool.getVersions()) {
                ToolVersionV20beta oldVersion = getToolVersion(version);
                betaTool.getVersions().add(oldVersion);
            }
            betaTool.setVerified(tool.getVersions().stream().anyMatch(io.openapi.model.ToolVersion::isVerified));
            Set<String> sources = new HashSet<>();
            tool.getVersions().stream().map(io.openapi.model.ToolVersion::getVerifiedSource).forEach(sources::addAll);
            betaTool.setVerifiedSource(sources.isEmpty() ? "[]" : sources.stream().collect(Collectors.joining("\",\"", "[\"", "\"]")));
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error("unable to backwards convert tool");
            throw new RuntimeException(e);
        }
        return betaTool;
    }

    public static ToolVersionV20beta getToolVersion(io.openapi.model.ToolVersion toolVersion) {
        ToolVersionV20beta betaToolVersion = new ToolVersionV20beta();
        try {
            BeanUtils.copyProperties(betaToolVersion, toolVersion);
            betaToolVersion.setUrl(betaToolVersion.getUrl().replace(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_FINAL, DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA));
            // look like it has issues converting enums
            betaToolVersion.setDescriptorType(Lists.newArrayList());
            toolVersion.getDescriptorType().forEach(type -> betaToolVersion.getDescriptorType().add(DescriptorTypeV20beta.fromValue(type.name())));

            // looks like BeanUtils has issues due to https://issues.apache.org/jira/browse/BEANUTILS-321 and https://github.com/swagger-api/swagger-codegen/issues/7764
            betaToolVersion.setVerified(toolVersion.isVerified());
            betaToolVersion.setVerifiedSource(toolVersion.getVerifiedSource().isEmpty() ? "[]"
                : toolVersion.getVerifiedSource().stream().collect(Collectors.joining("\",\"", "[\"", "\"]")));
            betaToolVersion.setContainerfile(toolVersion.isContainerfile());
            betaToolVersion.setImageName(
                toolVersion.getImages().stream().filter(Objects::nonNull).map(ImageData::getImageName).collect(Collectors.joining()));
            // this is a bit weird, but seems to be current behaviour, also need to get rid of the double lambda
            final Optional<Optional<String>> first = toolVersion.getImages().stream().filter(Objects::nonNull).map(
                item -> item.getChecksum().stream().filter(check -> check.getType().equals(ToolsImplCommon.DOCKER_IMAGE_SHA_TYPE_FOR_TRS))
                    .map(Checksum::getChecksum).findFirst()).findFirst();
            if (first.isPresent() && first.get().isPresent()) {
                betaToolVersion.setImage(first.get().get());
            } else {
                betaToolVersion.setImage("");
            }
            betaToolVersion.setRegistryUrl(
                toolVersion.getImages().stream().filter(Objects::nonNull).map(ImageData::getRegistryHost).collect(Collectors.joining()));
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error("unable to backwards convert toolVersion");
            throw new RuntimeException(e);
        }
        return betaToolVersion;
    }

    public static FileWrapperV20beta getOldWrapper(io.openapi.model.FileWrapper wrapper) {
        FileWrapperV20beta oldWrapper = new FileWrapperV20beta();
        oldWrapper.setContent(wrapper.getContent());
        oldWrapper.setUrl(wrapper.getUrl());
        return oldWrapper;
    }

    public static ToolFileV20beta getOldToolFile(io.openapi.model.ToolFile toolFile) {
        ToolFileV20beta oldWToolFile = new ToolFileV20beta();
        oldWToolFile.fileType(io.swagger.model.ToolFileV20beta.FileTypeEnum.fromValue(toolFile.getFileType().toString()));
        oldWToolFile.path(toolFile.getPath());
        return oldWToolFile;
    }

    public static ExtendedFileWrapper getWrapper(io.openapi.model.ExtendedFileWrapper wrapper) {
        ExtendedFileWrapper oldWrapper = new ExtendedFileWrapper();
        oldWrapper.setOriginalFile(wrapper.getOriginalFile());
        oldWrapper.setContent(wrapper.getContent());
        oldWrapper.setUrl(wrapper.getUrl());
        return oldWrapper;
    }

    private static Response getResponse(Object object, MultivaluedMap<String, Object> headers) {
        Response.ResponseBuilder responseBuilder = Response.ok(object);
        if (!headers.isEmpty()) {
            final List<String> relevantHeaders = List.of("next_page", "last_page", "current_offset", "current_limit");
            for (String str : headers.keySet()) {
                if (relevantHeaders.contains(str)) {
                    responseBuilder.header(str, headers.getFirst(str));
                }
            }
        }
        return responseBuilder.build();
    }

    public static class DescriptorTypeConverter implements Converter {
        @Override
        public <T> T convert(Class<T> type, Object value) {
            DescriptorTypeV20beta betaToolClass = DescriptorTypeV20beta.fromValue(value.toString());
            return (T)betaToolClass;
        }
    }

    public static class ToolClassConverter implements Converter {
        @Override
        public <T> T convert(Class<T> type, Object value) {
            ToolClassV20beta betaToolClass = new ToolClassV20beta();
            try {
                BeanUtils.copyProperties(betaToolClass, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOG.error("unable to backwards convert ToolClass");
                throw new RuntimeException(e);
            }
            return (T)betaToolClass;
        }
    }
}
