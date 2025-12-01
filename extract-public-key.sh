#!/bin/bash
# Script to extract public key from private key for JWT verification
# Usage: ./extract-public-key.sh

# THIS KEY IS ONLY USED FOR TESTING!!!
# The private key (base64 encoded PKCS#8 format)
PRIVATE_KEY="MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDBvM+riIoEaxItLtOU0or3kQ3P9Om7pN4NmM32ONMJxHSn6n+GpineEGeCdyFcE5OkRF0a6BrDANXDUfhGQccnN6u6BalIebF0pVBpyra+i9Wcpbb1dixZgm6cgROm1ZDqSfIGnx48PsMqHM/EE6LLHeiB8V/bdmd01eiRg/LAp8q7BZJGBlDZUNmedP/bkcU8szBT8X6ZD4btiITDZZKxLWtgZsEJmF0tt90gDSBy5pU/ewwUrTMaCTj2eQgQ5AfefMZ6tJT6phYBEFyWPTEkkS+ulJct0xt3rPrekcseeAdM61FdtaOdSr+TvWbVRp7s5qSobT9qaCM5+4bnPv9bAgMBAAECggEAAJfcEuxiGngGyuNKe+Qrz2zpm+oQunsGFbM9aN7tASn8KXTLBdXbFEunOtEJOxzxkMkyInOffAVeojB4ECRXaxlSifPxJsBTTht2JDzIqSCzJhL5J8Xq24NOD2XzHMmpSJkICAPTYIqDUnewHdY+41xTapfF8V1qx613/tS77jcT8Qar556YTccJu0qgwNlrapjQYRvV5wT2uV4JqYaN1vC3tNXlIIsCGNR2CN9S/s6jp/gOE+eqfCbiuON1jCbXbN0U1TL4WKj6OYsOiA27hGCJ6n17gMBou+t8T8MccuceV9Lb8ecXB6h/oUok9gqZ57om8dGVoP4hZaOPsSWXwQKBgQDwRMCmunAe9d64yoUQYqmfHKtW584ltIr4do0hpaOsGXTjmrueG10BhFXPlDYoU1BWRezTQXVxWcPoLeIFFA511H22nRQ4g/dvBibCUr8B33jpGfbUq1baMrp2GN2KYdVbChEtrWPGNZCtXeux9sLuoDZVXbakdr4frAEDna+Q0QKBgQDObCG2cW8rdbh+0BOZY2x82titS4uE4Nx8LpF2Yl8zyFa/n+eRyXzhZXtQvEka04IX7SqncOMPYPen0pCAgd3DvoGTVcxRB0d2sCJ9oY++A4kEoUprddcQXwhWGqPTATosVbr3V2XwAx58yvG0odZBUN466shR7FEQmzbmviP4awKBgQDCx2nKgC/u2XHSKtPOob1SsQIx9L/JD2Dt5eWp1kcmeIirD0Bz/0jZtvd9zXBOJqRlHFDOPi3AU34fFjs51LWYTkgPp63B1zHa/oijVkNkeE7j4dmZNMG3KBLDNIs86Oz23eVpOzw8biY4dYBiiGIk4xrI/6zWDTE6Kc20qbuvUQKBgGYoWZ7jELOfdQk9jRWSgPRhkm5hPtEqP7Qtj8vY72i/Mz9usboSz3z1LkxMgpmGJ5ITy9JGKflIcghaSy1uGARx2crC4XUQdyukC83FEVBmi38BG8WG8kKl5YhHcuBQcSvT2c3jMQ3RXVtBTNGqblCw5uqdmzoADDZ9unQDkeW1AoGBALy9D8nFqZQB9i4fPeve9kTLd4wgyB8KrpTGtKnIcDOqjQcA2DA+mG/vzuAxP2ie4/QnCPAxzHtmLTKMAjzxNHgB+6zGYT+zBOgFdWqfUHz14DisHoSICkwaKluTBeVZYakQb0g57TvtfagvNf9ADuiCorbYot7yxoG+IRCDCauw"

# Write private key to temp file
echo "-----BEGIN PRIVATE KEY-----" > /tmp/private.pem
echo "$PRIVATE_KEY" | fold -w 64 >> /tmp/private.pem
echo "-----END PRIVATE KEY-----" >> /tmp/private.pem

# Extract public key
openssl rsa -in /tmp/private.pem -pubout -out /tmp/public.pem 2>/dev/null

# Display public key in PEM format
echo "Public Key (PEM format):"
cat /tmp/public.pem
echo ""

# Display public key without headers/newlines (for application.properties)
echo "Public Key (base64, no headers - for application.properties):"
grep -v '^-----' /tmp/public.pem | tr -d '\n'
echo ""
echo ""

# Clean up
rm -f /tmp/private.pem /tmp/public.pem

echo "Copy the base64 string above and use it for mp.jwt.verify.publickey in application.properties"
