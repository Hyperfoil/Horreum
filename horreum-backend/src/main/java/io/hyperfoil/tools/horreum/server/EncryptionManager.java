package io.hyperfoil.tools.horreum.server;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class EncryptionManager {
   @ConfigProperty(name = "horreum.db.secret")
   String dbSecret;
   char[] dbSecretChars;

   private SecretKeyFactory factory;

   @PostConstruct
   void init() throws NoSuchAlgorithmException {
      dbSecretChars = dbSecret.toCharArray();
      factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
   }

   private SecretKey secretKey(byte[] salt) throws InvalidKeySpecException {
      KeySpec spec = new PBEKeySpec(dbSecretChars, salt, 65536, 256);
      return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
   }

   public String encrypt(String plaintext) throws GeneralSecurityException  {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      byte[] salt = new byte[16];
      new SecureRandom().nextBytes(salt);

      cipher.init(Cipher.ENCRYPT_MODE, secretKey(salt), new GCMParameterSpec(128, salt));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      Base64.Encoder encoder = Base64.getEncoder();
      return encoder.encodeToString(salt) + ";" + encoder.encodeToString(cipherText);
   }

   public String decrypt(String ciphertext) throws GeneralSecurityException {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

      int semicolon = ciphertext.indexOf(';');
      if (semicolon < 0) {
         throw new IllegalArgumentException("Invalid format, expecting IV;ciphertext");
      }
      byte[] salt = Base64.getDecoder().decode(ciphertext.substring(0, semicolon));

      cipher.init(Cipher.DECRYPT_MODE, secretKey(salt), new GCMParameterSpec(128, salt));
      byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(ciphertext.substring(semicolon + 1)));
      return new String(plainText, StandardCharsets.UTF_8);
   }
}
