package dev.abstratium.abstrauth.boundary.admin;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/")
@Tag(name = "Admin App", description = "Admin app for OAuth flow")
public class AdminAppResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Admin app page", description = "Allows the user to sign in")
    public Response signIn() {

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Admin App</title>")
            .append("<script>")
            .append("async function exchangeToken() {")
            .append("  const code = document.getElementById('code').value;")
            .append("  const codeVerifier = document.getElementById('codeVerifier').value;")
            .append("  const clientId = document.getElementById('clientId').value;")
            .append("  const redirectUri = document.getElementById('redirectUri').value;")
            .append("  const resultDiv = document.getElementById('result');")
            .append("  try {")
            .append("    const params = new URLSearchParams();")
            .append("    params.append('grant_type', 'authorization_code');")
            .append("    params.append('code', code);")
            .append("    params.append('client_id', clientId);")
            .append("    params.append('redirect_uri', redirectUri);")
            .append("    params.append('code_verifier', codeVerifier);")
            .append("    const response = await fetch('/oauth2/token', {")
            .append("      method: 'POST',")
            .append("      headers: {'Content-Type': 'application/x-www-form-urlencoded'},")
            .append("      body: params")
            .append("    });")
            .append("    const data = await response.json();")
            .append("    resultDiv.textContent = JSON.stringify(data, null, 2);")
            .append("    resultDiv.style.color = response.ok ? '#28a745' : '#dc3545';")
            .append("  } catch (error) {")
            .append("    resultDiv.textContent = 'Error: ' + error.message;")
            .append("    resultDiv.style.color = '#dc3545';")
            .append("  }")
            .append("}")
            .append("</script>")
            .append("</head><body>");

        var script = """
function generateRandomString(length) {
  let text = '';
  const possible = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  
  for (let i = 0; i < length; i++) {
    text += possible.charAt(Math.floor(Math.random() * possible.length));
  }
  
  return text;
}

async function generateCodeChallenge(codeVerifier) {
  const encoder = new TextEncoder();
  const data = encoder.encode(codeVerifier);
  const digest = await window.crypto.subtle.digest('SHA-256', data);
  
  // Convert to base64url format
  const base64 = btoa(String.fromCharCode(...new Uint8Array(digest)));
  return base64
    .replace(/\\+/g, '-')
    .replace(/\\//g, '_')
    .replace(/=/g, '');
}

async function login() {
  const codeVerifier = generateRandomString(128);
  console.log(codeVerifier);
  const codeChallenge = await generateCodeChallenge(codeVerifier);
  const state = generateRandomString(32);
  
  // Store for later use
  sessionStorage.setItem('code_verifier', codeVerifier);
  sessionStorage.setItem('state', state);
  
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: 'abstrauth_admin_app',
    redirect_uri: 'http://localhost:8080/callback',
    scope: 'openid profile email',
    state: state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256'
  });
  
  window.location.href = `/oauth2/authorize?${params}`;
}
                """;

        html.append("<h1>Admin App</h1>")
            .append("<button onclick='login()'>Sign In with OAuth</button>")
            .append("<hr style='margin: 40px 0;'>")
            .append("<h2>PKCE Code Challenge Verification</h2>")
            .append("<p>Verifying that the JavaScript PKCE implementation is working correctly...</p>")
            .append("<div id='testResult1' class='result'></div>")
            .append("<div id='testResult2' class='result'></div>")
            .append("<div id='testResult3' class='result'></div>")
            .append("<script>" + script + "</script>")
            .append("<script>")
            .append("async function testKnownVerifier() {")
            .append("  const verifier = 'qzm3Wha8q91KfWi6uxhvFnfb3fjApYOJbTgrcJ775uQwPuGrpiiWtiIls4gTt76oz45xJlSrbZZMAC08ZYhApycfQJBwPPxlq9bXc4DEv3fadTpf8lmyF8ss3YkiqWnZ';")
            .append("  const challenge = await generateCodeChallenge(verifier);")
            .append("  const expected = '-SP-ECNprkCj4uXN335qZBTszctq5QJibPKkTk0HvJs';")
            .append("  const match = challenge === expected;")
            .append("  const result = document.getElementById('testResult1');")
            .append("  result.innerHTML = `<strong>✓ Test 1: Known Verifier</strong><br>")
            .append("    Code Challenge: ${challenge}<br>")
            .append("    Expected: ${expected}<br>")
            .append("    <strong style='color: ${match ? 'green' : 'red'}'>")
            .append("    ${match ? '✅ PASS' : '❌ FAIL'}</strong>`;")
            .append("  return match;")
            .append("}")
            .append("async function testRFC7636Example() {")
            .append("  const verifier = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';")
            .append("  const challenge = await generateCodeChallenge(verifier);")
            .append("  const expected = 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM';")
            .append("  const match = challenge === expected;")
            .append("  const result = document.getElementById('testResult2');")
            .append("  result.innerHTML = `<strong>✓ Test 2: RFC 7636 Example</strong><br>")
            .append("    Code Challenge: ${challenge}<br>")
            .append("    Expected: ${expected}<br>")
            .append("    <strong style='color: ${match ? 'green' : 'red'}'>")
            .append("    ${match ? '✅ PASS' : '❌ FAIL'}</strong>`;")
            .append("  return match;")
            .append("}")
            .append("async function testRandomVerifier() {")
            .append("  const verifier = generateRandomString(128);")
            .append("  const challenge = await generateCodeChallenge(verifier);")
            .append("  const validLength = challenge.length === 43;")
            .append("  const validFormat = !/[+/=]/.test(challenge);")
            .append("  const pass = validLength && validFormat;")
            .append("  const result = document.getElementById('testResult3');")
            .append("  result.innerHTML = `<strong>✓ Test 3: Random Verifier</strong><br>")
            .append("    Code Verifier: ${verifier.substring(0, 50)}...<br>")
            .append("    Code Challenge: ${challenge}<br>")
            .append("    Length: ${challenge.length} (expected 43) ${validLength ? '✅' : '❌'}<br>")
            .append("    Format: Base64URL ${validFormat ? '✅' : '❌'}<br>")
            .append("    <strong style='color: ${pass ? 'green' : 'red'}'>")
            .append("    ${pass ? '✅ PASS' : '❌ FAIL'}</strong>`;")
            .append("  return pass;")
            .append("}")
            .append("// Run all tests on page load\n")
            .append("window.addEventListener('DOMContentLoaded', async function() {")
            .append("  const test1 = await testKnownVerifier();")
            .append("  const test2 = await testRFC7636Example();")
            .append("  const test3 = await testRandomVerifier();")
            .append("  if (test1 && test2 && test3) {")
            .append("    console.log('✅ All PKCE tests passed!');")
            .append("  } else {")
            .append("    console.error('❌ Some PKCE tests failed!');")
            .append("  }")
            .append("});")
            .append("</script>")
            .append("</body></html>");

        return Response.ok(html.toString()).build();
    }
}
