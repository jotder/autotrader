package com.rj.model;

import org.json.JSONObject;

public class ClientProfile {

    private final String fyId;
    private final String name;
    private final String displayName;
    private final String emailId;
    private final String mobileNumber;
    private final String pan;
    private final String image;
    private final boolean totp;
    private final boolean ddpiEnabled;
    private final boolean mtfEnabled;
    private final String pinChangeDate;
    private final String pwdChangeDate;
    private final int pwdToExpire;

    private ClientProfile(Builder b) {
        this.fyId = b.fyId;
        this.name = b.name;
        this.displayName = b.displayName;
        this.emailId = b.emailId;
        this.mobileNumber = b.mobileNumber;
        this.pan = b.pan;
        this.image = b.image;
        this.totp = b.totp;
        this.ddpiEnabled = b.ddpiEnabled;
        this.mtfEnabled = b.mtfEnabled;
        this.pinChangeDate = b.pinChangeDate;
        this.pwdChangeDate = b.pwdChangeDate;
        this.pwdToExpire = b.pwdToExpire;
    }

    public static ClientProfile from(JSONObject profile) {
        return new Builder()
                .fyId(profile.optString("fy_id", ""))
                .name(profile.optString("name", ""))
                .displayName(profile.optString("display_name", ""))
                .emailId(profile.optString("email_id", ""))
                .mobileNumber(profile.optString("mobile_number", ""))
                .pan(profile.optString("PAN", ""))
                .image(profile.optString("image", null))
                .totp(profile.optBoolean("totp", false))
                .ddpiEnabled(profile.optBoolean("ddpi_enabled", false))
                .mtfEnabled(profile.optBoolean("mtf_enabled", false))
                .pinChangeDate(profile.optString("pin_change_date", ""))
                .pwdChangeDate(profile.optString("pwd_change_date", ""))
                .pwdToExpire(profile.optInt("pwd_to_expire", 0))
                .build();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getFyId() {
        return fyId;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmailId() {
        return emailId;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPan() {
        return pan;
    }

    public String getImage() {
        return image;
    }

    public boolean isTotp() {
        return totp;
    }

    public boolean isDdpiEnabled() {
        return ddpiEnabled;
    }

    public boolean isMtfEnabled() {
        return mtfEnabled;
    }

    public String getPinChangeDate() {
        return pinChangeDate;
    }

    public String getPwdChangeDate() {
        return pwdChangeDate;
    }

    public int getPwdToExpire() {
        return pwdToExpire;
    }

    @Override
    public String toString() {
        return "ClientProfile{" +
                "fyId='" + fyId + '\'' +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", emailId='" + emailId + '\'' +
                ", mobileNumber='" + mobileNumber + '\'' +
                ", pan='" + pan + '\'' +
                ", totp=" + totp +
                ", ddpiEnabled=" + ddpiEnabled +
                ", mtfEnabled=" + mtfEnabled +
                ", pinChangeDate='" + pinChangeDate + '\'' +
                ", pwdChangeDate='" + pwdChangeDate + '\'' +
                ", pwdToExpire=" + pwdToExpire +
                '}';
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private String fyId = "";
        private String name = "";
        private String displayName = "";
        private String emailId = "";
        private String mobileNumber = "";
        private String pan = "";
        private String image = null;
        private boolean totp;
        private boolean ddpiEnabled;
        private boolean mtfEnabled;
        private String pinChangeDate = "";
        private String pwdChangeDate = "";
        private int pwdToExpire;

        public Builder fyId(String v) {
            fyId = v;
            return this;
        }

        public Builder name(String v) {
            name = v;
            return this;
        }

        public Builder displayName(String v) {
            displayName = v;
            return this;
        }

        public Builder emailId(String v) {
            emailId = v;
            return this;
        }

        public Builder mobileNumber(String v) {
            mobileNumber = v;
            return this;
        }

        public Builder pan(String v) {
            pan = v;
            return this;
        }

        public Builder image(String v) {
            image = v;
            return this;
        }

        public Builder totp(boolean v) {
            totp = v;
            return this;
        }

        public Builder ddpiEnabled(boolean v) {
            ddpiEnabled = v;
            return this;
        }

        public Builder mtfEnabled(boolean v) {
            mtfEnabled = v;
            return this;
        }

        public Builder pinChangeDate(String v) {
            pinChangeDate = v;
            return this;
        }

        public Builder pwdChangeDate(String v) {
            pwdChangeDate = v;
            return this;
        }

        public Builder pwdToExpire(int v) {
            pwdToExpire = v;
            return this;
        }

        public ClientProfile build() {
            return new ClientProfile(this);
        }
    }
}