package com.rj.fyers;

import com.rj.config.ConfigManager;
import com.tts.in.model.FyersClass;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

public class TokenGenerator {

    /**
     * Generates the access token using the provided auth code, and saves both
     * the access token and refresh token into local text files.
     *
     * @param clientId    the authentication code obtained from the browser
     * @param appHashID   the application hash ID
     * @param redirectUTL the redirect URL
     */
    public String generateToken(String clientId, String appHashID, String redirectUTL) {

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientId;
        fyersClass.GenerateCode(redirectUTL);

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the auth code from the browser:");
        String authCode = scanner.nextLine();

        JSONObject jsonObject = fyersClass.GenerateToken(authCode, appHashID);
        String accessToken = jsonObject.getString("access_token");
        String refreshToken = jsonObject.getString("refresh_token");
        scanner.close();

        ConfigManager config = ConfigManager.getInstance();
        config.updateEnvProperty("ACCESS_TOKEN", accessToken);  // Update ACCESS_TOKEN in .env so it persists across restarts (call once per day)
        config.updateEnvProperty("REFRESH_TOKEN", refreshToken);
        return accessToken;
    }

    /**
     * Generates a new access token using a valid refresh token.
     *
     * @param appIdHash    the application hash ID
     * @param refreshToken the valid refresh token
     * @param pin          the user's pin
     * @return the new access token, or null if the request failed
     */
    public String generateTokenFromRefreshToken(String appIdHash, String refreshToken, String pin) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("grant_type", "refresh_token");
            payload.put("appIdHash", appIdHash);
            payload.put("refresh_token", refreshToken);
            payload.put("pin", pin);

            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api-t1.fyers.in/api/v3/validate-refresh-token"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();

                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                if ("ok".equals(jsonResponse.optString("s"))) {
                    String accessToken = jsonResponse.getString("access_token");
                    ConfigManager.getInstance().updateEnvProperty("ACCESS_TOKEN", accessToken);
                    return accessToken;
                } else {
                    System.err.println("API error: " + jsonResponse.optString("message"));
                }
            } else {
                System.err.println("HTTP error: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
