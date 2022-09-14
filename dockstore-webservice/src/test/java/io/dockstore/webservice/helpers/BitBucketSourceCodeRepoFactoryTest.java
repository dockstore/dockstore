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

import static org.junit.Assert.assertEquals;

import io.dockstore.common.BitBucketTest;
import io.dockstore.webservice.core.BioWorkflow;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author dyuen
 */
@Category(BitBucketTest.class)
public class BitBucketSourceCodeRepoFactoryTest {

    /**
     * Tests that parsing a BitBucket URL works
     */
    @Test
    public void testBitBucketUrlParsing() {
        BitBucketSourceCodeRepo repo = new BitBucketSourceCodeRepo("fakeUser", "fakeToken");
        final BioWorkflow entry = new BioWorkflow();

        /* Test good URLs */
        entry.setGitUrl("git@bitbucket.org:dockstore/dockstore-ui.git");
        String bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", "dockstore/dockstore-ui", bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore/dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", "dockstore/dockstore-ui", bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore-cow/goat.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", "dockstore-cow/goat", bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore.dot/goat.bat.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", "dockstore.dot/goat.bat", bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore.dot/goat.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", "dockstore.dot/goat", bitBucketId);

        /* Test bad URLs */
        entry.setGitUrl("git@bitbucket.org/dockstore/dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", null, bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore:dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", null, bitBucketId);

        entry.setGitUrl("git@bitbucket.org:/dockstore-ui.git");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", null, bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", null, bitBucketId);

        entry.setGitUrl("git@bitbucket.org:dockstore/dockstore-ui");
        bitBucketId = repo.getRepositoryId(entry);
        assertEquals("Bitbucket ID parse check", null, bitBucketId);

    }
}
