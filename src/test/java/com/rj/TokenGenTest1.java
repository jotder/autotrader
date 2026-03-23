import fyers.TokenGenerator;

void main() {
    String appHashID = "40d7592d23f36fa436fc72d60dc6d690d8055feadc8d1f87a28c91fc4e27e6a8";
    String redirectUTL = "https://www.google.com";
    String clientID = "5UKRW6ONOM-100";

    TokenGenerator tokenGenerator = new TokenGenerator();
    String token = tokenGenerator.generateToken(clientID, appHashID, redirectUTL);
    System.out.println(token);
}

