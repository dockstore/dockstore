/*
 *    Copyright 2022 OICR and UCSC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.dockstore.common.BitBucketTest;
import io.dockstore.webservice.core.BioWorkflow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * @author dyuen
 */
@Tag(BitBucketTest.NAME)
class BitBucketSourceCodeRepoFactoryTest {

    /**
     * Tests that parsing a BitBucket URL works
     */
    @Test
    void testBitBucketUrlParsing() {
        BitBucketSourceCodeRepo repo = new BitBucketSourceCodeRepo("fakeUser", "fakeToken");
        final BioWorkflow entry = new BioWorkflow();

        /* Test good URLs */
        entry.setGitUrl("git@bitbucket.org:dockstore/dockstore-ui.git");
        String bitBucketId = repo.getRepositoryId(entry);
        assertEquals("dockstore/dockstore-ui", bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore/dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("dockstore/dockstore-ui", bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore-cow/goat.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("dockstore-cow/goat", bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore.dot/goat.bat.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("dockstore.dot/goat.bat", bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore.dot/goat.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("dockstore.dot/goat", bitBucketId, "Bitbucket ID parse check");

        /* Test bad URLs */
        entry.setGitUrl("git@bitbucket.org/dockstore/dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertNull(bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore:dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertNull(bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:/dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertNull(bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore");
        bitBucketId = repo.getRepositoryId(entry);
        assertNull(bitBucketId, "Bitbucket ID parse check");

        entry.setGitUrl("git@bitbucket.org:dockstore/dockstore-ui");
        bitBucketId = repo.getRepositoryId(entry);
        assertNull(bitBucketId, "Bitbucket ID parse check");

    }
}
