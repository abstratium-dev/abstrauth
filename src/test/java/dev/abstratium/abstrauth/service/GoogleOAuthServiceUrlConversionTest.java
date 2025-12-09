package dev.abstratium.abstrauth.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class GoogleOAuthServiceUrlConversionTest {

    @Inject
    GoogleOAuthService googleOAuthService;

    @Test
    public void testConvertToProxyUrl() throws Exception {
        // Use reflection to test the private method
        Method method = GoogleOAuthService.class.getDeclaredMethod("convertToProxyUrl", String.class);
        method.setAccessible(true);

        // Test with typical Google profile picture URL
        String googleUrl = "https://lh3.googleusercontent.com/a/ACg8ocKy8J07hRZZLnri1836Ze4_wd96YdPHERLsBiAJsbeYXm8WOA=s96-c";
        String result = (String) method.invoke(googleOAuthService, googleUrl);
        
        assertEquals("/api/profile-picture/google/a/ACg8ocKy8J07hRZZLnri1836Ze4_wd96YdPHERLsBiAJsbeYXm8WOA", result);
    }

    @Test
    public void testConvertToProxyUrlWithoutSizeParameter() throws Exception {
        Method method = GoogleOAuthService.class.getDeclaredMethod("convertToProxyUrl", String.class);
        method.setAccessible(true);

        String googleUrl = "https://lh3.googleusercontent.com/a/ACg8ocKy8J07hRZZLnri1836Ze4_wd96YdPHERLsBiAJsbeYXm8WOA";
        String result = (String) method.invoke(googleOAuthService, googleUrl);
        
        assertEquals("/api/profile-picture/google/a/ACg8ocKy8J07hRZZLnri1836Ze4_wd96YdPHERLsBiAJsbeYXm8WOA", result);
    }

    @Test
    public void testConvertToProxyUrlWithNullInput() throws Exception {
        Method method = GoogleOAuthService.class.getDeclaredMethod("convertToProxyUrl", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(googleOAuthService, (String) null);
        
        assertNull(result);
    }

    @Test
    public void testConvertToProxyUrlWithNonGoogleUrl() throws Exception {
        Method method = GoogleOAuthService.class.getDeclaredMethod("convertToProxyUrl", String.class);
        method.setAccessible(true);

        String otherUrl = "https://example.com/picture.jpg";
        String result = (String) method.invoke(googleOAuthService, otherUrl);
        
        assertEquals(otherUrl, result);
    }
}
