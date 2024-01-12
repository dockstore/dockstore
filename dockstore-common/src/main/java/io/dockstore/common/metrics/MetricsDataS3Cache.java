/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.common.metrics;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsDataS3Cache {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsDataS3Cache.class);

    private static final MetricsDataS3Cache METRICS_DATA_S3_CACHE = new MetricsDataS3Cache();

    private static volatile LoadingCache<String, String> s3KeyToFileContentCache;
    private static MetricsDataS3Client metricsDataS3Client;


    public static MetricsDataS3Cache getInstance() {
        return METRICS_DATA_S3_CACHE;
    }
    /**
     * Initialize the cache for GitHub api clients
     */
    public static void initCache(String metricsBucketName, String s3EndpointOverride) throws URISyntaxException {
        if (metricsDataS3Client == null) {
            metricsDataS3Client = new MetricsDataS3Client(metricsBucketName, s3EndpointOverride);
        }
        final int maxSize = 1000;
        // provides self-renew
        if (s3KeyToFileContentCache == null) {
            s3KeyToFileContentCache = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .recordStats()
                    .expireAfterWrite(1, TimeUnit.MINUTES)
                    .build(s3Key -> {
                        LOG.info("Fetching file content for S3 key {} from cache", s3Key);
                        return getFileContentForS3Key(s3Key);
                    });
        }
    }

    private static String getFileContentForS3Key(String s3Key) throws IOException {
        return metricsDataS3Client.getMetricsDataFileContentByKey(s3Key);
    }

    /**
     * Load S3 object from cache
     * @param s3Key
     * @return S3 object file content as a string
     */
    public Optional<String> getS3ObjectFromCache(String s3Key) {
        try {
            CacheStats cacheStats = s3KeyToFileContentCache.stats();
            LOG.info(cacheStats.toString());
            return Optional.of(s3KeyToFileContentCache.get(s3Key));
        } catch (Exception e) {
            LOG.error("Error retrieving S3 object from cache for key {}", s3Key, e);
        }
        return Optional.empty();
    }
}
