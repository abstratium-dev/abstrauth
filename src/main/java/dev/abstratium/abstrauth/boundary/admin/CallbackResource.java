package dev.abstratium.abstrauth.boundary.admin;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/callback")
@Tag(name = "Callback Interface", description = "Callback interface for OAuth flow, for logging into the admin UI")
public class CallbackResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "OAuth callback page", description = "Displays authorization code and allows token exchange")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error,
            @QueryParam("error_description") String errorDescription) {

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>OAuth Callback</title>")
            .append("<style>")
            .append("body{font-family:Arial,sans-serif;max-width:800px;margin:50px auto;padding:20px;}")
            .append("h1{color:#28a745;}")
            .append(".error{color:#dc3545;}")
            .append(".code-box{background:#f5f5f5;padding:15px;border-radius:5px;margin:20px 0;word-break:break-all;}")
            .append("input,textarea{width:100%;padding:10px;margin:5px 0;box-sizing:border-box;font-family:monospace;}")
            .append("button{padding:10px 20px;background:#007bff;color:white;border:none;cursor:pointer;margin:5px;}")
            .append("button:hover{background:#0056b3;}")
            .append(".result{background:#f8f9fa;padding:15px;border-radius:5px;margin-top:20px;white-space:pre-wrap;font-family:monospace;}")
            .append("</style>")
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

        if (error != null) {
            html.append("<h1 class='error'>Authorization Error</h1>")
                .append("<div class='code-box'>")
                .append("<strong>Error:</strong> ").append(error).append("<br>")
                .append("<strong>Description:</strong> ").append(errorDescription != null ? errorDescription : "N/A")
                .append("</div>");
        } else if (code != null) {
            html.append("<h1>Authorization Successful!</h1>")
                .append("<p>You have successfully authorized the application.</p>")
                .append("<div class='code-box'>")
                .append("<strong>Authorization Code:</strong><br>").append(code)
                .append("</div>");

            if (state != null) {
                html.append("<div class='code-box'>")
                    .append("<strong>State:</strong> ").append(state)
                    .append("</div>");
            }

            html.append("<h2>Exchange Code for Token</h2>")
                .append("<p>Enter your code verifier to exchange the authorization code for an access token:</p>")
                .append("<label>Authorization Code:</label>")
                .append("<input type='text' id='code' value='").append(code).append("' readonly/>")
                .append("<label>Code Verifier (from PKCE):</label>")
                .append("<input type='text' id='codeVerifier' placeholder='Enter your code_verifier'/>")
                .append("<label>Client ID:</label>")
                .append("<input type='text' id='clientId' value='abstrauth_admin_app'/>")
                .append("<label>Redirect URI:</label>")
                .append("<input type='text' id='redirectUri' value='http://localhost:8080/callback'/>")
                .append("<button onclick='exchangeToken()'>Exchange for Token</button>")
                .append("<h3>Token Response:</h3>")
                .append("<div id='result' class='result'>Click 'Exchange for Token' to get your access token</div>")
                .append("<script>")
                .append("// Auto-populate code_verifier from sessionStorage\n")
                .append("window.addEventListener('DOMContentLoaded', function() {\n")
                .append("  const storedVerifier = sessionStorage.getItem('code_verifier');\n")
                .append("  if (storedVerifier) {\n")
                .append("    document.getElementById('codeVerifier').value = storedVerifier;\n")
                .append("    // Clear it after use for security\n")
                .append("    sessionStorage.removeItem('code_verifier');\n")
                .append("  }\n")
                .append("});\n")
                .append("</script>");
        } else {
            html.append("<h1>OAuth Callback</h1>")
                .append("<p>This is the OAuth callback endpoint. You will be redirected here after authorization.</p>");
        }

        html.append("</body></html>");

        return Response.ok(html.toString()).build();
    }
}
