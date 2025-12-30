# Bootstrap Client Secret Solution

## Problem Statement

The application serves dual roles:
1. **OAuth Authorization Server** - Issues tokens to external clients
2. **Self-hosted BFF** - Uses itself as an OAuth provider for its Angular UI

The Angular UI needs to authenticate as a confidential OAuth client (`abstratium-abstrauth`) to exchange authorization codes for tokens. This requires:
- A client secret in plaintext (environment variable `ABSTRAUTH_CLIENT_SECRET`)
- The same secret hashed in the database for verification during token exchange

**Challenge**: The application is open-source and distributed as a Docker image. Each deployment needs a unique client secret that is:
- Generated during first-time setup
- Stored hashed in the database
- Available as plaintext for the BFF configuration

## Implemented Solution: Auto-Sync with UI Warning

This approach provides a balance between ease of deployment and security by automatically syncing the client secret hash on startup and warning administrators through the UI if an insecure secret is detected.

### Core Mechanism

1. **Startup Behavior**:
   - Read `ABSTRAUTH_CLIENT_SECRET` from environment variable
   - Validate minimum length (32 characters recommended)
   - Hash the value using BCrypt
   - Update the `abstratium-abstrauth` client's `clientSecretHash` in database
   - This happens on EVERY startup (idempotent operation)

2. **Configuration Endpoint Enhancement**:
   - Add new field to `ConfigResource.getConfig()`: `insecureClientSecret: boolean`
   - Check if current secret hash matches the hash of `"dev-secret-CHANGE-IN-PROD"`
   - Check if current secret length is below minimum threshold (< 32 characters)
   - Return `true` if either condition is met

3. **Angular UI Warning**:
   - Poll `/api/config` endpoint
   - Display prominent warning banner if `insecureClientSecret == true`
   - Warning message: "⚠️ SECURITY WARNING: The client secret is using the default or insecure value. Please set a secure ABSTRAUTH_CLIENT_SECRET environment variable (minimum 32 characters) and restart the application."
   - Banner should be dismissible but reappear on page reload
   - Use alert styling (red/warning colors)

### Implementation Details

**BootstrapService.java** (new class):
```java
@ApplicationScoped
public class BootstrapService {
    private static final Logger LOG = Logger.getLogger(BootstrapService.class);
    private static final int MIN_SECRET_LENGTH = 32;
    
    @Inject
    EntityManager em;
    
    @ConfigProperty(name = "quarkus.oidc.bff.credentials.secret")
    String clientSecret;
    
    void onStart(@Observes StartupEvent ev) {
        syncClientSecretHash();
    }
    
    @Transactional
    void syncClientSecretHash() {
        OAuthClient client = em.find(OAuthClient.class, "abstratium-abstrauth");
        if (client == null) {
            LOG.error("Default client 'abstratium-abstrauth' not found!");
            return;
        }
        
        // Validate secret length
        if (clientSecret.length() < MIN_SECRET_LENGTH) {
            LOG.warn("Client secret is too short (< " + MIN_SECRET_LENGTH + " chars). Please use a stronger secret.");
        }
        
        // Check if using default secret
        if ("dev-secret-CHANGE-IN-PROD".equals(clientSecret)) {
            LOG.warn("Using default client secret! Please set ABSTRAUTH_CLIENT_SECRET environment variable.");
        }
        
        // Hash and update
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String newHash = encoder.encode(clientSecret);
        client.setClientSecretHash(newHash);
        em.merge(client);
        
        LOG.info("Client secret hash synchronized for 'abstratium-abstrauth'");
    }
}
```

**ConfigResource.java** (enhanced):
```java
@GET
@Produces(MediaType.APPLICATION_JSON)
public Response getConfig() {
    boolean signupAllowed = authorizationService.isSignupAllowed();
    boolean allowNativeSignin = authorizationService.isNativeSigninAllowed();
    boolean insecureClientSecret = isClientSecretInsecure();
    
    return Response.ok(new ConfigResponse(
        signupAllowed, 
        allowNativeSignin, 
        sessionTimeoutSeconds,
        insecureClientSecret
    )).build();
}

private boolean isClientSecretInsecure() {
    // Check length
    if (clientSecret.length() < 32) {
        return true;
    }
    
    // Check if using default
    OAuthClient client = em.find(OAuthClient.class, "abstratium-abstrauth");
    if (client == null || client.getClientSecretHash() == null) {
        return true;
    }
    
    // Check if hash matches default secret
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    return encoder.matches("dev-secret-CHANGE-IN-PROD", client.getClientSecretHash());
}
```

**Angular Component** (app.component.ts or dedicated warning component):
```typescript
export class AppComponent implements OnInit {
  showSecurityWarning = false;
  
  ngOnInit() {
    this.configService.getConfig().subscribe(config => {
      this.showSecurityWarning = config.insecureClientSecret;
    });
  }
}
```

**Angular Template**:
```html
<div class="alert alert-danger security-warning" *ngIf="showSecurityWarning">
  <strong>⚠️ SECURITY WARNING</strong>
  <p>
    The OAuth client secret is using the default or an insecure value.
    Please set a secure <code>ABSTRAUTH_CLIENT_SECRET</code> environment variable 
    (minimum 32 characters) and restart the application.
  </p>
  <button (click)="showSecurityWarning = false">Dismiss</button>
</div>
```

### Advantages

✅ **Zero-config for development**: Works out of the box with default secret  
✅ **Clear security guidance**: UI prominently warns about insecure configuration  
✅ **No restart loop**: Application starts successfully even with default secret  
✅ **Automatic sync**: Secret hash always matches environment variable  
✅ **Simple deployment**: Just set env var and restart, no manual database updates  
✅ **Idempotent**: Safe to restart multiple times  
✅ **Admin visibility**: Warning appears immediately after login  

### Security Considerations

1. **Minimum Length**: 32 characters enforced (256 bits of entropy if random)
2. **Default Detection**: Explicitly checks for `dev-secret-CHANGE-IN-PROD`
3. **Persistent Warning**: UI warning reappears until fixed
4. **Audit Logging**: Log warnings on startup for monitoring
5. **Documentation**: Clear instructions in deployment guide

### What Can Go Wrong If Client Secret Is Compromised?

**CRITICAL**: If an attacker gains access to the `ABSTRAUTH_CLIENT_SECRET` value, they can:

#### ❌ What They CANNOT Do:
- **Cannot sign in as any user**: The client secret is NOT a user password
- **Cannot directly access user accounts**: Authentication still requires valid user credentials
- **Cannot bypass the authorization flow**: Users must still approve access

#### ⚠️ What They CAN Do:

1. **Impersonate the Angular UI Application**:
   - An attacker can create a malicious application that pretends to be the legitimate Angular UI
   - They can exchange authorization codes for access tokens on behalf of the `abstratium-abstrauth` client
   - This is possible because the client secret authenticates the **application**, not the user

2. **Phishing Attack Scenario**:
   - Attacker creates a fake login page that looks like your application
   - User enters credentials and approves access (thinking it's legitimate)
   - Attacker's application uses the stolen client secret to exchange the authorization code for tokens
   - Attacker now has valid access tokens for that user's account
   - **Result**: Attacker gains access to user data and can perform actions as that user

3. **Token Exchange Without User Interaction** (if authorization code is intercepted):
   - If an attacker intercepts an authorization code (e.g., via network sniffing, redirect URI manipulation)
   - They can use the client secret to exchange it for access tokens
   - This bypasses the intended application and gives the attacker the user's tokens

4. **Replay Attacks**:
   - With the client secret, an attacker can repeatedly exchange valid authorization codes
   - This could be used to maintain persistent access if codes are somehow obtained

#### 🛡️ Why This Matters:

The `abstratium-abstrauth` client has special privileges:
- It's the **administrative interface** for the OAuth server
- Users with admin roles can manage all accounts, clients, and roles
- Compromising this client secret could lead to **full system compromise** if combined with a successful phishing attack

#### 🔒 Protection Measures:

1. **Use a Strong, Unique Secret**: Minimum 32 characters, cryptographically random
2. **Rotate Regularly**: Change the secret periodically (requires application restart)
3. **Secure Storage**: Never commit to version control, use secrets management
4. **Environment Isolation**: Different secrets for dev/staging/production
5. **Monitor for Abuse**: Watch for unusual token exchange patterns
6. **HTTPS Only**: Always use TLS to prevent secret interception
7. **Restrict Access**: Limit who can view environment variables in production

#### 📊 Risk Assessment:

- **Likelihood**: Low (if secret is properly secured)
- **Impact**: **CRITICAL** (potential full system compromise via phishing + admin access)
- **Mitigation**: Strong secret + HTTPS + user awareness + monitoring

### Migration Path

Existing deployments using default secret:
1. See warning in UI immediately after upgrade
2. Generate secure secret: `openssl rand -base64 32`
3. Set `ABSTRAUTH_CLIENT_SECRET=<generated-secret>`
4. Restart application
5. Warning disappears

### Future Enhancements

- Add admin endpoint to regenerate secret (returns new value, updates hash)
- Add secret rotation with grace period
- Add metrics/monitoring for insecure deployments
- Add startup failure option for production mode

---

## Why This Approach

This solution was chosen because it:
- Maintains backward compatibility
- Provides clear user guidance through the UI
- Requires minimal code changes
- Works for all deployment scenarios
- Balances security with usability
- Follows the principle of "secure by default, but functional out of the box"

The key insight is that **the UI is the best place to communicate security warnings** to administrators, rather than failing silently or requiring complex bootstrap procedures.
