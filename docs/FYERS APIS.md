FYERS APIS
Fyers API is a set of REST-like APIs that provide integration with our in-house trading platform with which you can build your own customized trading applications.

# Introduction
Fyers API is a set of REST-like APIs that provide integration with our in-house trading platform with which you can build your own customized trading applications. You can place fresh single or multiple orders, modify and cancel existing orders in real-time. You can also get account related information such as orderbook, trade book, net positions, holdings, and funds.
We have ensured maximum security for our APIs which prevent unauthorised transactions. All API requests and received only over HTTPS protocol.

# Libraries and SDKs
Certainly! To make it even easier for you to use the Fyers API in different programming languages, we have provided dedicated libraries/packages that handle the API calls for you. These libraries/packages abstract away the complexities of raw HTTP calls, allowing you to focus on integrating the API seamlessly into your applications. Below are the links to the libraries/packages.
We have a dedicated community to discuss, share and raise feature requests on FYERS API. Our goal is to empower the AlgoTrading community in India with the most robust and easy to integrate APIs.

# Request & Response Structure
Everything about Request & Response Structure
Authorization Headers
Once the authentication is completed, you will receive an access_token. For all of the following requests, you will be required to send a combination of appId and access_token (api_id:access_token) in the HTTP Authorization header.

## Request samples
```shell
curl -H “Authorization: api_id:access_token”
curl -H “Authorization: aaa-99:bbb”
```

## Success
Response Attributes

## Failure
The error response attributes will contain the following
Response Attribute

## HTTP Status Codes
The status codes contain the following

## Common API Error Codes
The status codes contain the following

## Rate Limits
Note: Due to SEBI’s retail algo trading regulations, changes to its usage may take effect on April 1, 2026. Please refer to the link for further details.

## Permission Templates
You can provide different app permissions for each application at the time of creation.

## User blocking
The user will be blocked for the rest of the day if the per minute rate limit is exceeded more than 3 times in the day.

# App Creation
Note: Due to SEBI’s retail algo trading regulations, changes to its usage may take effect on April 1, 2026. Please refer to the link for further details.

## Individual Apps
These are apps which are created for your own personal usage. These apps can be used only by the creator of the app and no other client can login and make use of this particular app.
To create an app, you need to follow the following steps:-
Login to API Dashboard
Click on Create App
Provide the following details
App Icon
App Name
Redirect URL
Description (Optional)
App Permissions - Refer Permissions Template

## Third Party Apps
These apps are used by platform providers which would allow end users to login to the app and make use of the functionality. These apps are created by third party application providers to enable FYERS clients to use their applications.
To create a common app, you can get in touch with us at api-support@fyers.in.
Redirect URI
The user will be redirected to the redirect uri after successfully logging in using the FYERS credentials. The redirect uri should be in your control as the auth token is sensitive information.
Authentication & Login Flow - User Apps
Authentication Steps
Note: Due to SEBI’s retail algo trading regulations, changes to its usage may take effect on April 1, 2026. Please refer to the link for further details.
The login flow is as follows:
Navigate to the Login API endpoint
After successful login, user is redirected to the redirect uri with the auth_code
POST the auth_code and appIdHash (SHA-256 of api_id + app_secret) to Validate Authcode API endpoint
Obtain the access_token use that for all the subsequent requests

## Request Parameters for Step 1

### Request Attributes

### Response Attributes

### Request samples

```java
package com.example;
import com.tts.in.model.FyersClass;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String redirectURI = "https://trade.fyers.in/api-login/redirect-uri/index.html";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        App app = new App();
        app.GetGenerateCode(redirectURI, fyersClass);
    }

    public void GetGenerateCode(String redirectURI, FyersClass fyersClass) {
        fyersClass.GenerateCode(redirectURI);
    }
}
```

### Request Parameters for Step 2
Request Attributes

### Response Attributes

### Request samples

```java
package com.example;
import org.json.JSONObject;
import com.tts.in.model.FyersClass;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String code = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";
        String appHashId = "WWWWWWWWWW";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        App app = new App();
        app.GetGenerateToken(code, appHashId, fyersClass);
    }

    public void GetGenerateToken(String code, String appHashId, FyersClass fyersClass) {
        JSONObject jsonObject = fyersClass.GenerateToken(code, appHashId);
        System.out.println(jsonObject);
    }
}
```

### Sample Success Response


```json
{
  "access_token": "XXXXXXXXXXXXXXXXXXXXXXXXXXX",
  "refresh_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhcGkuZnllcnMuaW4iLCJpYXQiOjE3Mjc...",
  "RESPONSE_MESSAGE": "SUCCESS"
}
```

Refresh Token
When we validate the auth code to generate the access token, a refresh token is also sent in the response.

The refresh token has a validity of 15 days. A new access token can be generated using the refresh token as long as the refresh token is valid.

The following parameters must be passed in the body for the POST request

### Request samples

```shell
curl --location --request POST 'https://api-t1.fyers.in/api/v3/validate-refresh-token' \
--header 'Content-Type: application/json' \
--data-raw '{
  "grant_type": "refresh_token",
  "appIdHash": "c3efb1075ef2332b3a4ec7d44b0f05c1********************",
  "refresh_token": "eyJ0eXAiOiJKV1***.eyJpc3MiOiJhcGkuZn***.5_Qpnd1nQXBw1T_wNJNFF***",
  "pin": "****"
}'
```

### Sample Success Response

```json
{
    "s": "ok",
    "code": 200,
    "message": "",
    "access_token": "eyJ0eXAiOiJK***.eyJpc3MiOiJhcGkuZnllcnM***.IzcuRxg4tnXiULCx3***"
}
```

### Best Practices
These are the recommended best practises that you should follow while using the APIs
Never share your app_secret with anyone
Never share your access_token with anyone
Do not provide trading permissions unless you want to use the app to place orders
Provide a redirect_uri which is in your control rather than a public endpoint such as google.com
You should send a random value in the state parameter and verify whether the same value has been returned to you
Authentication & Login Flow - Third Party Apps
OAuth2 is authentication flow for Third Party Apps
OAuth2 - Auth Flow
This is a simple OAuth 2 Authentication Flows.
This is recommended for applications which have a backend server which can authenticate the second step
This is not recommended for Single Page Applications (SPA)
Diagram
Request Parameters for Step 1
You need to navigate the user to the FYERS login url with the correct get parameters

### Request Attributes

### Response Attributes

## Request Parameters for Step 2

### Request Attributes

### Response Attributes

### Request samples

```shell
curl -H "Content-Type: application/json" -X POST -d '{
         "grant_type":"authorization_code","appIdHash":"d8f152a3c45...","code":"eyJ0eXAiOQ...iJIUzI1NiJ9.eyJpJh..."
}' https://api-t1.fyers.in/api/v3/validate-authcode
```


### Sample Success Response

```json
 {
   "s": "ok",
   "code": 200,
   "message": "",
   "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJhcGkuZnllcnMuaW4iLCJpYXQiOOTExNjAsImV4cCI6M..."
 }
```

## Best Practices
These are the recommended best practises that you should follow while using the APIs
Never share your app_secret with anyone
Never share your access_token with anyone
Do not provide trading permissions unless you want to use the app to place orders
Provide a redirect_uri which is in your control rather than a public endpoint such as google.com
You should send a random value in the state parameter and verify whether the same value has been returned to you
Do not store the app_secret in the front end. It should be securely kept without exposing it to third parties.

# Sample Code
Postman Collection
We have created an extensive Postman collection which will make it easier for implementation. Kindly import the postman_collection & postman_environment_variables
Download and import the postman collection from here
Download and import the postman environment variables from here
You can check the sample Script to get started with from here
We have provided dummy data in the environment variables. Kindly update the correct values after you import it.

# User
Arguments in FyersModel Class

## Profile
This allows you to fetch basic details of the client.
Response Attributes
Note: As per our privacy policy and in line with compliance requirements, PII (Personally Identifiable Information) information will be masked to safeguard customer information. If you notice any issues, please report to us at api-support@fyers.in for remedial action as may be necessary.

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetProfile(fyersClass);
    }

    public void GetProfile(FyersClass fyersClass) {
        Tuple<JSONObject, JSONObject> ProfileResponseTuple = fyersClass.GetProfile();

        if (ProfileResponseTuple.Item2() == null) {
            System.out.println("Profile: " + ProfileResponseTuple.Item1()); 
        } else {
            System.out.println("Profile Error: " + ProfileResponseTuple.Item2());
        }
    }
}
```

### Sample Response

```json
"Profile":{
            "email_id":"txxxxxxxxxxx2@gmail.com",
            "image":null,
            "totp":true,
            "fy_id":"YK04391",
            "pwd_change_date":null,
            "name":"KUMAR KISHORE KUMAR",
            "pin_change_date":"16-08-2024 10:58:33",
            "pwd_to_expire":90,
            "display_name":null,
            "PAN": "FYxxxxxx0S",
            "mobile_number": "63xxxxxx08",
            "ddpi_enabled": false,
            "mtf_enabled": false
          }    
```

## Funds
Shows the balance available for the user for capital as well as the commodity market.
Response Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetFunds(fyersClass);
    }
public void GetFunds(FyersClass fyersClass) {
        Tuple<JSONObject, JSONObject> ResponseTuple = fyersClass.GetFunds();
        if (ResponseTuple.Item2() == null) {
            System.out.println("Fund: " + ResponseTuple.Item1()); 
        } else {
            System.out.println("Fund Error: " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Response
```json
"Fund":{
    "code":200,
    "s":"ok",
    "fund_limit":[
        {
          "commodityAmount":0,
          "equityAmount":45.92,
          "id":1,
          "title":"Total Balance"
        },
        {
          "commodityAmount":0,
          "equityAmount":20.774790038627003,
          "id":2,
          "title":"Utilized Amount"
        },
        {
          "commodityAmount":0,
          "equityAmount":36.045209961373,
          "id":3,
          "title":"Clear Balance"
        },
        {
          "commodityAmount":0,
          "equityAmount":0,
          "id":4,
          "title":"Realized Profit and Loss"
},
        {
          "commodityAmount":0,
          "equityAmount":0,
          "id":5,
          "title":"Collaterals"
        },
        {
          "commodityAmount":0,
          "equityAmount":0,
          "id":6,
          "title":"Fund Transfer"
        },
        {
          "commodityAmount":0,
          "equityAmount":10.87,
          "id":7,
          "title":"Receivables"
        },
        {
          "commodityAmount":0,
          "equityAmount":0,
          "id":8,
          "title":"Adhoc Limit"
        },
        {
          "commodityAmount":0,
          "equityAmount":56.82,
          "id":9,
          "title":"Limit at start of the day"
        },
        {
          "commodityAmount":0,
          "equityAmount":36.045209961373,
          "id":10,
          "title":"Available Balance"
        }
    ],
    "message":""
  }
```

## Holdings
Fetches the equity and mutual fund holdings which the user has in this demat account. This will include T1 and demat holdings.
Request Attributes - For each holding

### Response Attributes - Overall holdings

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetHoldings(fyersClass);
    }

    public void GetHoldings(FyersClass fyersClass) {
        Tuple<JSONObject, JSONObject> holdingTuple = fyersClass.GetHoldings();
        if (holdingTuple.Item2() == null) {
            System.out.println("Holdings: " + holdingTuple.Item1()); 
    } else {
            System.out.println("Holdings Error: " + holdingTuple.Item2());
        }
    }
}

```


### Sample success Response

```json
"Holdings":{
  "code":200,
  "s":"ok",
  "overall":{
      "total_pl":-0.18000000000000016,
      "count_total":1,
      "total_investment":11.97,
      "total_current_value":11.790000000000001,
      "pnl_perc":-1.5038
},
  "holdings":[
      {
        "remainingQuantity":9,
        "symbol":"BSE:BIOGEN-XT",
        "quantity":9,
        "costPrice":1.33,
        "qty_t1":0,
        "ltp":1.31,
        "fyToken":"1210000000531752",
        "marketVal":11.790000000000001,
        "remainingPledgeQuantity":9,
        "collateralQuantity":0,
        "holdingType":"HLD",
        "segment":10,
        "exchange":12,
        "id":0,
        "pl":-0.18000000000000016,
        "isin":"INE703D01023"
      }
    ],
  "message":""
}
```

## Logout
This invalidates the access token, revoking it only for the specific app without affecting other active apps or web and mobile sessions.
Request samples

### package com.example;

```java
import org.json.JSONObject;
import com.tts.in.model.FyersClass;
public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiO*******************************";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetLogoutValidation(fyersClass);
    }

    public void GetLogoutValidation(FyersClass fyersClass) {
        JSONObject jsonObject = fyersClass.LogoutValidation();
        System.out.println(jsonObject);
    }
}
```

### Sample Response

```json
{
  "s": "ok",
  "code": 200,
  "message": "you are successfully logged out",
}
```

# Transaction Info

## Trades
Fetches all the trades for the current day across all platforms and exchanges in the current trading day.

### Response attributes - For each trade

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetTradeBook(fyersClass);
    }

    public void GetTradeBook(FyersClass trades) {
        Tuple<JSONObject, JSONObject> tradeTuple = trades.GetTradeBook();
        if (tradeTuple.Item2() == null) {
            System.out.println("TradeBook:" + tradeTuple.Item1());
    } else {
            System.out.println("TradeBook Error: " + tradeTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"TradeBook":{
  "code":200,
  "s":"ok",
  "tradeBook":[
      {
        "orderDateTime":"27-Sep-2024 10:37:47",
        "tradeNumber":"24092700126786-800111218",
        "symbol":"NSE:IDEA-EQ",
        "orderType":2,
        "side":1,
        "clientId":"YK04391",
        "orderNumber":"24092700126786",
        "tradeValue":10.87,
        "fyToken":"101000000014366",
        "tradedQty":1,
        "exchangeOrderNo":"1400000000262508",
        "segment":10,
        "exchange":10,
        "row":1727413667,
        "tradePrice":10.87,
        "orderTag":"1:testtest",
        "productType":"CNC"
      }
    ],
  "message":""
}
```

## Trades Filter By Order tag
You can query for a particular orderTag by passing the orderTag in the get parameters.

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetTradeByTag(fyersClass);
    }

    public void GetTradeByTag(FyersClass trades) {
        String tag = "2:Untagged";
        Tuple<JSONObject, JSONObject> tradeTuple = trades.GetTradeByTag(tag);
        if (tradeTuple.Item2() == null) {
            System.out.println("TradeBook By Tag:" + tradeTuple.Item1()); 
        } else {
            System.out.println("TradeBook Error: " + tradeTuple.Item2());
        }
    }

}
```

### Sample Success Response

```json
"TradeBook By Tag":{
  "code":200,
  "s":"ok",
  "tradeBook":[
      {
        "orderDateTime":"27-Sep-2024 10:33:09",
        "tradeNumber":"24092700122266-603464077",
        "symbol":"NSE:UCOBANK-EQ",
        "orderType":2,
        "side":1,
        "clientId":"YK04391",
        "orderNumber":"24092700122266",
        "tradeValue":49.3,
        "fyToken":"101000000011223",
        "tradedQty":1,
        "exchangeOrderNo":"1300000017598717",
        "segment":10,
        "exchange":10,
        "row":1727413389,
        "tradePrice":49.3,
        "orderTag":"2:Untagged",
        "productType":"INTRADAY"
      }
    ],
  "message":""
}   
```

## Orders
Fetches all the orders placed by the user across all platforms and exchanges in the current trading day.
Response attributes - For each order

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetOrders(fyersClass);
    }

    public void GetOrders(FyersClass order) {
        Tuple<JSONObject, JSONObject> orderList = order.GetAllOrders();
        if (orderList.Item2() == null) {
            System.out.println("Orders :" + orderList.Item1());
        } else {
            System.out.println("Orders Error:" + orderList.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Orders":{
  "code":200,
  "s":"ok",
  "orderBook":[
      {
        "remainingQuantity":0,
        "symbol":"NSE:UCOBANK-EQ",
        "lp":49.31,
        "description":"UCO BANK",
        "instrument":0,
        "source":"W",
        "type":2,
        "slNo":2,
        "offlineOrder":false,
        "segment":10,
        "ex_sym":"UCOBANK",
        "id":"24092700122266",
        "id_fyers": "c6697c04-d9ab-4a7c-a6f4-b0cc4ca698f6",
        "pan":"LBGPK9804E",
        "productType":"INTRADAY",
        "orderDateTime":"27-Sep-2024 10:33:09",
        "side":1,
        "clientId":"YK04391",
        "limitPrice":49.3,
        "ch":0.76,
        "tradedPrice":49.3,
        "disclosedQty":0,
        "chp":1.565396498455201,
        "message":"",
        "fyToken":"101000000011223",
        "stopPrice":0,
        "qty":1,
        "orderValidity":"DAY",
        "exchOrdId":"1300000017598717",
        "exchange":10,
        "filledQty":1,
        "orderTag":"2:Untagged",
        "orderNumStatus":"24092700122266:2",
        "status":2,
        "takeProfit":0,
        "stopLoss":0
      }
    ],
  "message":""
}
```

## Order Filter By Order Id
You can query for a particular order id by passing the id in the get parameters

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetOrders(fyersClass, "id");
    }

    public void GetOrders(FyersClass order, String ordersBy) {
        String orderId = "24092500183464";
        Tuple<JSONObject, JSONObject> orderList = order.GetOrderById(orderId);
        if (orderList.Item2() == null) {
            System.out.println("Order by Id:" + orderList.Item1());
} else {
            System.out.println("Order by Id Error:" + orderList.Item2());
        }
    }

}
```

### Sample Success Response
```json
"Order by Id":{

  "code":200,
  "s":"ok",
  "orderBook":[
      {
        "remainingQuantity":0,
        "symbol":"NSE:IDEA-EQ",
        "lp":10.92,
        "description":"VODAFONE IDEA LIMITED",
        "instrument":0,
        "source":"API",
        "type":2,
        "slNo":1,
        "offlineOrder":false,
        "segment":10,
        "ex_sym":"IDEA",
        "id":"24092700126786",
        "pan":"LBGPK9804E",
        "productType":"CNC",
        "orderDateTime":"27-Sep-2024 10:37:47",
        "side":1,
        "clientId":"YK04391",
        "limitPrice":10.87,
        "ch":0.54,
        "tradedPrice":10.87,
        "disclosedQty":0,
        "chp":5.202312138728324,
        "message":"",
        "fyToken":"101000000014366",
        "stopPrice":0,
        "qty":1,
        "orderValidity":"DAY",
        "exchOrdId":"1400000000262508",
        "exchange":10,
        "filledQty":1,
        "orderTag":"1:testtest",
        "orderNumStatus":"24092700126786:2",
        "status":2,
        "takeProfit":0,
        "stopLoss":0
      }
],
  "message":""
}
```

## Order Filter By Order tag
You can query for a particular orderTag by passing the orderTag in the get parameters

### Request samples
Curl Request Method

```shell
curl -H "Authorization:app_id:access_token"  https://api-t1.fyers.in/api/v3/orders??order_tag=1:Ordertag
```

#### Sample Success Response

```json
{
  "s": "ok",
  "code": 200,
  "message": "",
  "orderBook": [{
      "clientId": "X******",
      "id": "23030900015105",
      "exchOrdId": "1100000001089341",
      "qty": 1,
      "remainingQuantity": 0,
      "filledQty": 1,
      "discloseQty": 0,
      "limitPrice": 6.95,
      "stopPrice": 0,
      "tradedPrice": 6.95,
      "type": 1,
      "fyToken": "101000000014366",
      "exchange": 10,
      "segment": 10,
      "symbol": "NSE:IDEA-EQ",
      "instrument": 0,
      "message": "",
      "offlineOrder": false,
      "orderDateTime": "09-Mar-2023 09:34:38",
      "orderValidity": "DAY",
      "pan": "",
      "productType": "CNC",
      "side": -1,
      "status": 2,
      "source": "W",
      "ex_sym": "IDEA",
      "description": "VODAFONE IDEA LIMITED",
      "ch": -0.1,
      "chp": -1.44,
      "lp": 6.85,
      "slNo": 1,
      "dqQtyRem": 0,
      "orderNumStatus": "23030900015105:2",
      "disclosedQty": 0,
      "orderTag": "1:Ordertag",
      "takeProfit":0,
      "stopLoss":0
    }]
}
```

## Positions
Fetches the current open and closed positions for the current trading day. Note that previous trading day’s closed positions will not be shown here.

### Response attributes - For each position

### Response attributes - Overall Positions

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetPositions(fyersClass);
    }

    public void GetPositions(FyersClass positions) {
        Tuple<JSONObject, JSONObject> positionTuple = positions.GetPositions();
        if (positionTuple.Item2() == null) {
            System.out.println("Positions:" + positionTuple.Item1()); 
        } else {
            System.out.println("Positions Error: " + positionTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Positions":{
  "code":200,
  "s":"ok",
  "netPositions":[
      {
        "symbol":"NSE:UCOBANK-EQ",
        "rbiRefRate":1,
        "sellVal":0,
        "sellAvg":0,
        "cfBuyQty":0,
        "buyAvg":49.3,
        "netAvg":49.3,
        "slNo":0,
        "unrealized_profit":-0.05999999999999517,
        "segment":10,
        "buyVal":49.3,
        "id":"NSE:UCOBANK-EQ-INTRADAY",
        "productType":"INTRADAY",
        "side":1,
        "qtyMulti_com":1,
        "netQty":1,
        "crossCurrency":"",
        "dayBuyQty":1,
        "daySellQty":0,
        "ltp":49.24,
        "realized_profit":-0,
        "sellQty":0,
        "fyToken":"101000000011223",
        "cfSellQty":0,
        "buyQty":1,
        "qty":1,
        "exchange":10,
        "pl":-0.05999999999999517
        }],
      "overall":{
      "count_open":2,
      "count_total":2,
      "pl_realized":0,
      "pl_total":-0.09999999999999432,
      "pl_unrealized":-0.09999999999999432
},
  "message":""
}
```

# Reports

## Order History
Fetches all historical orders within the specified date range. If no range is specified, the system automatically defaults to the current financial year.

### Request attributes - For each order
Response attributes - For each order

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetOrderHistory(fyersClass, null);   // no query params
        // app.GetOrderHistory(fyersClass, "symbol=NSE:MAZDOCK-EQ&from_date=2025-10-25&to_date=2025-10-25");  // with query params
    }

    /** Order history with optional query params, e.g. "from_date=2024-01-01&to_date=2024-02-01". Pass null for no params. */
    public void GetOrderHistory(FyersClass orders, String queryParams) {
    Tuple<JSONObject, JSONObject> tradeTuple = queryParams == null? orders.GetOrderHistory(null)
            : orders.GetOrderHistory(queryParams);
        if (tradeTuple.Item2() == null) {
            System.out.println("Order History:" + tradeTuple.Item1());
        } else {
            System.out.println("Order History Error: " + tradeTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
{
    "s": "ok",
    "data": [
      {
        "symbol": "NSE:SBIN-EQ",
        "clientId": "XXXXXX",
        "id_fyers": "26020100012037",
        "exchOrdId": "603210324081559",
        "exchange": 11,
        "segment": 20,
        "instrument": 30,
        "description": "SBI",
        "trade_date_time": 1769920009000,
        "trade_date ": "01-02-2026 09:56:49",
        "transaction_type": "SELL",
        "product_type": "Overnight",
        "status": "Executed",
        "ordertype": "Limit",
        "qty": 1,
        "tradedqty": 1,
        "traded_price": 15013,
        "limit_price": 15013,
        "ord_source": "Mobile",
        "rejection_reason": "",
        "is_symbol_active": true
      }
    ],
    "message": "Order Book data fetched successfully",
    "code": 200
}
```

## Trade History
Fetches all trades for a specified date range. If no date range is provided, it defaults to the current financial year.

### Request attributes - For each order

### Response attributes - For each trade

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();app.GetTradeHistory(fyersClass, null);   // no query params
        // app.GetTradeHistory(fyersClass, "symbol=NSE:IDEA-EQ");  // with query params
    }

    /** Trade history with optional query params, e.g. "from_date=2024-01-01&to_date=2024-02-01". Pass null for no params. */
    public void GetTradeHistory(FyersClass trades, String queryParams) {
    Tuple<JSONObject, JSONObject> tradeTuple = queryParams == null? trades.GetTradeHistory(null)
            : trades.GetTradeHistory(queryParams);
        if (tradeTuple.Item2() == null) {
            System.out.println("Trade History:" + tradeTuple.Item1());
    } else {
            System.out.println("Trade History Error: " + tradeTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
{
  "s": "ok",
  "data": [
      {
        "symbol": "MCX:GOLDPETAL25JUNFUT",
        "clientId": "XXXXXX",
        "description": "GOLDPETAL25JUNFUT",
        "orderDateTime": "13-Jun-2025 20:05:38",
        "orderNumber": "25061300346219",
        "tradeNumber": "100388805",
        "exchangeOrderNo": "516410327756710",
        "side": 1,
        "exchange": 11,
        "segment": 20,
        "product_type": "Overnight",
        "traded_qty": 1,
        "trade_price": 10007,
        "trade_value": 10007,
        "is_symbol_active": false
      }
],
  "message": "Trade Book data fetched successfully"
}
```

# Order Placement Guide
Note: Due to SEBI’s retail algo trading regulations, changes to its usage may take effect on April 1, 2026. Please refer to the link for further details.
Order Placement Guide
The order placement process requires you to carefully input several parameters at the time of making the request. There are several validations which are required to be done before sending the request.

## Order Types
Limit Order
type: 1
A limit order allows you to buy or sell an asset at a specific price (limitPrice) or better. It will only be executed at the specified price or lower for a buy order, and at the specified price or higher for a sell order.

Sample input:
```json
{ "symbol": "NSE:SBIN-EQ", "qty": 100, "type": 1, "side": 1, "productType": "INTRADAY", "limitPrice": 100, "stopPrice": 0, "validity": "DAY", "stopLoss": 0, "takeProfit": 0, "offlineOrder": false, "disclosedQty": 0, "isSliceOrder" : false }
```
Market Order
type: 2
A market order allows you to buy or sell an asset at the current market price. The limitPrice and stopPrice should be set to 0, as it is not used in this order type.

Sample input:
```json
{ "symbol": "NSE:SBIN-EQ", "qty": 100, "type": 2, "side": 1, "productType": "INTRADAY", "limitPrice": 0, "stopPrice": 0, "validity": "DAY", "stopLoss": 0, "takeProfit": 0, "offlineOrder": false, "disclosedQty": 0, "isSliceOrder" : false }
```
Stop Order / SL-M
type: 3
A stop order is designed to limit losses on a position. It becomes a market order when the stopPrice is reached. The stopPrice is the trigger price at which the market order will be placed.
The stopPrice must not be higher than the Last Traded Price (ltp) for a sell order and not lower than the ltp for a buy order.

Sample input:
```json
{ "symbol": "NSE:SBIN-EQ", "qty": 100, "type": 3, "side": 1, "productType": "INTRADAY", "limitPrice": 0, "stopPrice": 100, "validity": "DAY", "stopLoss": 0, "takeProfit": 0, "offlineOrder": false, "disclosedQty": 0, "isSliceOrder" : false }
```
Stop Limit Order / SL-L
type: 4
A stop-limit order combines features of a stop order and a limit order. It triggers a limit order once the stopPrice is reached.
The stopPrice must not be higher than the Last Traded Price (ltp) for a sell order and not lower than the ltp for a buy order.
The stopPrice must be lower than the limitPrice for a buy order and higher than the limitPrice for a sell order.

Sample input:
```json
{ "symbol": "NSE:SBIN-EQ", "qty": 100, "type": 4, "side": 1, "productType": "INTRADAY", "limitPrice": 100, "stopPrice": 95, "validity": "DAY", "stopLoss": 0, "takeProfit": 0, "offlineOrder": false, "disclosedQty": 0, "isSliceOrder" : false }
```
Product Types
Intraday
Used to place orders which will be bought and sold the same day
Order type can be anything (Market, Limit, Stop, and Stop Limit)
CNC
Used to place orders only in stocks which will be carried forward
Order type can be anything (Market, Limit, Stop, and Stop Limit)
Margin
Used to place orders in derivative contracts which will be carried forward
Order type can be anything (Market, Limit, Stop, and Stop Limit)
Cover Order (CO)
stopLoss is a mandatory input
stopLoss price is given in points denominated. (Eg: SBIN LTP = 300. For the above example, for buy CO, if you want to put stop loss as 298 then you have to pass "stopLoss" value as 2 (difference between ltp and your desired stop loss) )
Note:
1. "stopLoss" value can be float but should be multiple of 0.0500.
2. Now let's say if you don't provide stopLoss value as multiple of 0.0500 then you can find this error message {'code': -50, 'message': 'StopLoss not a multiple of tick size 0.0500', 's': 'error'}
The order type can be either market or limit (If the market order, then the stop loss price should not be higher than the ltp in buy and lower than the ltp for sell. If the limit order, then the stop loss price should not be higher than the limit price in buy and lower than the limit for sell)
Validity should be “DAY.”
Disclosed quantity should be 0.
Bracket Order (BO)
stopLoss is a mandatory input
takeProfit is a mandatory input
The stopLoss and takeProfit are denominated in rupees above and below the trade price. (Eg: SBIN LTP = 300. So the user can give a target of Rs. 10 and stop loss or Rs. 5.)
Order type can be either market, limit, stop, or stop limit
Validity should be “DAY.”
Disclosed quantity should be 0
Margin Trading Facility (MTF)
This order type is for placing trades with additional margin.
MTF orders require activation.
Only stocks from the approved MTF list are allowed.
Supported order types: Market, Limit, Stop, and Stop Limit.
Price Validations
For all transaction requests (Order placement / modification), the price input should be in accordance with the exchange provided tick size for the particular symbol.
Tick size is applicable for all price inputs (Limit / stop / stopLoss / target)
Each symbol will have its own tick size
All price inputs have to be in multiples of the tick size
The tick size is available in the symbol master

## Quantity Validations
For all transaction requests (Order placement / modification), the quantity input should be in accordance with the exchange provided minimum lot size.

## Order Tag
For all Order placement requests where tags are passed they should meet following requirements.
Ordertag string should be Alphanumeric i.e. no space or special characters will be allowed.
Ordertag string should not exceed length of 30 characters and should have minimum length of 1 character.
Ordertag string cannot be your clientID or string Untagged.
Ordertagging is currently not supported for ProductType BO & CO. Orders with ordertag for CO/BO product Type will be rejeceted.

## Auto-Order Slice
Use the auto-slice feature to place large orders that exceed the exchange’s freeze-quantity limits.
Enable it by setting "isSliceOrder": true in the order request. When enabled, the system automatically splits the order into smaller quantities based on the applicable freeze limit. Each slice is submitted as a separate order with its own "Order ID".
Note:
Slicing is only allowed for NSE CM, NFO and BFO.
Maximum slices per request: 10.

### Sample input :
```json
{ "symbol": "NSE:BANKNIFTY25NOV58900PE", "qty": 3150, "type": 2, "side": 1, "productType": "MARGIN", "limitPrice": 0, "stopPrice": 0, "disclosedQty": 0, "validity": "DAY", "offlineOrder": false, "stopLoss": 0, "takeProfit": 0, "IsSliceOrder": true }
```

Response Output :

```json
{
  "code": 1101,
  "message": "Order Slice placed successfully",
  "data": [
    {
      "statusCode": 200,
      "body": {
        "code": 1101,
        "message": "Successfully placed order",
        "s": "ok",
        "id": "25120400000372"
},
      "statusDescription": "OK"
},
    {
      "statusCode": 200,
      "body": {
        "code": 1101,
        "message": "Successfully placed order",
        "s": "ok",
        "id": "25120400000373"
    },
      "statusDescription": "OK"
    },
    {
      "statusCode": 200,
      "body": {
        "code": 1101,
        "message": "Successfully placed order",
        "s": "ok",
        "id": "25120400000374"
    },
      "statusDescription": "OK"
    }
]
}
```

# Order Placement
Single Order
This allows the user to place an order to any exchange via Fyers

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.OrderValidity;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.TransactionType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.PlaceOrder(fyersClass);
    }

    public void PlaceOrder(FyersClass fyersClass) {
        PlaceOrderModel model = new PlaceOrderModel();
        model.Symbol = "NSE:IDEA-EQ";
        model.Qty = 1;
        model.OrderType = OrderType.MarketOrder.getDescription();
        model.Side = TransactionType.Buy.getValue();
        model.ProductType = ProductType.CNC;
        model.LimitPrice = 0;
        model.StopPrice = 0;
        model.OrderValidity = OrderValidity.DAY;
        model.DisclosedQty = 0;
        model.OffLineOrder = false;
        model.StopLoss = 0;
        model.TakeProfit = 0;
        model.OrderTag = "PlacingOrderWithTag2";

        Tuple<JSONObject, JSONObject> ResponseTuple = fyersClass.PlaceOrder(model);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Order ID: " + ResponseTuple.Item1()); 
        } else {
            System.out.println("Place order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response
```json
"Order ID":{

        "code":1101,
        "s":"ok",
        "id":"24092700227212",
        "message":"Successfully placed order"
      }
```

## Multi Order
You can place upto 10 orders simultaneously via the API.
While Placing Multi orders you need to pass an ARRAY containing the orders request attributes
Sample Request :
[{ "symbol":"MCX:SILVERM20NOVFUT", "qty":1, "type":1, "side":1, "productType":"INTRADAY", "limitPrice":61050, "stopPrice":0 , "disclosedQty":0, "validity":"DAY", "offlineOrder":false, "stopLoss":0, "takeProfit":0, "orderTag":"tag1" }, { "symbol":"MCX:SILVERM20NOVFUT", "qty":1, "type":2, "side":1, "productType":"INTRADAY", "limitPrice":61050, "stopPrice":0 , "disclosedQty":0, "validity":"DAY", "offlineOrder":false, "stopLoss":0, "takeProfit":0, "orderTag":"tag1" }]

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.OrderValidity;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.TransactionType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.PlaceMultipleOrder(fyersClass);
    }

    public void PlaceMultipleOrder(FyersClass order) {
        List<PlaceOrderModel> placeOrdersList = new ArrayList<>();

        PlaceOrderModel model = new PlaceOrderModel();
        model.Symbol = "NSE:PSB-EQ";
        model.Qty = 1;
        model.OrderType = OrderType.MarketOrder.getDescription();
        model.Side = TransactionType.Buy.getValue();
        model.ProductType = ProductType.INTRADAY;
        model.LimitPrice = 0;
        model.StopPrice = 0;
        model.OrderValidity = OrderValidity.DAY;
        model.DisclosedQty = 0;
        model.OffLineOrder = false;
        model.StopLoss = 0;
        model.TakeProfit = 0;
        model.OrderTag = "ManualOrderTag1";

        PlaceOrderModel orderModel2 = new PlaceOrderModel();
        orderModel2.Symbol = "NSE:IDEA-EQ";
        orderModel2.Qty = 1;
        orderModel2.OrderType = OrderType.MarketOrder.getDescription();
        orderModel2.Side = TransactionType.Buy.getValue();
        orderModel2.ProductType = ProductType.CNC;
        orderModel2.LimitPrice = 0;
        orderModel2.StopPrice = 0;
        orderModel2.OrderValidity = OrderValidity.DAY;
        orderModel2.DisclosedQty = 0;
        orderModel2.OffLineOrder = false;
        orderModel2.StopLoss = 0;
        orderModel2.TakeProfit = 0;
        orderModel2.OrderTag = "tag1";

        PlaceOrderModel orderModel3 = new PlaceOrderModel();
        orderModel3.Symbol = "NSE:UCOBANK-EQ";
        orderModel3.Qty = 1;
        orderModel3.OrderType = OrderType.MarketOrder.getDescription();
        orderModel3.Side = TransactionType.Sell.getValue();
        orderModel3.ProductType = ProductType.INTRADAY;
        orderModel3.LimitPrice = 0;
        orderModel3.StopPrice = 0;
        orderModel3.OrderValidity = OrderValidity.DAY;
        orderModel3.DisclosedQty = 0;
        orderModel3.OffLineOrder = false;
        orderModel3.StopLoss = 0;
        orderModel3.TakeProfit = 0;
        orderModel3.OrderTag = "tag2";

        placeOrdersList.add(model);
        placeOrdersList.add(orderModel2);
        placeOrdersList.add(orderModel3);

        Tuple<JSONObject, JSONObject> ResponseTuple = order.PlaceMultipleOrders(placeOrdersList);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders:" + ResponseTuple.Item1());
    } else {
            System.out.println("Place order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
 "Orders":{
  "code":200,
  "s":"ok",
  "data":[
      {
        "statusDescription":"OK",
        "body":{
            "code":1101,
            "s":"ok",
            "id":"24092700231369",
            "message":"Successfully placed order"
},
        "statusCode":200
},
      {
        "statusDescription":"OK",
        "body":{
            "code":1101,
            "s":"ok",
            "id":"24092700231371",
            "message":"Successfully placed order"
        },
        "statusCode":200
    },
      {
        "statusDescription":"OK",
        "body":{
            "code":1101,
            "s":"ok",
            "id":"24092700231373",
            "message":"Successfully placed order"
        },
        "statusCode":200
      }
],
  "message":""
}
```

## MultiLeg Order
This allows the user to place an MultiLeg order to NSE via Fyers.
Note: As per exchange mandate, while selecting symbols please check the "stream" key in the symbol master JSON file to ensure both legs belong to the same stream.

### Request attributes

### Leg attributes

### Response attributes

### Input Validations

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.model.Leg;
import com.tts.in.model.MultiLegModel;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.PlaceMultiLegOrder(fyersClass);
    }

    public void PlaceMultiLegOrder(FyersClass order) {
        List<MultiLegModel> placeOrdersList = new ArrayList<>();
        MultiLegModel model = new MultiLegModel();
        model.OrderTag = "tag1";
        model.ProductType = ProductType.MARGIN;
        model.OfflineOrder = false;
        model.OrderType = "3L";
        model.Validity = "IOC";
        model.addLeg("leg1", new Leg("NSE:SBIN24JUNFUT", 750, 1, 1, 800)); //(symbol,qty,side,type,limitPrice)
        model.addLeg("leg2", new Leg("NSE:SBIN24JULFUT", 750, 1, 1, 790));
        model.addLeg("leg3", new Leg("NSE:SBIN24JUN900CE", 750, 1, 1, 3));

        placeOrdersList.add(model);

        Tuple<JSONObject, JSONObject> ResponseTuple = order.PlaceMultiLegOrder(placeOrdersList);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders:" + ResponseTuple.Item1());
        } else {
            System.out.println("Place order Message :" + ResponseTuple.Item2());
        }
    }

}
```

### Sample Success Response
```json
Orders:{
s: 'ok',
code: 1101,
message: "Successfully placed order",
id: '52104097616'
          }
```

# GTT Orders
GTT (Good Till Trigger) is used to create orders with longer validity. It helps you set a Target or Stop-Loss for your open positions or holdings, and you can also use it to take new positions. The order remains active for up to one year.
GTT Single
This type is used to create a single GTT order.
It supports placing orders for CNC, Margin, and MTF.
It can be applied either as a Target or Stop Loss for your existing holdings/positions.
Alternatively, it can be used to create a GTT order for a new position/holding.

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.model.GTTLeg;
import com.tts.in.model.GTTModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.PlaceOrder(fyersClass);
    }

    public void PlaceGTTOrder(FyersClass order) {
      List<GTTModel> placeOrdersList = new ArrayList<>();
      GTTModel model = new GTTModel();
      model.Side = 1;
      model.Symbol = "NSE:IDFCFIRSTB-EQ";
      model.productType = "MTF";
      model.addGTTLeg("leg1", new GTTLeg(400,400,1));

      placeOrdersList.add(model);

      Tuple<JSONObject, JSONObject> ResponseTuple = order.PlaceGTTOrder(placeOrdersList);
      if (ResponseTuple.Item2() == null) {
          System.out.println("Orders:" + ResponseTuple.Item1());
    } else {
          System.out.println("Place order Message :" + ResponseTuple.Item2());
      }
    }
}
```
------------------------------------------------------------------------------------------------------------------------------------------

### Sample Success Response
------------------------------------------------------------------------------------------------------------------------------------------
```json
"Order ID":{

        "code":1101,
        "s":"ok",
        "id":"24092700227212",
        "message":"Successfully placed order"
      }
```

## GTT OCO
This type allows you to place both a Stop Loss and a Target order simultaneously. If one order is triggered, the other gets automatically canceled.
To avoid rejections:
For Leg1: The trigger price must always be greater than the LTP
For Leg 2: The trigger price must always be less than the LTP

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.model.GTTLeg;
import com.tts.in.model.GTTModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.PlaceOrder(fyersClass);
    }

    public void PlaceGTTOrder(FyersClass order) {
      List<GTTModel> placeOrdersList = new ArrayList<>();
      GTTModel model = new GTTModel();
      model.Side = 1;
      model.Symbol = "NSE:IDFCFIRSTB-EQ";
      model.productType = "MTF";
      model.addGTTLeg("leg1", new GTTLeg(400,400,1));
      model.addGTTLeg("leg2", new GTTLeg(50,50,1));

      placeOrdersList.add(model);

      Tuple<JSONObject, JSONObject> ResponseTuple = order.PlaceGTTOrder(placeOrdersList);
      if (ResponseTuple.Item2() == null) {
          System.out.println("Orders:" + ResponseTuple.Item1());
        } else {
          System.out.println("Place order Message :" + ResponseTuple.Item2());
      }
    }
}
```

### Sample Success Response
```json
"Order ID":{
        "code":1101,
        "s":"ok",
        "id":"24092700227212",
        "message":"Successfully placed order"
      }
```

## GTT Modify Order
This allows the user to modify pending GTT orders. Users can provide parameters which need to be modified. In case a particular parameter has not been provided, the original value will be considered.

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.model.GTTLeg;
import com.tts.in.model.GTTModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ModifySingleOrder(fyersClass);
    }

    public void ModifyGTTOrder(FyersClass order) {
        List<GTTModel> placeOrdersList = new ArrayList<>();
        GTTModel model = new GTTModel();
        model.Id = "25012300000741";
        model.addGTTLeg("leg1", new GTTLeg(1100,1100,5));
        model.addGTTLeg("leg2", new GTTLeg(67,67,5));

        placeOrdersList.add(model);

        Tuple<JSONObject, JSONObject> ResponseTuple = order.ModifyGTTOrder(placeOrdersList);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders:" + ResponseTuple.Item1());
        } else {
            System.out.println("Modify Order Message :" + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response
```json
"Order ID":{
  "code":1102,
  "s":"ok",
  "id":"24092700247449",
  "message":"Successfully modified order"
}
```

## GTT Cancel Order
You can cancel pending orders before they trigger.

### Request attributes - For each order

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.model.GTTLeg;
import com.tts.in.model.GTTModel;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.CancelSingleOrder(fyersClass);
    }

    public void CancelGTTOrder(FyersClass order) {
        String OrderId = "25012200002259";

        Tuple<JSONObject, JSONObject> ResponseTuple = order.CancelGTTOrder(OrderId);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders ID: " + ResponseTuple.Item1());
        } else {
            System.out.println("Cancel order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response
```json
"Orders ID":{
  "code":1103,
  "s":"ok",
  "id":"24092700267322",
  "message":"Successfully cancelled order"
}
```

## GTT Order Book
You can fetch all pending GTT Orders.

### Response attributes - For each order

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetOrders(fyersClass);
    }

    public void GetOrders(FyersClass order) {
        Tuple<JSONObject, JSONObject> orderList = order.GetAllOrders();
        if (orderList.Item2() == null) {
            System.out.println("Orders :" + orderList.Item1());
        } else {
            System.out.println("Orders Error:" + orderList.Item2());
        }
    }
}
```

### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "s": "ok",
  "message": "",
  "orderBook": [
    {
      "clientId": "X******",
      "exchange": 10,
      "fy_token": "10100000003045",
      "id_fyers": "c6697c04-d9ab-4a7c-a6f4-b0cc4ca698f6",
      "id": "25012400002074",
      "instrument": 0,
      "lot_size": 1,
      "multiplier": 0,
      "ord_status": 1,
      "precision": 2,
      "price_limit": 1020,
      "price2_limit": 620,
      "price_trigger": 1020,
      "price2_trigger": 620,
      "product_type": "CNC",
      "qty": 5,
      "qty2": 5,
      "report_type": "CANCELLED",
      "segment": 10,
      "symbol": "NSE:SBIN-EQ",
      "symbol_desc": "STATE BANK OF INDIA",
      "symbol_exch": "SBIN",
      "tick_size": 0.05,
      "tran_side": 1,
      "gtt_oco_ind": 2,
      "create_time": "24-Jan-2025 16:13:31",
      "create_time_epoch": 1737715411,
      "oms_msg": "GTT/OCO order cancelled successfully.",
      "ltp_ch": 0,
      "ltp_chp": 0,
      "ltp": 0
},
    {
      "clientId": "X******",
      "exchange": 10,
      "fy_token": "10100000003045",
      "id_fyers": "142849a0-d32b-44b5-9108-b7db5ee5e59b",
      "id": "25012400002099",
      "instrument": 0,
      "lot_size": 1,
      "multiplier": 0,
      "ord_status": 1,
      "precision": 2,
      "price_limit": 1000,
      "price2_limit": 600,
      "price_trigger": 1000,
      "price2_trigger": 600,
      "product_type": "MTF",
      "qty": 3,
      "qty2": 3,
      "report_type": "CANCELLED",
      "segment": 10,
      "symbol": "NSE:SBIN-EQ",
      "symbol_desc": "STATE BANK OF INDIA",
      "symbol_exch": "SBIN",
      "tick_size": 0.05,
      "tran_side": -1,
      "gtt_oco_ind": 2,
      "create_time": "24-Jan-2025 16:10:27",
      "create_time_epoch": 1737715227,
      "oms_msg": "GTT/OCO order cancelled successfully.",
      "ltp_ch": 0,
      "ltp_chp": 0,
      "ltp": 0
    }
]
}
```

# Smart Orders
Smart Orders are advanced orders designed to give traders more control and flexibility in executing their strategies.
Order Types:
Smart Limit
Smart Trail
Smart Step
Smart SIP
Features:
Smart Limit, Trail, and Step support CNC, MARGIN, MTF, and INTRADAY product types
Smart SIP is supported only for CNC product types
Price range protection (HPR / LPR) to restrict order execution outside defined limits
All market order types are placed with Market Protection Percentage (MPP) to control slippage
Pause and Resume functionality for order management
Maximum of 100 Smart Orders per day (Smart SIP orders have separate limits)
Smart Limit Order
Smart Limit Orders allow you to place limit orders that remain active until the specified end time. Once the end time is reached, the order can be converted to an MPP order or cancelled.
Note:
Supports order placement under CNC, MARGIN, MTF, and INTRADAY.
You can define a price range (HPR/LPR) to restrict execution within your specified limits.
Supports only selected NSE and BSE symbols.

### Request Attributes

### Validations & Error Handling

### Response Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "YOUR_ACCESS_TOKEN";
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.CreateSmartorderLimit(fyersClass);
    }

    public void CreateSmartorderLimit(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("symbol", "NSE:SBIN-EQ");
        requestBody.put("side", 1);
        requestBody.put("qty", 1);
        requestBody.put("productType", "CNC");
        requestBody.put("limitPrice", 1250);
        requestBody.put("stopPrice", 1200);
        requestBody.put("orderType", 1);
        requestBody.put("endTime", 1769162100);
        requestBody.put("hpr", 1300);
        requestBody.put("lpr", 700);
        requestBody.put("mpp", 1);
        requestBody.put("onExp", 2);

        Tuple<JSONObject, JSONObject> result = fyersClass.CreateSmartorderLimit(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Limit Created: " + result.Item1());
        } else {
            System.out.println("Create Smart Order Limit Error: " + result.Item2());
        }
    }
}
```

### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to place smart order",
  "s": "ok",
  "id": "1bf162b1-6406-407a-911a-5a3309e1aaba"
}
```

## Smart Trail (Trailing Stop Loss)
A Smart Trail Order is a trailing stop-loss that automatically adjusts the stop price as the market moves in your favour. The stop price trails the market by a specified jump price.
Note:
For sell orders, the stop price moves up as the price rises.
For buy orders, the stop price moves down as the price falls.
The order is triggered when the market price hits the trailing stop price.
You can optionally set a target price to book profits automatically.
Supports both Limit and Market (MPP) order types for execution.
Supports only selected NSE and BSE symbols.

### Request Attributes

### Validations & Error Messages

### Response Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().CreateSmartorderTrail(fyersClass);
    }

    public void CreateSmartorderTrail(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("symbol", "NSE:SBIN-EQ");
        requestBody.put("side", 1);
        requestBody.put("qty", 1);
        requestBody.put("productType", "CNC");
        requestBody.put("stopPrice", 740);
        requestBody.put("jump_diff", 5);
        requestBody.put("orderType", 2);
        requestBody.put("mpp", 1);

        Tuple<JSONObject, JSONObject> result = fyersClass.CreateSmartorderTrail(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Trail Created: " + result.Item1());
        } else {
            System.out.println("Create Smart Order Trail Error: " + result.Item2());
        }
    }
}
```
---------------------------------------------------------------------------------------------------------------------------------------------

### Sample Success Response
---------------------------------------------------------------------------------------------------------------------------------------------
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to place smart order",
  "s": "ok",
  "id": "2cf273c2-7517-518b-a22b-6b4420f2bcbc"
}
```

## Smart Step Order
Smart Step Order lets you average your position by placing orders at predefined price intervals, helping you build the position gradually across multiple price levels.
Note:
Starts with an initial quantity, followed by averaging based on defined step intervals.
Direction determines whether orders are placed as the price increases or decreases.
Orders are placed automatically at each step until the total quantity is fulfilled.
Supports both price-based and time-based step execution.
Supports only selected NSE and BSE symbols.

### Request Attributes

### Validations & Error Messages
Note: After the order starts executing, the following fields cannot be modified: initQty, limitPrice, orderType

### Response Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().CreateSmartorderStep(fyersClass);
    }

    public void CreateSmartorderStep(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("symbol", "NSE:SBIN-EQ");
        requestBody.put("side", 1);
        requestBody.put("qty", 10);
        requestBody.put("productType", "CNC");
        requestBody.put("initQty", 2);
        requestBody.put("avgqty", 2);
        requestBody.put("avgdiff", 5);
        requestBody.put("direction", 1);
        requestBody.put("limitPrice", 750);
        requestBody.put("orderType", 1);
        requestBody.put("startTime", 1769149800);
        requestBody.put("endTime", 1769162100);
        requestBody.put("hpr", 800);
        requestBody.put("lpr", 700);
        requestBody.put("mpp", 1);

        Tuple<JSONObject, JSONObject> result = fyersClass.CreateSmartorderStep(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Step Created: " + result.Item1());
        } else {
            System.out.println("Create Smart Order Step Error: " + result.Item2());
        }
    }
}
```

### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to place smart order",
  "s": "ok",
  "id": "1bf162b1-6406-407a-911a-5a3309e1aaba"
}
```

## Smart SIP (Systematic Investment Plan)
Smart SIP allows you to automate recurring investments in equity stocks, ETFs, with orders placed automatically at your selected frequency—daily, weekly, monthly, or on custom dates.
Note:
Available only for Equity segment symbols on NSE/BSE CM.
You can invest using either a fixed amount or a specified quantity.
Use price range protection (HPR/LPR) to skip execution when prices move beyond your defined range.
With the step-up option, you can gradually increase the investment amount or quantity over time.

### Request Attributes

### SIP Frequency Values

### Validations & Error Messages

### Response Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().CreateSmartorderSip(fyersClass);
    }

    public void CreateSmartorderSip(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("symbol", "NSE:SBIN-EQ");
        requestBody.put("side", 1);
        requestBody.put("amount", 5000);
        requestBody.put("productType", "CNC");
        requestBody.put("freq", 3);
        requestBody.put("sip_day", 15);
        requestBody.put("imd_start", false);
        requestBody.put("endTime", 1772512200);
        requestBody.put("hpr", 900);
        requestBody.put("lpr", 600);
        requestBody.put("step_up_freq", 3);
        requestBody.put("step_up_amount", 500);

        Tuple<JSONObject, JSONObject> result = fyersClass.CreateSmartorderSip(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order SIP Created: " + result.Item1());
        } else {
            System.out.println("Create Smart Order SIP Error: " + result.Item2());
        }
    }
}
```
---------------------------------------------------------------------------------------------------------------------------------------------

#### Sample Success Response
---------------------------------------------------------------------------------------------------------------------------------------------
Response structure:

```json
{
  "code": 200,
  "message": "Your Equity SIP for NSE:SBIN-EQ has been successfully initiated",
  "s": "ok",
  "id": "1bf162b1-6406-407a-911a-5a3309e1aaba"
}
```

## Modify Smart Order
You can modify a pending Smart Order by updating parameters such as quantity, price, and time settings, depending on the order type.
Note:
The order must be in a paused state to be modified (except SIP orders).
Only include the parameters you want to update. All others will retain their original values.
Certain parameters cannot be modified once the order has started executing.

### Request Attributes

### Optional Request Attributes (by Order Type)
Limit Order (flowtype: 4)

#### Trail Order (flowtype: 6)

#### Step Order (flowtype: 3)

#### SIP Order (flowtype: 7)

#### Validations & Error Handling

##### General Validations

##### Limit Order Validations (flowtype: 4)

##### Trail Order Validations (flowtype: 6)

##### Step Order Validations (flowtype: 3)

##### Response Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().ModifySmartorder(fyersClass);
    }

    public void ModifySmartorder(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("flowId", "d99419e1-42c4-4944-b9f6-5399b68cd686");
        requestBody.put("qty", 5);
        requestBody.put("limitPrice", 0);
        requestBody.put("endTime", 1769162100);

        Tuple<JSONObject, JSONObject> result = fyersClass.ModifySmartorder(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Modified: " + result.Item1());
        } else {
            System.out.println("Modify Smart Order Error: " + result.Item2());
        }
    }
}
```
---------------------------------------------------------------------------------------------------------------------------------------------

##### Sample Success Response
---------------------------------------------------------------------------------------------------------------------------------------------
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to modify smart order",
  "s": "ok"
}
```

#### Cancel Smart Order
Cancel a pending Smart Order. Once cancelled, the order cannot be resumed.

##### Request Attributes

##### Validations & Error Handling

##### Response Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().CancelSmartorder(fyersClass);
    }

    public void CancelSmartorder(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("flowId", "d40d8597-26da-434b-879c-0de3b8492cda");

        Tuple<JSONObject, JSONObject> result = fyersClass.CancelSmartorder(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Cancelled: " + result.Item1());
        } else {
            System.out.println("Cancel Smart Order Error: " + result.Item2());
        }
    }
}
```
---------------------------------------------------------------------------------------------------------------------------------------------

##### Sample Success Response
---------------------------------------------------------------------------------------------------------------------------------------------
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to cancel smart order",
  "s": "ok"
}
```

#### Pause Smart Order
Pause a running smart order. This temporarily stops order execution without cancelling it.
To modify a Smart Order, it must be paused first (pausing is not mandatory for Smart Limit order modification).
The order can be resumed later using the Resume API.

##### Request Attributes

##### Validations & Error Handling

##### Response Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().PauseSmartorder(fyersClass);
    }

    public void PauseSmartorder(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("flowId", "d99419e1-42c4-4944-b9f6-5399b68cd686");

        Tuple<JSONObject, JSONObject> result = fyersClass.PauseSmartorder(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Paused: " + result.Item1());
        } else {
            System.out.println("Pause Smart Order Error: " + result.Item2());
        }
    }
}
```
---------------------------------------------------------------------------------------------------------------------------------------------

##### Sample Success Response
---------------------------------------------------------------------------------------------------------------------------------------------
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to pause smart order",
  "s": "ok"
}
```

#### Resume Smart Order
Resume a paused Smart Order. This restarts the order for execution and is mandatory after modifying the order.

##### Request Attributes

##### Validations & Error Handling

##### Response Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().ResumeSmartorder(fyersClass);
    }

    public void ResumeSmartorder(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("flowId", "d99419e1-42c4-4944-b9f6-5399b68cd686");

        Tuple<JSONObject, JSONObject> result = fyersClass.ResumeSmartorder(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Order Resumed: " + result.Item1());
        } else {
            System.out.println("Resume Smart Order Error: " + result.Item2());
        }
    }
}
```

##### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "message": "Request sent successfully to resume smart order",
  "s": "ok"
}
```

#### Smart Order Book
Retrieve the list of all smart orders (active, completed, and cancelled).

##### Optional Query Parameters

##### Response Attributes

##### Order Book Item Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().GetSmartorderBookWithFilter(fyersClass);
    }

    public void GetSmartorderBookWithFilter(FyersClass fyersClass) {
        String queryParams = "";

        Tuple<JSONObject, JSONObject> result = fyersClass.GetSmartorderBookWithFilter(queryParams);

        if (result != null) {
            if (result.Item2() == null) {
                System.out.println("Smart Order Book: " + result.Item1());
            } else {
                System.out.println("Get Smart Order Book Error: " + result.Item2());
            }
            } else {
            System.out.println("Get Smart Order Book Error: Result is null");
        }
    }
}
```

##### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "s": "ok",
  "orderBook": [
    {
      "flowId": "9d12ded8-f046-440f-89f5-e750a37e6048",
      "flowtype": 4,
      "symbol": "NSE:SBIN-EQ",
      "side": 1,
      "qty": 5,
      "filledQty": 0,
      "status": 6,
      "productType": "CNC",
      "limitPrice": 760,
      "createdTime": 1738383000,
      "updatedTime": 1738383000
    }
],
  "count": 1,
  "filterCount": 1
}
```

#### Smart Exits
Smart Exit is a real-time P&L monitoring system that tracks up to 100 open positions (excluding CNC). It takes action when your defined profit or loss targets are reached. You can choose to receive only notifications or enable automatic exit when a target is met.
Exit Types
Only Alert (1): Sends a notification when your target is reached. No positions are exited automatically. You need to take manual action.
Exit with Alert (2): Sends a notification and exits all open positions (Intraday, margin, MTF) immediately when your target is reached.
Exit with Alert + Wait (3): Sends a notification, then waits for a defined recovery period. If the target is still not met after waiting, it exits all positions.
Note: Only one Smart Exit flow can be active per user. It monitors intraday, margin and MTF positions.
Create Smart Exit
Create a new Smart Exit trigger to monitor your overall position P&L and take action when a profit or loss target is reached.

##### Request Attributes

##### Exit Types (type field)

##### Validations & Error Handling

##### Response Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().CreateSmartExitTrigger(fyersClass);
    }

    public void CreateSmartExitTrigger(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("name", "Alert Only Strategy");
        requestBody.put("type", 1);
        requestBody.put("profitRate", 5000);
        requestBody.put("lossRate", -2000);

        Tuple<JSONObject, JSONObject> result = fyersClass.CreateSmartExitTrigger(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Exit Trigger Created: " + result.Item1());
        } else {
            System.out.println("Create Smart Exit Trigger Error: " + result.Item2());
        }
    }
}
```

##### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "message": "Created smart exit successfully.",
  "s": "ok",
  "data": {
    "flowId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Alert Only Strategy",
    "profit_rate": 5000,
    "loss_rate": -2000,
    "type": 1,
    "waitTime": 0
  }
}
```

#### Fetch Smart Exit
You can fetch the recent Smart Exit triggers.
Includes current monitoring status.
If active, includes expiry timestamp (end of trading day).

##### Response Attributes

##### Flow Item Attributes

##### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        new App().GetSmartExitTriggers(fyersClass);
    }

    public void GetSmartExitTriggers(FyersClass fyersClass) {
        String queryParams = "";

        Tuple<JSONObject, JSONObject> result = fyersClass.GetSmartExitTriggers(queryParams);

        if (result != null) {
            if (result.Item2() == null) {
                System.out.println("Smart Exit Triggers: " + result.Item1());
        } else {
                System.out.println("Get Smart Exit Triggers Error: " + result.Item2());
            }
        } else {
            System.out.println("Get Smart Exit Triggers Error: Result is null");
        }
    }
}
```

##### Sample Success Response
Response structure:

```json
{
  "code": 200,
  "s": "ok",
  "data": [
    {
      "flowId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "My Daily Exit",
      "profitRate": 5000,
      "lossRate": -2000,
      "type": 1,
      "activeStatus": 1,
      "status": 1,
      "exitStatus": 1,
      "createdTimestamp": 1738383000,
      "updatedTimestamp": 1738383600,
      "expiryTimestamp": 1738425000
    }
    ]
}
```

## Modify / Activate / Deactivate Smart Exit
Manage Smart Exit triggers by updating preferences or changing the status to active or inactive. You can also deactivate triggers when needed.

## Modify Smart Exit (PUT)
Use this endpoint to modify a Smart Exit trigger.
You can update the target values, exit type, or wait time.
If the trigger is active, updates are validated against the current P&L.
Either a profit target or a loss limit must be provided.

### Request Attributes (Modify)

### Exit Types (type field)

### Activate/Deactivate Smart Exit (POST /se/activate)
You can activate/deactivate the Smart Exit using this API
Activation starts real-time P&L monitoring.
Deactivation stops the monitoring.
Requires open Intraday, Margin, or MTF positions (excluding CNC).
Supports up to 100 open positions.

### Request Attributes (Activate)

### Validations & Error Handling

#### Edit Validations

#### Activate Validations

#### Response Attributes

#### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {
    public static void main(String[] args) {
        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = "AAAAAAAA-100";
        fyersClass.accessToken = "YOUR_ACCESS_TOKEN";
        App app = new App();
        app.UpdateSmartExitTrigger(fyersClass);
        app.ActivateDeactivateSmartExitTrigger(fyersClass);
    }

    public void UpdateSmartExitTrigger(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("flowId", "2ac656b4-d11c-4013-9e90-04ce7f9dc273");
        requestBody.put("profitRate", 80050);
        requestBody.put("lossRate", -50);
        requestBody.put("type", 1);
        requestBody.put("name", "test");

        Tuple<JSONObject, JSONObject> result = fyersClass.UpdateSmartExitTrigger(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Exit Trigger Updated: " + result.Item1());
} else {
            System.out.println("Update Smart Exit Trigger Error: " + result.Item2());
        }
    }

    public void ActivateDeactivateSmartExitTrigger(FyersClass fyersClass) {
        JSONObject requestBody = new JSONObject();
        requestBody.put("flowId", "2ac656b4-d11c-4013-9e90-04ce7f9dc273");

        Tuple<JSONObject, JSONObject> result = fyersClass.ActivateDeactivateSmartExitTrigger(requestBody);

        if (result.Item2() == null) {
            System.out.println("Smart Exit Trigger Activated: " + result.Item1());
} else {
            System.out.println("Activate Smart Exit Trigger Error: " + result.Item2());
        }
    }
}
```

#### Sample Success Response (Update)
Response structure:

```json
{
  "code": 200,
  "message": "Updated the smart exit successfully.",
  "s": "ok",
  "data": {
    "flowId": "2ac656b4-d11c-4013-9e90-04ce7f9dc273",
    "name": "test",
    "profitRate": 80050,
    "lossRate": -50,
    "type": 1,
    "waitTime": 0
  }
}

```

#### Sample Success Response (Activate/Deactivate)
Response structure:

```json
{
  "code": 200,
  "message": "Smart exit activated successfully.",
  "s": "ok",
  "data": {
    "flowId": "2ac656b4-d11c-4013-9e90-04ce7f9dc273",
    "activeStatus": 1
  }
}
```

# Other Transactions

## Modify Orders
This allows the user to modify a pending order. User can provide parameters which needs to be modified. In case a particular parameter has not been provided, the original value will be considered.

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ModifySingleOrder(fyersClass);
    }

    public void ModifySingleOrder(FyersClass order) {
        PlaceOrderModel model = new PlaceOrderModel();
        model.OrderId = "24092500338700";
        model.Qty = 1;
        model.OrderType = OrderType.MarketOrder.getDescription();
        model.LimitPrice = 0;
        model.StopPrice = 0;
        Tuple<JSONObject, JSONObject> ResponseTuple = order.ModifyOrder(model);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Order ID: " + ResponseTuple.Item1()); 
        } else {
            System.out.println("Modify order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Order ID":{
  "code":1102,
  "s":"ok",
  "id":"24092700247449",
  "message":"Successfully modified order"
}
```

## Modify Multi Orders
You can modify upto 10 orders simultaneously via the API.
While Modifying Multi orders you need to pass an ARRAY containing the orders request attributes

### Sample Request :
```json
[{

    "id":orderId,
    "type":1,
    "limitPrice": 61049,
    "qty":1
},
  {
    "id":orderId,
    "type":1,
    "limitPrice": 61049,
    "qty":1 
}]
```

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.utilities.OrderType;
import com.tts.in.utilities.TransactionType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ModifyMultipleOrder(fyersClass);
    }

    public void ModifyMultipleOrder(FyersClass order) {
        List<PlaceOrderModel> placeOrdersList = new ArrayList<>();
        PlaceOrderModel model = new PlaceOrderModel();
        model.OrderId = "24092700252396";
        model.Qty = 1;
        model.OrderType = OrderType.LimitOrder.getDescription();
        model.Side = TransactionType.Buy.getValue();
        model.LimitPrice = 0;

        PlaceOrderModel model2 = new PlaceOrderModel();
        model2.OrderId = "24092700251935";
        model2.Qty = 1;
        model2.OrderType = OrderType.LimitOrder.getDescription();
        model2.Side = TransactionType.Buy.getValue();
        model2.LimitPrice = 0;

        placeOrdersList.add(model);
        placeOrdersList.add(model2);

        Tuple<JSONObject, JSONObject> ResponseTuple = order.ModifyMultipleOrders(placeOrdersList);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders ID: " + ResponseTuple.Item1());
        } else {
            System.out.println("Place order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Orders ID":{
    "code":200,
    "s":"ok",
    "data":[
        {
          "statusDescription":"OK",
          "body":{
              "code":1102,
              "s":"ok",
              "id":"24092700263215",
              "message":"Successfully modified order"
},
          "statusCode":200
},
        {
          "statusDescription":"OK",
          "body":{
              "code":1102,
              "s":"ok",
              "id":"24092700263017",
              "message":"Successfully modified order"
},
          "statusCode":200
        }
],
    "message":""
  }
```

## Cancel Order
Cancel pending orders

### Request attributes
Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.CancelSingleOrder(fyersClass);
    }

    public void CancelSingleOrder(FyersClass order) {
        String OrderId = "24092500390860";

        Tuple<JSONObject, JSONObject> ResponseTuple = order.CancelOrder(OrderId);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders ID: " + ResponseTuple.Item1());
} else {
            System.out.println("Place order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Orders ID":{
  "code":1103,
  "s":"ok",
  "id":"24092700267322",
  "message":"Successfully cancelled order"
}
```

## Cancel Multi Order
You can cancel upto 10 orders simultaneously via the API.
While cancelling Multi orders you need to pass an ARRAY containing the orders request attributes

### Sample Request :
[{
  "id":orderId
},
  {
  "id":orderId
}]

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.CancelMultipleOrder(fyersClass);
    }

    public void CancelMultipleOrder(FyersClass order) {
        List<String> orderIdList = new ArrayList<>();
        orderIdList.add("24092500390490");
        orderIdList.add("24092500389713");
        Tuple<JSONObject, JSONObject> ResponseTuple = order.CancelMultipleOrders(orderIdList);
        if (ResponseTuple.Item2() == null) {
            System.out.println("Orders ID: " + ResponseTuple.Item1());
} else {
            System.out.println("Place order Message : " + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Orders ID":{
  "code":200,
  "s":"ok",
  "data":[
      {
        "statusDescription":"OK",
        "body":{
            "code":1103,
            "s":"ok",
            "id":"24092700269223",
            "message":"Successfully cancelled order"
        },
                "statusCode":200
        },
      {
        "statusDescription":"OK",
        "body":{
            "code":1103,
            "s":"ok",
            "id":"24092700268967",
            "message":"Successfully cancelled order"
        },
        "statusCode":200
      }
],
  "message":""
}
```

## Exit Position
This allows the user to either exit all open positions or any specific open position.

### Request attributes

### Response attributes
Sample Request :

```json
  {
    "id": [positionId1,positionId2]
  }
```

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ExitPosition(fyersClass);
    }

    public void ExitPosition(FyersClass positions) {
        List<String> positionIDs = new ArrayList<>();
        // by sending empty list all posiion will be closed
        Tuple<JSONObject, JSONObject> jObject = positions.ExitPositions(positionIDs);
        if (jObject.Item2() == null) {
            System.out.println("Position Message: " + jObject.Item1()); 
        } else {
            System.out.println("Position Error: " + jObject.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Position Message":{
  "code":200,
  "s":"ok",
  "message":"Position NSE:UCOBANK-EQ-INTRADAY is closed."
}          
```

## Exit Position - By Id

### Request attributes

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ExitPosition(fyersClass);
    }

    public void ExitPosition(FyersClass positions) {
        String positionId = "NSE:IDEA-EQ-INTRADAY";
        String positionId2 = "NSE:EASEMYTRIP-EQ-INTRADAY";

        List<String> positionIDs = new ArrayList<>();
        positionIDs.add(positionId);
        //positionIDs.add(positionId2);

        Tuple<JSONObject, JSONObject> jObject = positions.ExitPositions(positionIDs);
        if (jObject.Item2() == null) {
            System.out.println("Position Message: " + jObject.Item1()); 
        } else {
            System.out.println("Position Error: " + jObject.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Position Message":{
  "code":200,
  "s":"ok",
  "message":"All positions are closed."
}
```

## Exit Position - By Segment Side & productType

### Request attributes

### Request samples

```java

package com.example;
import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.ProductType;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ExitPositionBySegmentSidePrdType(fyersClass);
    }

    public void ExitPositionBySegmentSidePrdType(FyersClass positions) {
        int[] sides = new int[]{1};
        int[] segments = new int[]{10};
        String[] products = new String[]{ProductType.INTRADAY};

        Tuple<JSONObject, JSONObject> jObject = positions.ExitPositionBySegmentSidePrdType(sides, segments, products);
        if (jObject.Item2() == null) {
            System.out.println("Position Message: " + jObject.Item1());
} else {
            System.out.println("Position Error: " + jObject.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Position Message":{
  "code":200,
  "s":"ok",
  "message":"All positions are closed."
}
```

## Pending Order Cancel
This is an added functionality for exit position API. If a user has open positions in particular stocks and also working opposite order of the particular stock, the user can close the working orders and then exit the open positions.

Endpoint: https://api-t1.fyers.in/api/v3/positions

The following should be passed in the body of the DELETE method of positions to cancel the pending orders:
{"pending_orders_cancel": 1}
If only a single symbol's order and position needs to be cancelled, the position ID should also be sent in the body:
{"id": "NSE:SBIN-EQ-INTRADAY","pending_orders_cancel": 1}

### Request samples

```shell
curl -H "Authorization:app_id:access_token" -H "Content-Type: application/json" -X DELETE -d '{"pending_orders_cancel": 1}' https://api-t1.fyers.in/api/v3/positions
```

### Sample Success Response

```json
{
 "s": "ok",
 "code": 200,
 "message": "The position is closed."
}
```

## Convert Position
This allows the user to convert an open position from one product type to another.

### Request attributes
Notes
CNC, CO, BO, and MTF positions cannot be converted.
You cannot convert positions to CO, BO,MTF.

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.model.PositionConversionModel;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.PositionConversion(fyersClass);
    }

    public void PositionConversion(FyersClass positions) {
        PositionConversionModel positionConversionModel = new PositionConversionModel();

        positionConversionModel.Symbol = "NSE:IDEA-EQ-INTRADAY";
        positionConversionModel.Side = 1;
        positionConversionModel.ConvertQty = 1;
        positionConversionModel.ConvertFrom = "INTRADAY";
        positionConversionModel.ConvertTo = "CNC";
        positionConversionModel.Overnight = 0;

        Tuple<JSONObject, JSONObject> jObject = positions.PositionConversion(positionConversionModel);
        if (jObject.Item2() == null) {
            System.out.println("Position Message: " + jObject.Item1());
        } else {
            System.out.println("Position error Message: " + jObject.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Position Message":{
  "code":200,
  "s":"ok",
  "message":"Position Conversion is successful"
}
```

## Margin Calculator

### Span Margin Calculator
Span margin API will calculate the span margin and exposure margin required for the given stock symbols.

#### Request Attributes

#### Request samples

```shell
curl --location --request POST 'https://api.fyers.in/api/v2/span_margin' \ --header 'Authorization: UC0KMO****-102:eyJ0eX*****' \--header 'Content-Type: application/json' \ --data-raw '{
         "data": [{
           "symbol": "NSE:BANKNIFTY23NOV44400CE",
           "qty": 50,
           "side": -1,
           "type": 2,
           "productType": "INTRADAY",
           "limitPrice": 0.0,
           "stopLoss": 0.0
}, {
           "symbol": "NSE:BANKNIFTY23NOVFUT",
           "qty": 50,
           "side": -1,
           "type": 2,
           "productType": "INTRADAY",
           "limitPrice": 0.0,
           "stopLoss": 0.0
}]
```

### Multiorder Margin Calculator
Multiorder margin API will calculate the margin required for the given list of order bodies.

#### Request Attributes

#### Response attributes

#### Request samples

```shell
curl --location --request POST 'https://api-t1.fyers.in/api/v3/multiorder/margin' \ --header 'Authorization: UC0KMO****-102:eyJ0eX*****' \--header 'Content-Type: application/json' \ --data-raw '{
  {
    "data": [
      {
        "symbol": "NSE:NIFTY23DECFUT",
        "qty": 50,
        "side": 1,
        "type": 2,
        "productType": "MARGIN",
        "limitPrice": 0.0,
        "stopLoss": 0.0,
        "stopPrice": 0.0,
        "takeProfit": 0.0
},
      {
        "symbol": "NSE:SBIN-EQ",
        "qty": 50,
        "side": 1,
        "type": 2,
        "productType": "MARGIN",
        "limitPrice": 0.0,
        "stopLoss": 0.0,
        "stopPrice": 0.0,
        "takeProfit": 0.0
      }
]
  }
```

Sample Success Response

```json
  { 
    "code": 200, 
    "message": "", 
    "data": { 
        "margin_avail": 1999.9, 
        "margin_total": 147738.05634886527, 
        "margin_new_order": 147738.05634886527 
},
    "s": "ok"
  } 
```

# Broker Config

## Market Status
Fetches the current market status of all the exchanges and their segments
Response attributes - For each exchange market segment

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetMarketStatus(fyersClass);
    }

    public void GetMarketStatus(FyersClass fyersClass) {
        Tuple<JSONObject, JSONObject> stockTuple = fyersClass.GetMarketStatus();

        if (stockTuple.Item2() == null) {
            System.out.println("Market Status:" + stockTuple.Item1()); 
        } else {
            System.out.println("Market Status Error:" + stockTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Market Status":{
  "code":200,
  "s":"ok",
  "marketStatus":[
      {
        "segment":10,
        "exchange":10,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":10,
        "exchange":10,
        "market_type":"ODD_LOT",
        "status":"OPEN"
},
      {
        "segment":10,
        "exchange":10,
        "market_type":"CALL_AUCTION2",
        "status":"PREOPEN"
},
      {
        "segment":10,
        "exchange":10,
        "market_type":"AUCTION",
        "status":"CLOSED"
},
      {
        "segment":11,
        "exchange":10,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":12,
        "exchange":10,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":20,
        "exchange":10,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":20,
        "exchange":11,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":12,
        "exchange":12,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":10,
        "exchange":12,
        "market_type":"NORMAL",
        "status":"OPEN"
},
      {
        "segment":10,
        "exchange":12,
        "market_type":"AUCTION",
        "status":"OPEN"
},
      {
        "segment":11,
        "exchange":12,
        "market_type":"NORMAL",
        "status":"OPEN"
      }
],
  "message":""
}
```

Symbol Master
You can get all the latest symbols of all the exchanges from the symbol master files
NSE – Currency Derivatives:https://public.fyers.in/sym_details/NSE_CD.csv
NSE – Equity Derivatives:https://public.fyers.in/sym_details/NSE_FO.csv
NSE – Commodity:https://public.fyers.in/sym_details/NSE_COM.csv
NSE – Capital Market:https://public.fyers.in/sym_details/NSE_CM.csv
BSE – Capital Market:https://public.fyers.in/sym_details/BSE_CM.csv
BSE - Equity Derivatives:https://public.fyers.in/sym_details/BSE_FO.csv
MCX - Commodity:https://public.fyers.in/sym_details/MCX_COM.csv

### File Headers

## Symbol Master Json
You can get all the latest symbols of all the exchanges from the symbol master json files
NSE – Currency Derivatives:https://public.fyers.in/sym_details/NSE_CD_sym_master.json
NSE – Equity Derivatives:https://public.fyers.in/sym_details/NSE_FO_sym_master.json
NSE – Commodity:https://public.fyers.in/sym_details/NSE_COM_sym_master.json
NSE – Capital Market:https://public.fyers.in/sym_details/NSE_CM_sym_master.json
BSE – Capital Market:https://public.fyers.in/sym_details/BSE_CM_sym_master.json
BSE - Equity Derivatives:https://public.fyers.in/sym_details/BSE_FO_sym_master.json
MCX - Commodity:https://public.fyers.in/sym_details/MCX_COM_sym_master.json

### File Format
Key will be the symbol ticker and value will hold the below json object which has symbol master details for that particular symbol ticker.

# EDIS
Electronic Delivery Instruction Slip (eDIS) allows you to sell shares if your Power of Attorney (POA) is not submitted or DDPI is not activated.

Please note: You can only sell the authorized stocks that you are holding in your Demat account. You can activate DDPI to ensure a smooth and uninterrupted trading experience.

## TPIN Generation
TPIN is an authorization code generated by CDSL/NSDL respectively, using which the customer validates/authorises the transaction.

### Request samples
```shell
curl --location --request GET 'https://api.fyers.in/api/v2/tpin' 
--header 'Authorization: app_id:access_token'
```

Sample Success Response

```shell
{"s": "ok", "code": 200, "message": "Successfully sent request for BO Tpin generation", "data": ""}
```

Details This API provides information about holding authorizations that have been successfully completed.
#Request samples

```shell
curl --location --request GET 'https://api.fyers.in/api/v2/details' \
--header 'Authorization: app_id:access_token'
```


### Sample Success Response
```shell
{"s": "ok", "code": 200, "message": "", "data": [{"clientId": "DXXXX4", "isin": "INE313D01013", "qty": 1.0, "qtyUtlize": 0.0, "entryDate": "07/06/2021 13:58:56", "startDate": "07/06/2021", "endDate": "07/06/2021", "noOfDays": 1, "source": "W", "status": "SUCCESS", "reason": "eDIS Transaction done successfully", "internalTxnId": "915485", "dpTxnId": "0706202171316317", "errCode": "NA", "errorCount": "0", "transactionId": "915484108176"}]
```

## Index
This Api will provide you with the CDSL page to login where you can submit your Holdings information and accordingly you can provide the same to exchange to Sell your holdings.

### Request samples

```shell
curl --location --request POST 'https://api.fyers.in/api/v2/index' --header 'Authorization: app_id:accessToken' --header 'Content-Type: application/json' 
```
--data-raw '{"recordLst": [{"isin_code": "INE114A01011","qty": "1","symbol": "NSE:SAIL-EQ"}]}'


### Sample Success Response

{"s": "ok", "code": 200, "message": "", "data": "<table width=\"100%\"><tr><td><table align=\"center\"><tr><td><table  align=\"center\"><tr><th><img src=\"https://clib.fyers.in/fy_images/320x132.png\" alt=\"Fyers\" width=\"220\" /></th></tr><tr style=\"color:#7c7e7f;\"><th class=\"sansserif\">&nbsp; &nbsp; &nbsp; Free Investment Zone</th></tr></table></td></tr></table><table align=\"center\" bgcolor=\"#ffffff\"  style=\"Margin: 0 auto;max-width: 600px;min-width: 320px; border-style:solid;border-left-width:10px;padding:5px; border-color:#ffffff;\"><tr style=\"color:#3e4751;\"><th><img src=\"https://mockedis.cdslindia.com/images/CDSL-Logo.png\"></th> </tr><tr><th><hr  style=\" border:1px solid\" width=\"12%\"><br></th></tr><br><tr align=\"left\" style=\"color:#7c7e7f;\"><td class=\"sansserif\" id=\"tpinDescp\">As per new regulations, clients are required to authorise sell transactions by providing specific instrument details along with quantites at the CDSL portal prior to executing any sell transactions from their demat account.<br><br> The autorisation will be valid till the end of the day irrespective of whether you have completed the sell transaction or not. <br><br></td></tr><tr align=\"left\" style=\"color:#7c7e7f;\"><td class=\"sansserif\"><br></td></tr><tr align=\"left\" style=\"color:#7c7e7f;\"><td class=\"sansserif\" style=\"text-align:center\"><br><br>       <form name= \"frmDIS\" method = \"post\" action= \"https://eDIS.cdslindia.com/eDIS/VerifyDIS\" >        <input type= \"hidden\" name= \"DPId\" value= \"89400\" />        <input type= \"hidden\" name= \"ReqId\" value= \"917177108176\" />        <input type= \"hidden\" name= \"Version\" value= \"1.1\" />        <input type= \"hidden\" name= \"TransDtls\" value= \"LD9WAIJCL2jgSj1hY2DABqfayzA6iInmBvh9ub+Ftqy0P+V/Qy4kRf9dsBHElVwcDdAhTx5a6+9g3y/TcVh1zEdMbslVXAcMi913u+YwHNp5IWUS6XAOCAx9UY01XZ+OVAgAez/9m+7cP6TjOeOBCqw57MWZ1y5N6OsPyzh+ecLUD2e6G5hJMc/ZKRw1dl5FvzJGpfmr1MGpM5jwtpzpbksmAIiAUMyx+zqfT5dX27ZLp0P4MRCl/QNyLnMCNwbhoPx7TEp6fN23UD8T3Y1742Kb1mVz3b4Aw6Kt+maXsjM12jP2bHZuM+rYKkjQWBK+AejT3Uk9vAZmFbd+Y1xeqKJFXAoKRA+cQXiCp8gjpm6RaZ04p8V7MMTWrIhpKAXNCCpCb+suxO74mjfW18AfZMKxX0UK/JjVomEoHz0GaIAKq4z3KAfwwcpqhtcNZv8u68DyeMmCFwojJ0Y+SBLjwUlJV3SWqpYhBnXxni5YsmvOK5NQLfWxd+KjuWK4gXgONgxWIcPMWsjY++JkYwtAlAhI43khxe0Y0SjntmZTZ4A=\" />        <input type= \"submit\" value=\"Submit\">         </form></td></tr><tr><td style=\"text-align: center;\" class=\"like-anchor\"><div id=\"forgetTpinDiv\"><a href=\"#\" id=\"forgetTpin\" onclick=\"tpin()\">Forgot CDSL TPIN</a></div></td></tr></tbody></table></td></tr></tbody></table>"}

## Inquiry
This Api is used to get the information/status of the provided transaction Id for the respective holdings you have on your end.
Note: Transaction ID to be base64 encoded in payload. please refer to the online tool for conversion.

#### Request samples

```shell
curl --location --request POST 'https://api.fyers.in/api/v2/inquiry' 

--header 'Authorization: app_id:access_Token' --header 'Content-Type: application/json' --data-raw '{"transactionId": "OTE1NDg0MTA4MTc2"}'
```

#### Sample Success Response
```shell
{"s": "ok", "code": 200, "message": "", "data": {"FAILED_CNT": 0, "SUCEESS_CNT": 1}}
```

# Postback (Webhooks)
The Postback API sends a POST request with a JSON payload to the registered postback_url of your app when an order's status changes. This enables you to get orders updates reliably, irrespective of when they happen (Pending, Cancel, Rejected, Traded).

## 1. Create web hooks
To Create Postback, you need to create an App by:
Login to API Dashboard.
Once you have logged into the Dashboard, you will see a block where you need to update your webhook URL, webhook secret, and the webhook preference (Cancel, Rejected, Pending, Traded). Here you can add multiple webhooks by clicking on the "Add webhook" button.
After entering the required details, click on the "Create App" button by accepting the terms and conditions.

## 2. Active App
After successful creation of the App, you need to activate the App by following the Authentication & Login mechanism. After successfully logging in via the App, you will be able to get the order data over the webhook URL.

## 3. Response
The JSON payload is posted as a raw HTTP POST body. You will have to read the raw body.

### Response Output :

```json
{
  "orderDateTime": "18-Jul-2023 11:44:29",
  "id": "23071800238607",
  "exchOrdId": "2500000061319029",
  "side": -1,
  "segment": 11,
  "instrument": 15,
  "productType": "MARGIN",
  "status": 2,
  "qty": 5400,
  "remainingQuantity": 0,
  "filledQty": 5400,
  "limitPrice": 2.15,
  "stopPrice": 0,
  "type": 2,
  "discloseQty": 0,
  "dqQtyRem": 0,
  "orderValidity": "DAY",
  "source": "M",
  "fyToken": "101123072754619",
  "offlineOrder": false,
  "message": "Completed",
  "orderNumStatus": "23071800238607:2",
  "tradedPrice": 2.15,
  "exchange": 10,
  "pan": "",
  "clientId": "xxxx",
  "symbol": "NSE:ABCAPITAL23JUL190CE"
}
```

## 4. Blacklisting
The webhook/postback URL provided by the user can be blacklisted if the response status is not 200 from your web server or if the post request to your PostbackURL fails. These URLs are blacklisted for 30 minutes, and during that time, no webhooks are sent to the blacklisted URL. Additionally, the URL will be blacklisted after continuous failures, typically after three retries.

# Data Api

## History
The historical API provides archived data (up to date) for the symbols. across various exchanges within the given range. A historical record is presented in the form of a candle and the data is available in different resolutions like - minute, 10 minutes, 60 minutes...240 minutes and daily.

### To Handle partial Candle
To receive completed candle data, it is important to send a timestamp that comes before the current minute. If you send a timestamp for the current minute, you will receive partial data because the minute is not yet finished. Therefore, it is recommended to always use a "range_to" timestamp of the previous minute to ensure that you receive the completed candle data.

#### Example:
Current Time(seconds can be 1-59): 12:10:20 PM
Input for history will be:
range_from: 12:08:00 PM
range_to: Current Time - 1 minute = 12:09:20 PM
So you will get 2 candles - 12:08 PM and 12:09 PM candles. This example is for 1-minute candles; for other resolutions, you have to subtract the resolution time from "range_to" to get completed candles only.

### Limits for History
Unlimited number of stocks history data can be downloaded in a day.
Up to 100 days of data per request for resolutions of 1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 120, 180, and 240 minutes. Data is available from July 3, 2017.
For 1D resolutions up to 366 days of data per request for 1D (1 day) resolutions.
For Seconds Charts the history will be available only for 30-Trading Days

### Request Attribute
Response Attribute

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.model.StockHistoryModel;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetStockHistory(fyersClass);
    }

    public void GetStockHistory(FyersClass fyersClass) {
        StockHistoryModel model = new StockHistoryModel();
        model.Symbol = "NSE:SBIN-EQ";
        model.Resolution = "30";
        model.DateFormat = "1";
        model.RangeFrom = "2021-01-01";
        model.RangeTo = "2021-01-02";
        model.ContFlag = 1;

        Tuple<JSONObject, JSONObject> stockTuple = fyersClass.GetStockHistory(model);
        if (stockTuple.Item2() == null) {
            System.out.println("Stock History: " + stockTuple.Item1());
} else {
            System.out.println("Stock History Error: " + stockTuple.Item2());
        }
    }
}
```

### Sample Success Response

```json
"Stock History":{
    "code":200,
    "s":"ok",
    "candles":[
            [
            1609472700,
            274.9,
            278.05,
            274.4,
            277.75,
            4346532
            ],
            [
            1609474500,
            277.75,
            278.5,
            277.2,
            277.45,
            2394262
            ],
            [
            1609476300,
            277.4,
            277.7,
            276.8,
            277.25,
            1312581
            ],
            [
            1609478100,
            277.2,
            277.9,
            277.2,
            277.75,
            854730
            ],
            [
            1609479900,
            277.75,
            278.2,
            277.5,
            277.7,
            1133743
            ],
            [
            1609481700,
            277.7,
            278.8,
            277.65,
            278.4,
            2443462
            ],
            [
            1609483500,
            278.4,
            279.3,
            278.1,
            279,
            1781959
            ],
            [
            1609485300,
            278.95,
            279,
            278.55,
            278.7,
            875293
            ],
            [
            1609487100,
            278.65,
            279,
            277.9,
            278.35,
            1106786
            ],
            [
            1609488900,
            278.35,
            279.5,
            278.2,
            279.45,
            1462675
            ],
            [
            1609490700,
            279.5,
            279.85,
            279.2,
            279.35,
            1949862
            ],
            [
            1609492500,
            279.45,
            279.65,
            278.8,
            279.5,
            2600929
            ],
            [
            1609494300,
            279.4,
            280,
            279.05,
            279.05,
            2257563
            ]
    ],
    "message":""
  }
```

## Quotes
The Quotes API retrieves the full market quotes for one or more symbols provided by the user.

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetStockQuotes(fyersClass);
    }

    public void GetStockQuotes(FyersClass fyersClass) {
        String symbols = "NSE:TCS-EQ";
        Tuple<JSONObject, JSONObject> stockTuple = fyersClass.GetStockQuotes(symbols);

        if (stockTuple.Item2() == null) {
            System.out.println("Stock Quotes:" + stockTuple.Item1());
} else {
            System.out.println("Error: " + stockTuple.Item2());
        }
    }
}
```


### Sample Success Response

```json
"Stock Quotes": {
    "code":200,
    "s":"ok",
    "d":[
        {
          "s":"ok",
          "v":{
              "tt":"1727395200",
              "symbol":"NSE:TCS-EQ",
              "lp":4312.4,
              "ch":19.9,
              "high_price":4378,
              "description":"NSE:TCS-EQ",
              "chp":0.46,
              "fyToken":"101000000011536",
              "spread":0.6,
              "volume":1963609,
              "original_name":"NSE:TCS-EQ",
              "ask":4313,
              "exchange":"NSE",
              "short_name":"TCS-EQ",
              "bid":4312.4,
              "low_price":4303,
              "open_price":4335,
              "prev_close_price":4292.5,
              "atp": 4315.6
        },
          "n":"NSE:TCS-EQ"
        }
    ],
    "message":""
  }
```

## Market Depth
The Market Depth API returns the complete market data of the symbol provided. It will include the quantity, OHLC values, and Open Interest fields, and bid/ask prices.

### Request attributes

### Response attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetMarketDepth(fyersClass);
    }

    public void GetMarketDepth(FyersClass fyersClass) {
        Tuple<JSONObject, JSONObject> ResponseTuple = fyersClass.GetMarketDepth("NSE:TCS-EQ", 0);

        if (ResponseTuple.Item2() == null) {
            System.out.println("Market Depth: " + ResponseTuple.Item1());
        } else {
            System.out.println("Market Depth Error:" + ResponseTuple.Item2());
        }
    }
}
```

### Sample Success Response
```json
"Market Depth":{
    "s":"ok",
    "d":{
"NSE:TCS-EQ":{
          "totalbuyqty":91988,
          "totalsellqty":249826,
          "ask":[
              {
                "volume":2,
                "ord":1,
                "price":4313.4
},
              {
                "volume":25,
                "ord":2,
                "price":4313.45
},
              {
                "volume":11,
                "ord":1,
                "price":4313.6
},
              {
                "volume":21,
                "ord":1,
                "price":4313.65
},
              {
                "volume":10,
                "ord":1,
                "price":4314.55
              }
],
          "bids":[
              {
                "volume":7,
                "ord":5,
                "price":4313.35
},
              {
                "volume":57,
                "ord":4,
                "price":4313.3
},
              {
                "volume":17,
                "ord":1,
                "price":4313.25
},
              {
                "volume":1,
                "ord":1,
                "price":4313
},
              {
                "volume":12,
                "ord":1,
                "price":4312.85
              }
]
        }
},
    "message":"Success"
  }
```

## Option Chain
The Optionchain API provides important information about options trading, specifically focusing on strike prices, which are predetermined prices at which an option contract can be exercised. This API offers data for both Call (CE) and Put (PE) options, along with values for index, IndiaVIX (Volatility Index) and available expiry dates. When using the API, you can specify the number of strike prices you're interested in. For example, if you request a strike count of 2, the API will return data for:
At-The-Money (ATM) strike: The closest strike price to the current market price.
Out-of-The-Money (OTM) strikes: Strike prices higher than the current market price for Calls (CE) and lower than the current market price for Puts (PE). The API will return data for 2 CE and 2 PE OTM strikes.
In-The-Money (ITM) strikes: Strike prices lower than the current market price for Calls (CE) and higher than the current market price for Puts (PE). The API will return data for 2 CE and 2 PE ITM strikes.

### Request attributes

### Response attributes

### Request samples


```java

package com.example;
import org.json.JSONObject;
import com.tts.in.model.FyersClass;
import com.tts.in.utilities.Tuple;

public class App {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.GetOptionChain(fyersClass);
    }

    public void GetOptionChain(FyersClass fyersClass) {
        String symbol = "NSE:TCS-EQ";
        int strikeCount = 1;
        String timestamp = "";

        Tuple<JSONObject, JSONObject> stockTuple = fyersClass.GetOptionChain(symbol, strikeCount, timestamp);

        if (stockTuple.Item2() == null) {
            System.out.println("OptionChain:" + stockTuple.Item1());
        } else {
            System.out.println("OptionChain Error: " + stockTuple.Item2());
        }

    }
}
```

### Sample Success Response

```json
"OptionChain":{
  "code":200,
  "s":"ok",
  "data":{
      "callOi":6133225,
      "indiavixData":{
        "ltpchp":-0.42,
        "symbol":"NSE:INDIAVIX-INDEX",
        "ex_symbol":"INDIAVIX",
        "ltpch":-0.05,
        "option_type":"",
        "ask":0,
        "description":"INDIAVIX-INDEX",
        "ltp":11.95,
        "exchange":"NSE",
        "bid":0,
        "fyToken":"101000000026017",
        "strike_price":-1
},
      "optionsChain":[
        {
            "ltpchp":0.54,
            "symbol":"NSE:TCS-EQ",
            "ltpch":23.25,
            "option_type":"",
            "description":"TATA CONSULTANCY SERV LT",
            "ltp":4315.75,
            "fp":4328.95,
            "fyToken":"101000000011536",
            "fpchp":0.56,
            "ex_symbol":"TCS",
            "ask":4315.75,
            "exchange":"NSE",
            "bid":4314.5,
            "fpch":24.05,
            "strike_price":-1
},
        {
            "ltpchp":-13.46,
            "symbol":"NSE:TCS24OCT4250PE",
            "ltpch":-13.45,
            "option_type":"PE",
            "ltp":86.5,
            "oich":37800,
            "prev_oi":134925,
            "fyToken":"1011241031126039",
            "volume":297500,
            "ask":86.6,
            "oi":172725,
            "bid":86,
            "oichp":28.02,
            "strike_price":4250
},
        {
            "ltpchp":6.97,
            "symbol":"NSE:TCS24OCT4250CE",
            "ltpch":10.95,
            "option_type":"CE",
            "ltp":168.05,
            "oich":8750,
            "prev_oi":236950,
            "fyToken":"1011241031126036",
            "volume":448700,
            "ask":168.95,
            "oi":245700,
            "bid":168,
            "oichp":3.69,
            "strike_price":4250
},
        {
            "ltpchp":-12.42,
            "symbol":"NSE:TCS24OCT4300PE",
            "ltpch":-15.3,
            "option_type":"PE",
            "ltp":107.9,
            "oich":14875,
            "prev_oi":713125,
            "fyToken":"1011241031103300",
            "volume":1634500,
            "ask":107.9,
            "oi":728000,
            "bid":107.5,
            "oichp":2.09,
            "strike_price":4300
},
        {
            "ltpchp":7.24,
            "symbol":"NSE:TCS24OCT4300CE",
            "ltpch":9.45,
            "option_type":"CE",
            "ltp":140,
            "oich":-29575,
            "prev_oi":890575,
            "fyToken":"1011241031103290",
            "volume":2801225,
            "ask":140.35,
            "oi":861000,
            "bid":139.8,
            "oichp":-3.32,
            "strike_price":4300
},
        {
            "ltpchp":7.92,
            "symbol":"NSE:TCS24OCT4350CE",
            "ltpch":8.45,
            "option_type":"CE",
            "ltp":115.1,
            "oich":187950,
            "prev_oi":248850,
            "fyToken":"1011241031126040",
            "volume":1814400,
            "ask":115.45,
            "oi":436800,
            "bid":115,
            "oichp":75.53,
            "strike_price":4350
},
        {
            "ltpchp":-9.73,
            "symbol":"NSE:TCS24OCT4350PE",
            "ltpch":-14.5,
            "option_type":"PE",
            "ltp":134.5,
            "oich":100975,
            "prev_oi":76475,
            "fyToken":"1011241031126043",
            "volume":597275,
            "ask":132.7,
            "oi":177450,
            "bid":132,
            "oichp":132.04,
            "strike_price":4350
        }
],
      "putOi":4182850,
      "expiryData":[
        {
            "date":"31-10-2024",
            "expiry":"1730368800"
},
        {
            "date":"28-11-2024",
            "expiry":"1732788000"
},
        {
            "date":"26-12-2024",
            "expiry":"1735207200"
        }
]
},
  "message":""
}
```

# Price Alerts
Price Alerts are user-configured triggers that notify when a stock or instrument reaches a specific price or meets certain market conditions.
Once the alert is triggered, you can get the notification in the General Websocket.

## Create Price Alert
You can create a new price alert using this API. Alerts can be configured based on the following parameters.

### Comparison Type

### Condition

### Alert Type

### Request Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;

public class App {
    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.CreatePriceAlert(fyersClass);
    }
    
    public void CreatePriceAlert(FyersClass fyersClass) {
        PriceAlertModel alert = new PriceAlertModel();
        alert.Agent = "fyers-api";
        alert.AlertType = 1;
        alert.Name = "gold alert";
        alert.Symbol = "NSE:GOLDBEES-EQ";
        alert.ComparisonType = "LTP";
        alert.Condition = "GT";
        alert.Value = 9999;
        alert.Notes = " Gold Alert";

        Tuple<JSONObject, JSONObject> result = fyersClass.CreatePriceAlert(alert);

        if (result.Item2() == null) {
            System.out.println("Price Alert Created: " + result.Item1());
        } else {
            System.out.println("Create Price Alert Error: " + result.Item2());
        }
    }
}
```

### Sample Success Response

```json
{
  "code": 120,
  "message": "A price alert for NSE:SILVERMIC25DECFUT at ₹45 is created.",
  "s": "ok"
}
```

## Get Price Alerts
You can retrieve alerts using:
Alert Name
Alert Id
This returns both active and triggered alerts.

### Response Attributes

### alert Object Fields

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;

public class App {
    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;

         App app = new App();
         app.GetPriceAlerts(fyersClass);

    }

    public void GetPriceAlerts(FyersClass fyersClass) {
        Tuple<JSONObject, JSONObject> result = fyersClass.GetPriceAlerts();
        if (result.Item2() == null) {
            System.out.println("Price Alerts: " + result.Item1());
        } else {
            System.out.println("Get Price Alerts Error: " + result.Item2());
        }
    } 
}
```

### Sample Success Response

```json
{
  "code": 200,
  "message": "",
  "data": {
    "5682484": {
      "fyToken": "10100000008080",
      "alert": {
        "comparisonType": "LTP",
        "condition": "LT",
        "name": "Silver alert ",
        "type": "V",
        "value": 130,
        "triggeredAt": "",
        "createdAt": "09-Dec-2025 11:49:06",
        "modifiedAt": "",
        "notes": "",
        "status": 2,
        "triggeredEpoch": 0,
        "createdEpoch": 1765280946,
        "modifiedEpoch": 0
    },
      "symbol": "NSE:SILVERBEES-EQ"
    }
    },
  "s": "ok"
}
```

## Modify Price Alert
Allows you to modify alerts.
Only active alerts can be modified.
Expired or triggered alerts cannot be modified.
The symbol associated with an alert cannot be changed; all other fields can be updated.

### Request Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;

public class App {
    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.UpdatePriceAlert(fyersClass);
    }

    public void UpdatePriceAlert(FyersClass fyersClass) {
      PriceAlertModel alert = new PriceAlertModel();
      alert.AlertId = "5397131"; // Replace with actual alert ID
      alert.Agent = "fyers-api";
      alert.AlertType = 1;
      alert.Name = "NSE:SILVERMIC25DECFUT";
      alert.Symbol = "NSE:SILVERMIC25DECFUT";
      alert.ComparisonType = "LTP";
      alert.Condition = "LTE";
      alert.Value = 50;

      Tuple<JSONObject, JSONObject> result = fyersClass.UpdatePriceAlert(alert);

      if (result.Item2() == null) {
          System.out.println("Price Alert Updated: " + result.Item1());
      } else {
          System.out.println("Update Price Alert Error: " + result.Item2());
      }
  }
}
```

### Sample Success Response

```json
{
  "code": 123,
  "message": "A price alert for NSE:SILVERMIC25DECFUT at ₹50 is updated.",
  "s": "ok"
}
```

## Delete Price Alert
Delete a price alert.

### Request Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;

public class App {
    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.DeletePriceAlert(fyersClass);
    }

    public void DeletePriceAlert(FyersClass fyersClass) {
        String alertId = "539713"; // Replace with actual alert ID
        Tuple<JSONObject, JSONObject> result = fyersClass.DeletePriceAlert(alertId);

        if (result.Item2() == null) {
            System.out.println("Price Alert Deleted: " + result.Item1());
} else {
            System.out.println("Delete Price Alert Error: " + result.Item2());
        }
    }
}
```

### Sample Success Response

```json
{
  "code": 121,
  "message": "A price alert for NSE:GOLDBEES-EQ at ₹185 is deleted.",
  "s": "ok"
}
```

## Enable/Disable Price Alert
This API allows you to change the alert state between enabled and disabled.
Only active alerts are allowed to change state; expired or triggered alert states cannot be changed.

### Request Attributes

### Request samples

```java
package com.example;

import org.json.JSONObject;
import com.tts.in.model.FyersClass;

public class App {
    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "XXXXXXXXXXXXXXXXXXXXXXXXXXX";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.ToggleAlert(fyersClass);   
    }

    public void ToggleAlert(FyersClass fyersClass) {
        String alertId = "6249070";
        Tuple<JSONObject, JSONObject> result = fyersClass.ToggleAlert(alertId);

        if (result.Item2() == null) {
            System.out.println("Alert Toggled: " + result.Item1());
} else {
            System.out.println("Toggle Alert Error: " + result.Item2());
        }
    }
}
```

### Sample Success Response

```json
{
  "code": 123,
  "message": "Your alert has been successfully enabled",
  "s": "ok"
}
```

Sample Success Response for Disabled Alert

```json
{
  "code": 123,
  "message": "Your alert has been successfully disabled",
  "s": "ok"
}
```

# Web Socket

## Introduction
The WebSocket provides a robust method for accessing real-time data or order updates seamlessly and with low latency. It enables developers and users to establish a persistent, bidirectional connection with the server, allowing them to receive continuous updates, such as symbol updates, depth updates or orderupdate. To enhance your experience with our WebSocket, here are some helpful tips and best practices
Subscription Limit: You have the flexibility to subscribe up to 5000 data subscriptions simultaneously via WebSocket with latest SDK versions, please refer Change Log. Staying within this limit ensures smooth subscription management without errors.
Single Instance: Keep in mind that you can create only one WebSocket connection instance at a time. This approach ensures stability and prevents issues that might arise from multiple concurrent connections.
Efficient Thread Management: WebSocket operates on a dedicated thread, allowing it to run independently of your main application thread. This design guarantees that your primary tasks can continue without interruptions from WebSocket operations.
Customizable Callback Functions: Tailor your application's behavior using callback functions provided by the WebSocket. These functions empower you to define specific actions for events like data updates or error occurrences.
Auto-Reconnect: Enjoy uninterrupted connectivity by enabling automatic reconnection in case of disconnection. Simply set the reconnect parameter to true during WebSocket initialization, ensuring your application can recover without manual intervention.You can set max reconnection count upto 50.
Logging to File: If you want to log data to a file, you can set the write_to_file parameter to true. This feature allows you to efficiently save received data to a log file for analysis or archival purposes. The write_to_file function will only work without callback functions.
Reconnect Retry: If you want to define dynamic retry count(max 50), you can set the reconnect_retry parameter to int value of number of times you want it to try reconnection.(In case of node fyersdata.autoreconnect(trycount))
Disable Logging(node JS): In case you want to disable logging use disable logging flag to disable logging sample format:
new FyersSocket("token","logpath",true/*flag to enable disable logging*/)

## Request samples

```java
FyersSocket client = new FyersSocket();
client.ReconnectAttemptsCount = 1;
client.webSocketDelegate = this;

await client.Connect();
client.ConnectHSM(ChannelModes.FULL);
```

## General Socket (orders)
The WebSocket API for receiving order, position, trade, price alerts, and EDIS updates is a real-time communication protocol designed to provide seamless access to various critical elements of a trading and EDIS system. This API allows traders and developers to establish a persistent, bidirectional connection with the server, enabling them to receive real-time updates on their orders, current positions, executed trades, price alerts, and EDIS status.

### Response attributes - For order updates

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        List<String> list = new ArrayList<>();
        list.add("orders");
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        fyersSocket.Connect();
        fyersSocket.Subscribe(list);

    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
"On Orders":{
  "orderDateTime":"27-Sep-2024 14:28:27",
  "remainingQuantity":1,
  "symbol":"NSE:IDEA-EQ",
  "side":1,
  "clientId":"YK04391",
  "description":"VODAFONE IDEA LIMITED",
  "instrument":0,
  "source":"W",
  "type":2,
  "fyToken":"101000000014366",
  "offlineOrder":false,
  "segment":10,
  "qty":1,
  "orderValidity":"DAY",
  "ex_sym":"IDEA",
  "exchange":10,
  "id":"24092700357413",
  "orderTag":"2:Untagged",
  "productType":"INTRADAY",
  "status":4,
  "id_fyers": "1b30241e-2819-4ec9-a3e4-69b6155cacab"
}
```

## General Socket (trades)

### Response attributes - For trade update

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        List<String> list = new ArrayList<>();
        list.add("trades");
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        fyersSocket.Connect();
        fyersSocket.Subscribe(list);

    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
"On Trades":{
  "orderDateTime":"27-09-2024 14:30:14",
  "symbol":"NSE:IDEA-EQ",
  "tradeNumber":"24092700359270-800241282",
  "orderType":2,
  "side":1,
  "clientId":"YK04391",
  "orderNumber":"24092700359270",
  "tradeValue":10.66,
  "fyToken":"101000000014366",
  "tradedQty":1,
  "exchangeOrderNo":"1400000000573625",
  "segment":10,
  "exchange":10,
  "orderTag":"2:Untagged",
  "tradePrice":10.66,
  "productType":"INTRADAY",
  "id_fyers": "1b30241e-2819-4ec9-a3e4-69b6155cacab"
}       
```

## General Socket (positions)

### Response attributes - For position updates

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        List<String> list = new ArrayList<>();
        list.add("positions");
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        fyersSocket.Connect();
        fyersSocket.Subscribe(list);

    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
"On Positions":{
    "symbol":"NSE:IDEA-EQ",
    "side":0,
    "qtyMulti_com":1,
    "netQty":0,
    "dayBuyQty":4,
    "rbiRefRate":1,
    "sellVal":42.61,
    "daySellQty":4,
    "sellAvg":10.6525,
    "cfBuyQty":0,
    "realized_profit":-0.03999999999999915,
    "sellQty":4,
    "buyAvg":10.6625,
    "netAvg":0,
    "fyToken":"101000000014366",
    "cfSellQty":0,
    "buyQty":4,
    "segment":10,
    "qty":0,
    "buyVal":42.65,
    "exchange":10,
    "id":"NSE:IDEA-EQ-INTRADAY",
    "productType":"INTRADAY"
  }  
```

## General Socket (general)
The WebSocket API for receiving order, position, trade, price alerts, and EDIS updates is a real-time communication protocol designed to provide seamless access to various critical elements of a trading and EDIS system. This API allows traders and developers to establish a persistent, bidirectional connection with the server, enabling them to receive real-time updates on their orders, current positions, executed trades, price alerts, and EDIS status.

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        List<String> list = new ArrayList<>();
        list.add("orders");
        list.add("trades");
        list.add("positions");
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        fyersSocket.Connect();
        fyersSocket.Subscribe(list);

    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
"On Trades":{
  "orderDateTime":"27-09-2024 14:33:43",
  "symbol":"NSE:IDEA-EQ",
  "tradeNumber":"24092700363934-800242611",
  "orderType":2,
  "side":1,
  "clientId":"YK04391",
  "orderNumber":"24092700363934",
  "tradeValue":10.64,
  "fyToken":"101000000014366",
  "tradedQty":1,
  "exchangeOrderNo":"1400000000576352",
  "segment":10,
  "exchange":10,
  "orderTag":"2:Untagged",
  "tradePrice":10.64,
  "productType":"INTRADAY",
  "id_fyers": "1b30241e-2819-4ec9-a3e4-69b6155cacab"
}
"On Orders":{
  "orderDateTime":"27-Sep-2024 14:33:43",
  "symbol":"NSE:IDEA-EQ",
  "side":1,
  "clientId":"YK04391",
  "limitPrice":10.64,
  "tradedPrice":10.64,
  "description":"VODAFONE IDEA LIMITED",
  "instrument":0,
  "source":"W",
  "type":2,
  "fyToken":"101000000014366",
  "offlineOrder":false,
  "segment":10,
  "qty":1,
  "orderValidity":"DAY",
  "exchOrdId":"1400000000576352",
  "ex_sym":"IDEA",
  "exchange":10,
  "id":"24092700363934",
  "orderTag":"2:Untagged",
  "filledQty":1,
  "productType":"INTRADAY",
  "status":2,
  "id_fyers": "1b30241e-2819-4ec9-a3e4-69b6155cacab"
}
"On Positions":{
  "symbol":"NSE:IDEA-EQ",
  "side":1,
  "qtyMulti_com":1,
  "netQty":1,
  "dayBuyQty":5,
  "rbiRefRate":1,
  "sellVal":42.61,
  "daySellQty":4,
  "sellAvg":10.6525,
  "cfBuyQty":0,
  "realized_profit":-0.021999999999998465,
  "sellQty":4,
  "buyAvg":10.658,
  "netAvg":10.658,
  "fyToken":"101000000014366",
  "cfSellQty":0,
  "buyQty":5,
  "segment":10,
  "qty":1,
  "buyVal":53.29,
  "exchange":10,
  "id":"NSE:IDEA-EQ-INTRADAY",
  "productType":"INTRADAY"
}
```

## Market Data Symbol Update
The WebSocket API for receiving stock data is a real-time communication protocol designed to provide seamless and low-latency access to live stock market data. This API allows developers and traders to establish a persistent, bidirectional connection with the server, enabling them to receive continuous updates on stock prices, trading volumes, and other relevant market information.
To quickly get started with the WebSocket API and start receiving real-time stock data, you can explore our sample scripts and code examples in our GitHub repository:
Data WebSocket Sample Scripts

### Response Attributes(Market Data Update)

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

import in.tts.hsjavalib.ChannelModes;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        List<String> scripList = new ArrayList<>();
        scripList.add("NSE:SBIN-EQ");
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        fyersSocket.ConnectHSM(ChannelModes.FULL);
        fyersSocket.SubscribeData(scripList);
    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
  {
    "vol_traded_today":"9791025",
    "symbol":"NSE:SBIN-EQ",
    "ask_size":"89",
    "type":"sf",
    "avg_trade_price":"803.83",
    "ask_price":"801.55",
    "tot_sell_qty":"1440165",
    "OI":"0",
    "last_traded_qty":"50",
    "low_price":"798.45",
    "open_price":"801.85",
    "lower_ckt":"72170",
    "turnover":"787031962575",
    "prev_close_price":"801.85",
    "ch":"-0.35",
    "high_price":"808.0",
    "exch_feed_time":"1727428144",
    "ltp":"801.5",
    "chp":"-0.04",
    "Yhigh":"83485",
    "Ylow":"54320",
    "bid_size":"13",
    "tot_buy_qty":"601871",
    "last_traded_time":"1727428144",
    "bid_price":"801.5",
    "upper_ckt":"88200"
  }
```

## Market Data Indices Update
The WebSocket API for receiving stock data is a real-time communication protocol designed to provide seamless and low-latency access to live stock market data. This API allows developers and traders to establish a persistent, bidirectional connection with the server, enabling them to receive continuous updates on stock prices, trading volumes, and other relevant market information.
To quickly get started with the WebSocket API and start receiving real-time stock data, you can explore our sample scripts and code examples in our GitHub repository:
Data WebSocket Sample Scripts

### Response Attributes(Index Update)

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

import in.tts.hsjavalib.ChannelModes;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        List<String> scripList = new ArrayList<>();
        scripList.add("NSE:NIFTY50-INDEX");
        fyersSocket.ConnectHSM(ChannelModes.FULL);
        fyersSocket.SubscribeData(scripList);
    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }

}
```

### Sample Success Response

```json
  {
    "symbol":"NSE:NIFTY50-INDEX",
    "ch":"-27.95",
    "high_price":"26277.35",
    "ltp":"26188.1",
    "exch_feed_time":"1727428424",
    "chp":"-0.11",
    "low_price":"26166.95",
    "open_price":"26248.25",
    "type":"if",
    "prev_close_price":"26216.05"
  }
```

## Market Data Depth Update
The WebSocket API for receiving stock data is a real-time communication protocol designed to provide seamless and low-latency access to live stock market data. This API allows developers and traders to establish a persistent, bidirectional connection with the server, enabling them to receive continuous updates on stock prices, trading volumes, and other relevant market information.
To quickly get started with the WebSocket API and start receiving real-time stock data, you can explore our sample scripts and code examples in our GitHub repository:
Data WebSocket Sample Scripts

### Response Attributes(Depth Update)

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

import in.tts.hsjavalib.ChannelModes;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        List<String> scripList = new ArrayList<>();
        scripList.add("NSE:SBIN-EQ");
        fyersSocket.ConnectHSM(ChannelModes.FULL);
        fyersSocket.SubscribeDepth(scripList);
    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
  {
    "symbol":"NSE:SBIN-EQ",
      "type":"dp",
      "bid_size5":"1862",
      "bid_size4":"2127",
      "bid_size3":"776",
      "bid_size2":"553",
      "bid_size1":"1",
      "ask_order4":"11",
      "ask_order5":"8",
      "bid_order3":"10",
      "ask_order2":"6",
      "bid_order2":"4",
      "ask_order3":"7",
      "bid_order1":"1",
      "ask_order1":"1",
      "bid_order5":"19",
      "bid_order4":"13",
      "bid_price1":"802.5",
      "bid_price2":"802.45",
      "bid_price5":"802.3",
      "ask_price4":"802.8",
      "ask_price3":"802.75",
      "bid_price3":"802.4",
      "bid_price4":"802.35",
      "ask_price5":"802.85",
      "ask_size1":"15",
      "ask_size2":"745",
      "ask_price2":"802.7",
      "ask_price1":"802.65",
      "ask_size5":"1106",
      "ask_size3":"368",
      "ask_size4":"3142"
    }
```

## Market Data Lite-Mode
The WebSocket API provides a lightweight and efficient "Lite Mode" for receiving only the Last Traded Price (LTP) updates of specific stock symbols. This mode is designed for users who require real-time access to LTP data without the additional overhead of subscribing to other stock data fields. The Lite Mode allows for seamless integration into applications where only the latest stock prices are needed.

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

import in.tts.hsjavalib.ChannelModes;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        List<String> scripList = new ArrayList<>();
        scripList.add("NSE:SBIN-EQ");
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;
        fyersSocket.ConnectHSM(ChannelModes.LITE);
        fyersSocket.SubscribeData(scripList);
    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
  {
    "symbol":"NSE:SBIN-EQ",
    "ch":"0.45",
    "ltp":"802.3",
    "chp":"0.06",
    "type":"sf"
  }
```

## Market Data Unsubscribe
To stop receiving real-time data updates for a specific stock symbol or a group of symbols, you can utilize the "unsubscribe" action in the WebSocket API. Sending an "unsubscribe" message will remove the specified symbol(s) from your active subscriptions, and you will no longer receive updates for those symbols. You can check the sample code to know how to unsubscribe to already subscribed symbols.

### Request samples

```java
package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.tts.in.model.FyersClass;
import com.tts.in.websocket.FyersSocket;
import com.tts.in.websocket.FyersSocketDelegate;

import in.tts.hsjavalib.ChannelModes;

public class App implements FyersSocketDelegate {

    public static void main(String[] args) {
        String clientID = "AAAAAAAA-100";
        String LiveToken = "eyJ...";

        FyersClass fyersClass = FyersClass.getInstance();
        fyersClass.clientId = clientID;
        fyersClass.accessToken = LiveToken;
        App app = new App();
        app.WebSocket();
    }

    public void WebSocket() {
        FyersSocket fyersSocket = new FyersSocket(3);
        fyersSocket.webSocketDelegate = this;

        List<String> scripList = new ArrayList<>();
        scripList.add("NSE:SBIN-EQ");
        
        fyersSocket.ConnectHSM(ChannelModes.FULL);
        fyersSocket.SubscribeData(scripList);

        try {
            TimeUnit.SECONDS.sleep(3);
            fyersSocket.UnSubscribeData(scripList);
            fyersSocket.CloseHSM();
    } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void OnIndex(JSONObject index) {
        System.out.println("On Index: " + index);
    }

    @Override
    public void OnScrips(JSONObject scrips) {
        System.out.println("On Scrips: " + scrips);
    }

    @Override
    public void OnDepth(JSONObject depths) {
        System.out.println("On Depth: " + depths);
    }

    @Override
    public void OnOrder(JSONObject orders) {
        System.out.println("On Orders: " + orders);
    }

    @Override
    public void OnTrade(JSONObject trades) {
        System.out.println("On Trades: " + trades);
    }

    @Override
    public void OnPosition(JSONObject positions) {
        System.out.println("On Positions: " + positions);
    }

    @Override
    public void OnOpen(String status) {
        System.out.println("On open: " + status);
    }

    @Override
    public void OnClose(String status) {
        System.out.println("On Close: " + status);
    }

    @Override
    public void OnError(JSONObject error) {
        System.out.println("On Error: " + error);
    }

    @Override
    public void OnMessage(JSONObject message) {
        System.out.println("OnMessage: " + message);
    }
}
```

### Sample Success Response

```json
 {
  "msg":"Unsubscribe successful",
  "type":"unsub",
  "message":"Successfully unsubscribed",
  "stCode":200
}
```

### Advanced Configuration
The WebSocket queue processing interval is an Optional setting that allows you to customize how frequently data in the subscription queue is handled. By default, this interval is set to 1 millisecond, but it can be adjusted to better suit your application's specific needs and performance preferences.
Customizable Interval: You can set the processing interval anywhere between 1ms to 2000ms (2 seconds), giving you flexibility to optimize performance and responsiveness.
Dynamic Adjustment: The interval setting can be changed at any time during an active WebSocket session.
Performance Optimization: Shorter intervals (1-10ms) are ideal for high-frequency trading scenarios that benefit from rapid data processing, while slightly longer intervals (100-500ms) work well for general market data streaming.
Resource Management: You can tune the interval based on how your application handles incoming data, helping to maintain smooth operation across different environments.

### Request samples

```java
const FyersSocket = require("fyers-api-v3").fyersDataSocket

var fyersdata= new FyersSocket("xxxxx-1xx:ey....","logpath",true/*flag to enable disable logging*/)

fyersdata.setQueueProcessInterval(200)  // 200ms - more reasonable interval for queue processing
fyersdata.connect()
```

# Order Websocket Usage Guide

## Introduction
The WebSocket API for receiving order, position, trade, price alerts, and EDIS updates is a real-time communication protocol designed to provide seamless access to various critical elements of a trading and EDIS system. This API allows traders and developers to establish a persistent, bidirectional connection with the server, enabling them to receive real-time updates on their orders, current positions, executed trades, price alerts, and EDIS status.
This guide provides instructions on integrating the order WebSocket into any programming language.

## Order WebSocket Connection
To connect to order websocket, the below two input params are mandate
Websocket endpoint : wss://socket.fyers.in/trade/v3
Header:
Format: < appId:accessToken >
Sample header format : 7ABXUX38S-100:eyJ0eXAi**********qiTnzd2lGwS17s

Based on the programming language chosen, respective socket connection libraries can be used.
For the reference please find the sample code for socket connection written in Python.
Here, we are making a connection with Fyers order websocket with parameters and callback functions on_message, on_error, on_close, on_open, which are required in the Python socket connection library used and would change as per the other programming library used. Sample callback function is shared below.
Note : Handle accordingly in your Programming language

### Connection Response:
On Success: Returns the socket object
On Failure:
Possible Error : Status code 403
Error: Handshake status 403 Forbidden -+-+- {'date': 'Tue, 19 Dec 2023 04:46:45 GMT', 'content-length': '0', 'connection': 'keep-alive', 'cf-cache-status': 'DYNAMIC', 'set-cookie': '__cf_bm=BOE16LGB7NHpNqW0AJOuFN1rcL3Q9TgnhmtpBfb3.Wk-1702961205-1- AfmECmK9cbVA2XGvkpx+jFuXyRsJET/ZOQYmw3LyZJ68pYLZTgtpbalvNs09ECZZ4GpPiogeYGhhFo+3PCp20nE=; path=/; expires=Tue, 19-Dec-23 05:16:45 GMT; domain=.fyers.in; HttpOnly; Secure', 'server': 'cloudflare', 'cf-ray': '837d00eec8ed17b6-MAA'} -+-+- b''
Reason : This error will come when your accessToken is wrong
How to solve : Provide correct accessToken
AccessToken format: < appID:accesstoken >

Error: Handshake status 404 Not Found -+-+- {'date': 'Tue, 19 Dec 2023 10:04:35 GMT', 'content-type': 'text/plain; charset=utf-8', 'content-length': '0', 'connection': 'keep-alive', 'cf-cache-status': 'DYNAMIC', 'set-cookie': '__cf_bm=I5oN6zdeKjfGsicqFiXZ57J5SX2IjsFDspaLIEGKPDE-1702980275-1-Ae+ldjrb3WfSuM7yNOzo3ykOIBQ1m+50QqcIqU26A+wqPIHhIEIGSy9kT2OG3OWNI0hwcmh7U+PnJ/aWWhz6fOA=; path=/; expires=Tue, 19-Dec-23 10:34:35 GMT; domain=.fyers.in; HttpOnly; Secure', 'server': 'cloudflare', 'cf-ray': '837ed28169c817ae-MAA'} -+-+- b''
Reason : Socket Connection URL would be wrong
How to solve : Provide valid URL.

### Sample callback function:
For more reference, please find our on_open callback function code for more reference

#### Request samples

```python

try:
if self.__ws_object is None:
if self.write_to_file:
        self.background_flag = True
    header = {"authorization": self.__access_token}
    ws = websocket.WebSocketApp(
self.__url,
        header=header,
        on_message=lambda ws, msg: self.__on_message(msg),
        on_error=lambda ws, msg: self.On_error(msg),
        on_close=lambda ws, close_code, close_reason: self.__on_close(
ws, close_code, close_reason
),
        on_open=lambda ws: self.__on_open(ws),
)
    self.t = Thread(target=ws.run_forever)
    self.t.daemon = self.background_flag
self.t.start()

#Sample callback function:

def __on_open(self, ws):
try:
if self.__ws_object is None:
        self.__ws_object = ws
        self.ping_thread = threading.Thread(target=self.__ping)
self.ping_thread.start()
except Exception as e:
self.order_logger.error(e)

        self.On_error(e)
```

#### Subscribe Method
Once the connection is established, it is required to subscribe for required actions to get the data. To subscribe for different actions, create a message data, which would be the string format for json node.

```python
message = json.dumps( {"T": "SUB_ORD", "SLIST": action_data, "SUB_T": 1} )
```

#### Json node Params:
T: Type: String
value: “SUB_ORD” (Fixed)
action_data: Type: List/Array
Value: ['orders', 'trades', 'positions', 'edis', 'pricealerts', 'login'] Note: Based on the list passed in action_data web_socket data will be received
SUB_T: Integer
Value: 1 (value 1 is for subscribing and -1 for unsubscribe)
Convert the json to string and send this message to socket to subscribe for action_data mentioned

#### Sample Response:
```json
{'code': 1605, 'message': 'Successfully subscribed', 's': 'ok'}
```

Response from socket on any action triggered
Once the subscribed successfully, for any action triggered, data will be received through socket, if any callback function is defined, would receive on function.
Response would be string, In node we get as array buffer and in python we string, then it parsed to required format. In another programming language you might get in another format you just have to change in string.

```json
Type: string
Value: {"orders":{"client_id":"XP0001","exchange":10,"fy_token":"10100000001628","id":"23121800292158","id_fyers":"df013f50-6925-4e2d-ba0f-0becf1229298","instrument":0,"lot_size":1,"offline_flag":false,"oms_flag":"K:1","ord_source":"W","ord_status":20,"ord_type":2,"ordertag":"2:Untagged","org_ord_status":4,"pan":"LVJPS3998E","precision":2,"price_multiplier":1,"product_type":"CNC","qty":1,"qty_multiplier":1,"qty_remaining":1,"report_type":"New Ack","segment":10,"status_msg":"New Ack","symbol":"NSE:BECTORFOOD-EQ","symbol_desc":"MRS BECTORS FOOD SPE LTD","symbol_exch":"BECTORFOOD", "tick_size":0.05,"time_epoch_oms":1702887690,"time_exch":"NA","time_oms":"18-Dec-2023 13:51:30","tran_side":1,"update_time_epoch_oms":1702887690,"update_time_exch":"01-Jan-1970 05:30:00","validity":"DAY"},"s":"ok"}
```

#### Note:
Above response would be in the string format (In Python ) and arraybuffer (In Node). You have to check in which format you are getting data from websocket as it is dependent on the websocket library for the particular language.
Once you get data, you have to change into the string.(if already in string format then no need to change). After that, you have to change this string to JSON. Here you find the following keys as a JSON Key (One of them ) : orders, trades, positions.
Now Based on the Key the data is identified, if key is orders it means it is order update message, if key is trades then this message is for trades updates and same for positions updates.
In an Fyers SDK the socket raw response is parsed to generate data with required keys and remove the unnecessary keys

```json
Parsed Data: {'s': 'ok', 'orders': {'clientId': 'XP03754', 'id': '23121800388066', 'qty': 1, 'remainingQuantity': 1, 'type': 2, 'fyToken': '10100000002705', 'exchange': 10, 'segment': 10, 'symbol': 'NSE:PRAJIND-EQ', 'instrument': 0, 'offlineOrder': False, 'orderDateTime': '18-Dec-2023 16:33:24', 'orderValidity': 'DAY', 'productType': 'CNC', 'side': 1, 'status': 4, 'source': 'W', 'ex_sym': 'PRAJIND', 'description': 'PRAJ INDUSTRIES LTD', 'orderNumStatus': '23121800388066:4'}}
```

All the keys information for orders updates are available there : Link
All the keys information for Trades updates are available there : Link
All the keys information for positions updates are available there : Link

Also attaching our internal mapping for your reference, how we are changing the keys from raw data to final data.

```json
        "position_mapper" :
         {
                "symbol": "symbol",
                "id": "id",
                "buy_avg": "buyAvg",
                "buy_qty": "buyQty",
                "buy_val": "buyVal",
                "sell_avg": "sellAvg",
                "sell_qty": "sellQty",
                "sell_val": "sellVal",
                "net_avg": "netAvg",
                "net_qty": "netQty",
                "tran_side": "side",
                "qty": "qty",
                "product_type": "productType",
                "pl_realized": "realized_profit",
                "rbirefrate": "rbiRefRate",
                "fy_token": "fyToken",
                "exchange": "exchange",
                "segment": "segment",
                "day_buy_qty": "dayBuyQty",
                "day_sell_qty": "daySellQty",
                "cf_buy_qty": "cfBuyQty",
                "cf_sell_qty": "cfSellQty",
                "qty_multiplier": "qtyMulti_com",
                "pl_total": "pl",
                "cross_curr_flag": "crossCurrency",
                "pl_unrealized": "unrealized_profit"
},
          "order_mapper" :
        {
                "client_id":"clientId",
                "id":"id",
                "id_parent":"parentId",
                "id_exchange":"exchOrdId",
                "qty":"qty",
                "qty_remaining":"remainingQuantity",
                "qty_filled":"filledQty",
                "price_limit":"limitPrice",
                "price_stop":"stopPrice",
                "tradedPrice":"price_traded",
                "ord_type":"type",
                "fy_token":"fyToken",
                "exchange":"exchange",
                "segment":"segment",
                "symbol":"symbol",
                "instrument":"instrument",
                "oms_msg":"message",
                "offline_flag":"offlineOrder",
                "time_oms":"orderDateTime",
                "validity":"orderValidity",
                "product_type":"productType",
                "tran_side":"side",
                "org_ord_status":"status",
                "ord_source":"source",
                "symbol_exch":"ex_sym",
                "symbol_desc":"description"
},
            "trade_mapper" :
          {
                "id_fill": "tradeNumber",
                "id": "orderNumber",
                "qty_traded": "tradedQty",
                "price_traded": "tradePrice",
                "traded_val": "tradeValue",
                "product_type": "productType",
                "client_id": "clientId",
                "id_exchange": "exchangeOrderNo",
                "ord_type": "orderType",
                "tran_side": "side",
                "symbol": "symbol",
                "fill_time": "orderDateTime",
                "fy_token": "fyToken",
                "exchange": "exchange",
                "segment": "segment"
          }
```

#### Request samples
```python
def subscribe(self, data_type: str) -> None:
"""
Subscribes to real-time updates of a specific data type.
Args:
data_type (str): The type of data to subscribe to, such as orders, position, or holdings.
"""

try:
if self.__ws_object is not None:
        self.data_type = []
for elem in data_type.split(","):
if isinstance(self.socket_type[elem], list):
self.data_type.extend(self.socket_type[elem])
else:
self.data_type.append(self.socket_type[elem])

print("Data type is ", self.data_type)
print("Data type is ", type(self.data_type))

message = json.dumps(
    {"T": "SUB_ORD", "SLIST": self.data_type, "SUB_T": 1}
)
self.__ws_object.send(message)

except Exception as e:
self.order_logger.error(e)
UnSubscribe Method
To Unsubscribe for different actions, create a message data, which would be the string format for json node.

message = json.dumps( {"T": "SUB_ORD", "SLIST": action_data, "SUB_T": -1} )
```

Json node Params:
T: Type: String
value: “SUB_ORD” (Fixed)
action_data: Type: List/Array
Value: ['orders', 'trades', 'positions', 'edis', 'pricealerts', 'login'] Note: Based on the list passed in action_data web_socket data will be received
SUB_T: Integer
Value: -1 (value -1 is for unsubscribing and 1 for subscribe)
Convert the json to string and send this message to socket to subscribe for action_data mentioned

Request samples
```python
def unsubscribe(self, data_type: str) -> None:
"""
Unsubscribes from real-time updates of a specific data type.

Args:
data_type (str): The type of data to unsubscribe from, such as orders, position, holdings or general.

"""

try:
if self.__ws_object is not None:
      self.data_type = [
self.socket_type[(type)] for type in data_type.split(",")
]
      message = json.dumps(
{"T": "SUB_ORD", "SLIST": self.data_type, "SUB_T": -1}
)
self.__ws_object.send(message)

except Exception as e:
self.order_logger.error(e)
```

# Ping Method
To check whether the websocket connection is alive or not, we have to send a periodically “ping” message. If we get a pong from websocket, it means it is alive else dead. Find how we are doing in Python Code.
Here there is one while loop with sleep of 10 seconds and we send a “ping” message to websocket to know that websocket is alive or not.

## Request samples

```python
def __ping(self) -> None:
"""
Sends periodic ping messages to the server to maintain the WebSocket connection.
The method continuously sends "__ping" messages to the server at a regular interval
as long as the WebSocket connection is active.
"""

while (
self.__ws_object is not None
and self.__ws_object.sock
and self.__ws_object.sock.connected
):
self.__ws_object.send("ping")
      time.sleep(10)
```

# Mandatory fields

# Tick-by-Tick (TBT) Websocket Usage Guide

## Introduction
Tick-by-tick data is the most detailed market data, recording every trade and order book update in real-time. Each "tick" includes the price, volume, and timestamp of individual trades, as well as changes to buy and sell orders. This granular data is crucial for analyzing market microstructure, tracking order flow, and developing high-frequency trading strategies.

## Key Points:

Available for NFO and NSE Instruments Only
Tick-by-tick data is exclusively available only for NFO (NSE Futures & Options) and NSE (Equity) instruments.
Data Formats
Requests are sent in JSON format.
Responses are received in protobuf format (a compact, efficient data format).
Incremental Data Updates
Instead of sending the full market data repeatedly, the server only sends changes (differences) between the last data packet and the current one.
To get the complete picture, users must maintain previous data and apply these changes.
The official SDKs provided by Fyers will handle this process automatically.
Snapshot Data on New Subscriptions
When a user subscribes to tick-by-tick data, the first packet received is a snapshot, containing the full market data at that moment.
After this, all subsequent packets only contain updates (differences) that need to be applied to the snapshot for a real-time view.

## TBT WebSocket Connection [50 Market Depth]
Currently, these are the features available on the socket

## To connect to tbt websocket, the below input params are mandated
Websocket endpoint : wss://rtsocket-api.fyers.in/versova
Header:
Key: Authorization
Format: < appId:accessToken >
Sample header format : 7ABXUX38S-100:eyJ0eXAi**********qiTnzd2lGwS17s

## Request samples

```python
from fyers_apiv3.FyersWebsocket.tbt_ws import FyersTbtSocket, SubscriptionModes

def onopen():
"""
Callback function to subscribe to data type and symbols upon WebSocket connection.
"""
print("Connection opened")
# Specify the data type and symbols you want to subscribe to
    mode = SubscriptionModes.DEPTH
    Channel = '1'
# Subscribe to the specified symbols and data type
    symbols = ['NSE:NIFTY25MARFUT', 'NSE:BANKNIFTY25MARFUT']
    fyers.subscribe(symbol_tickers=symbols, channelNo=Channel, mode=mode)
    fyers.switchChannel(resume_channels=[Channel], pause_channels=[])
# Keep the socket running to receive real-time data
    fyers.keep_running()
def on_depth_update(ticker, message):
"""
Callback function to handle incoming messages from the FyersDataSocket WebSocket.
Parameters:
ticker (str): The ticker symbol of the received message.
message (Depth): The received message from the WebSocket.
"""
print("ticker", ticker)
print("depth response:", message)
print("total buy qty:", message.tbq)
print("total sell qty:", message.tsq)
print("bids:", message.bidprice)
print("asks:", message.askprice)
print("bidqty:", message.bidqty)
print("askqty:", message.askqty)
print("bids ord numbers:", message.bidordn)
print("asks ord numbers:", message.askordn)
print("issnapshot:", message.snapshot)
print("tick timestamp:", message.timestamp)

def onerror(message):
"""
Callback function to handle WebSocket errors.

Parameters:
message (dict): The error message received from the WebSocket.

"""
print("Error:", message)

def onclose(message):
"""
Callback function to handle WebSocket connection close events.
"""
print("Connection closed:", message)

def onerror_message(message):

"""
Callback function for error message events from the server
Parameters: message (dict): The error message received from the Server.
"""
print("Error Message:", message)

# Replace the sample access token with your actual access token obtained from Fyers

access_token = "XCXXXXXXM-100:eyJ0tHfZNSBoLo"

fyers = FyersTbtSocket(
    access_token=access_token,  # Your access token for authenticating with the Fyers API.
    write_to_file=False,        # A boolean flag indicating whether to write data to a log file or not.
    log_path="",                # The path to the log file if write_to_file is set to True (empty string means current directory).
    on_open=onopen,          # Callback function to be executed upon successful WebSocket connection.
    on_close=onclose,           # Callback function to be executed when the WebSocket connection is closed.
    on_error=onerror,           # Callback function to handle any WebSocket errors that may occur.
    on_depth_update=on_depth_update, # Callback function to handle depth-related events from the WebSocket
    on_error_message=onerror_message         # Callback function to handle server-related erros from the WebSocket.
)
# Establish a connection to the Fyers WebSocket
fyers.connect()

```

# Concept of channels
With the Tick-by-Tick (TBT) WebSocket, we are introducing the concept of channels. A channel acts as a logical grouping for different subscribed symbols, making it easier to manage data streams efficiently.
How Channels Work
When subscribing to market data, you need to specify both the symbols and a channel number.
Simply subscribing to a channel does not start the data stream—you must also resume the channel to begin receiving updates.

## Example Usage
Let’s say you organize your subscriptions as follows:
Channel 1: All Nifty-related symbols
Channel 2: All BankNifty-related symbols
Now, depending on what data you need, you can control the channels dynamically:
To receive only Nifty data → Pause Channel 2 and Resume Channel 1
To receive only BankNifty data → Pause Channel 1 and Resume Channel 2
To receive both Nifty and BankNifty data → Resume both channels
This approach provides greater flexibility and control over market data streaming, allowing you to filter and manage real-time data efficiently.

# Request Message Types

# Response Message Types
We use Protocol Buffers (protobuf) as the response format for all market data. The .proto file, which defines the data structure, is available at:
📌 Proto URL: https://public.fyers.in/tbtproto/1.0.0/msg.proto
Protobuf is a widely used data format, and compilers are available to generate code in different programming languages.
How to Install and Use Protobuf
Protobuf Compiler Installation: https://protobuf.dev/getting-started/
Using Protobuf with Python: https://protobuf.dev/reference/python/python-generated/
Using Protobuf with Node.js: https://www.npmjs.com/package/protobufjs
We have uploaded the compiled files also for python, nodejs, and golang. You can download the files from the below link: https://public.fyers.in/tbtproto/1.0.0/protogencode.zip
Copy the required file directly in your project and use it.

## Proto Versions and Links:

# Ratelimits
Following ratelimits apply for TBT Websocket:

# Appendix

## Fytoken

## Exchanges

## Segments

## Available Exchange-Segment Combinations

## Instrument Types

## Symbology Format

## Symbology Possible Values

## Product Types.

## Order Types.

## Order Status

## Order Sides

## Position Sides

## Holding Types

## Order Sources
