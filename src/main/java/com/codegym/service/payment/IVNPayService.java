package com.codegym.service.payment;

import com.codegym.dto.payment.VNPayCallbackResult;
import com.codegym.model.Appointment;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface IVNPayService {
    String createPaymentUrl(Appointment appointment, HttpServletRequest request);
    VNPayCallbackResult processReturn(Map<String, String> params);
}
