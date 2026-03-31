package com.antigravity.bff.domain;

public class Alert {
    private String deviceId;
    private String reason;
    private double hr;
    private double spo2;

    public Alert() {
    }

    public Alert(String deviceId, String reason, double hr, double spo2) {
        this.deviceId = deviceId;
        this.reason = reason;
        this.hr = hr;
        this.spo2 = spo2;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getHr() {
        return hr;
    }

    public void setHr(double hr) {
        this.hr = hr;
    }

    public double getSpo2() {
        return spo2;
    }

    public void setSpo2(double spo2) {
        this.spo2 = spo2;
    }
}
