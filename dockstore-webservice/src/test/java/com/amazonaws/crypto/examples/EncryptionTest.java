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
// This sample code encrypts and then decrypts a string using a KMS CMK.
// You provide the KMS key ARN and plaintext string as arguments.
package com.amazonaws.crypto.examples;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.MasterKeyProvider;
import com.amazonaws.encryptionsdk.jce.JceMasterKey;
import com.amazonaws.encryptionsdk.multi.MultipleProviderFactory;
import org.junit.Test;

public class EncryptionTest {

    @Test
    public void demoEncrypt() {
        // code from https://github.com/aws/aws-encryption-sdk-java

        String keyArn = "foo";
        String data = "foo";

        // Instantiate the SDK
        final AwsCrypto crypto = AwsCrypto.builder().build();

        // Set up the master key provider
        SecretKey cryptoKey = retrieveEncryptionKey();
        // Create a JCE master key provider using the random key and an AES-GCM encryption algorithm
        JceMasterKey masterKey = JceMasterKey.getInstance(cryptoKey, "Example", "RandomKey", "AES/GCM/NoPadding");
        final MasterKeyProvider<?> prov = MultipleProviderFactory.buildMultiProvider(masterKey);

        // Encrypt the data
        //
        // NOTE: Encrypted data should have associated encryption context
        // to protect integrity. For this example, just use a placeholder
        // value. For more information about encryption context, see
        // https://amzn.to/1nSbe9X (blogs.aws.amazon.com)
        final Map<String, String> context = Collections.singletonMap("Example", "String");

        final String ciphertext = crypto.encryptString(prov, data, context).getResult();
        System.out.println("Ciphertext: " + ciphertext);

        // Decrypt the data
        final CryptoResult<String, ?> decryptResult = crypto.decryptString(prov, ciphertext);

        // The SDK may add information to the encryption context, so check to
        // ensure all of the values are present
        for (final Map.Entry<String, String> e : context.entrySet()) {
            if (!e.getValue().equals(decryptResult.getEncryptionContext().get(e.getKey()))) {
                throw new IllegalStateException("Wrong Encryption Context!");
            }
        }

        // The data is correct, so output it.
        System.out.println("Decrypted: " + decryptResult.getResult());
    }

    /**
     * In practice, this key would be saved in a secure location.
     * For this demo, we generate a new random key for each operation.
     */
    private static SecretKey retrieveEncryptionKey() {
        SecureRandom rnd = new SecureRandom();
        byte[] rawKey = new byte[16]; // 128 bits
        rnd.nextBytes(rawKey);
        return new SecretKeySpec(rawKey, "AES");
    }
}
