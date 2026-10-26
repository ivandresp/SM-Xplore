# Test-only RSA key pair

These keys are used **only** by the automated tests to sign and verify RS256 tokens. They are
not production secrets and must never be used outside the test suite.

Production and local development must configure `JWK_SET_URI` (ms-01) or provide a real public
key through `JWT_PUBLIC_KEY_LOCATION`.
