package dev.abstratium.abstrauth.boundary.api;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.abstratium.abstrauth.service.AccountService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Path("/api/profile-picture")
@Tag(name = "Profile Picture Proxy", description = "Proxy for external profile pictures to avoid rate limiting")
@PermitAll
public class ProfilePictureProxyResource {

    private final HttpClient httpClient;

    public ProfilePictureProxyResource() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @GET
    @Path("/{provider}/{imageId: .+}")
    @Produces({"image/jpeg", "image/png", "image/gif", "image/webp"})
    @Operation(summary = "Proxy profile picture", description = "Proxies external profile pictures to avoid rate limiting")
    public Response getProfilePicture(
            @PathParam("provider") String provider,
            @PathParam("imageId") String imageId) {
        
        String imageUrl = buildImageUrl(provider, imageId);
        
        if (imageUrl == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Unsupported provider")
                    .build();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                String contentType = response.headers()
                        .firstValue("Content-Type")
                        .orElse("image/jpeg");

                return Response.ok(response.body())
                        .header("Content-Type", contentType)
                        .header("Cache-Control", "public, max-age=86400") // Cache for 24 hours
                        .build();
            } else {
                return Response.status(response.statusCode())
                        .entity("Failed to fetch image")
                        .build();
            }
        } catch (IOException | InterruptedException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error fetching profile picture")
                    .build();
        }
    }

    private String buildImageUrl(String provider, String imageId) {
        switch (provider.toLowerCase()) {
            case AccountService.GOOGLE:
                // Google profile pictures from lh3.googleusercontent.com
                return "https://lh3.googleusercontent.com/" + imageId;
            // case AccountService.GITHUB:
                // GitHub avatars
                // return "https://avatars.githubusercontent.com/u/" + imageId;
            default:
                return null;
        }
    }
}
