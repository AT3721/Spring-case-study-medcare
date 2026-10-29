package com.codegym.dto.payment;

import com.codegym.model.Appointment;

import java.math.BigDecimal;

public class VNPayCallbackResult {
    private boolean success;
    private String responseCode;
    private String message;
    private String bookingCode;
    private String txnRef;
    private String transactionNo;
    private BigDecimal amount;
    private String bankCode;
    private String payDate;
    private Appointment appointment;

    public VNPayCallbackResult() {}

    public VNPayCallbackResult(boolean success, String responseCode, String message) {
        this.success = success;
        this.responseCode = responseCode;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getBookingCode() { return bookingCode; }
    public void setBookingCode(String bookingCode) { this.bookingCode = bookingCode; }

    public String getTxnRef() { return txnRef; }
    public void setTxnRef(String txnRef) { this.txnRef = txnRef; }

    public String getTransactionNo() { return transactionNo; }
    public void setTransactionNo(String transactionNo) { this.transactionNo = transactionNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode; }

    public String getPayDate() { return payDate; }
    public void setPayDate(String payDate) { this.payDate = payDate; }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
}
