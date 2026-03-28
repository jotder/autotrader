# Installation Guide

This guide provides step-by-step instructions to set up the **PersonalTradeAssistant** (AutoTrader) on your local machine.

## 1. Prerequisites

Ensure you have the following installed:

- **JDK 25+**: Required for the backend (Spring Boot 3.4.4).
- **Maven 3.9+**: Required for building the backend.
- **Node.js (v20+) & npm**: Required for the frontend.
- **Angular CLI**: Install globally via `npm install -g @angular/cli`.
- **Fyers API App**: You need a developer account and an app created at [myapi.fyers.in](https://myapi.fyers.in/docsv3).

## 2. Environment Configuration

The system requires two environment files in the root directory:

### `.env` (Trading & Broker Configuration)
Create a `.env` file by copying the example (if available) or creating a new one:

```properties
# === Fyers API Credentials ===
FYERS_APP_ID=your_app_id
FYERS_SECRET_KEY=your_secret
FYERS_REDIRECT_URI=https://your-redirect-uri
FYERS_AUTH_CODE=your_auth_code

# === System Settings ===
FYERS_SYMBOLS=NSE:SBIN-EQ,NSE:RELIANCE-EQ,NSE:TCS-EQ
APP_ENV=paper                    # backtest | paper | live
LOG_LEVEL=INFO
PORT=7777
```

### `APIs.env` (Agent & AI Configuration)
This file is used for AI-driven development and research features.

```properties
OPENROUTER_API_KEY=your_api_key_here
```

## 3. Backend Setup

1. **Clean and Install Dependencies**:
   ```bash
   mvn clean install
   ```

2. **Verify Installation**:
   Run the tests to ensure everything is configured correctly:
   ```bash
   mvn test
   ```

## 4. Frontend Setup

1. **Navigate to UI Directory**:
   ```bash
   cd web-ui
   ```

2. **Install Dependencies**:
   ```bash
   npm install
   ```

## 5. Directory Structure Verification

Ensure the following directories exist (they are typically created automatically or managed via Git):
- `config/`: Contains YAML strategy and symbol configurations.
- `data/`: Contains CSV master data and trade journals.
- `logs/`: Application logs.

---
For operational instructions, refer to [OPERATION.md](./OPERATION.md).
