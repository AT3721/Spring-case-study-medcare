package com.codegym.controller.payment;

import com.codegym.dto.payment.VNPayCallbackResult;
import com.codegym.model.*;
import com.codegym.service.payment.PaymentWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentWorkflowService paymentWorkflowService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PaymentController paymentController;

    private Appointment appointment;

    @BeforeEach
    void setUp() {
        appointment = new Appointment();
        appointment.setId(10L);
        appointment.setBookingCode("MC-PAY-TEST");
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setPaymentStatus(PaymentStatus.UNPAID);
        appointment.setPaymentAmount(BigDecimal.valueOf(300000));
    }

    @Test
    void payAppointmentRedirectsToVNPayUrlWhenAppointmentIsValid() {
        when(paymentWorkflowService.createPaymentUrl(eq("MC-PAY-TEST"), isNull(), any()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1");

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String view = paymentController.payAppointment("MC-PAY-TEST", null, request, redirectAttributes);

        assertEquals("redirect:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1", view);
        verify(paymentWorkflowService).createPaymentUrl("MC-PAY-TEST", null, request);
    }

    @Test
    void payAppointmentRedirectsToAppointmentsWhenAlreadyPaid() {
        when(paymentWorkflowService.createPaymentUrl(eq("MC-PAY-TEST"), isNull(), any()))
                .thenThrow(new IllegalArgumentException("Lịch hẹn này đã được thanh toán trước đó."));

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String view = paymentController.payAppointment("MC-PAY-TEST", null, request, redirectAttributes);

        assertEquals("redirect:/patient/appointments", view);
        verify(paymentWorkflowService).createPaymentUrl("MC-PAY-TEST", null, request);
    }

    @Test
    void vnpayReturnProcessesParametersAndReturnsResultView() {
        Map<String, String[]> paramMap = Map.of(
                "vnp_ResponseCode", new String[]{"00"},
                "vnp_TxnRef", new String[]{"MC-PAY-TEST_123"}
        );

        VNPayCallbackResult callbackResult = new VNPayCallbackResult(true, "00", "Thành công");
        callbackResult.setBookingCode("MC-PAY-TEST");
        callbackResult.setAmount(BigDecimal.valueOf(300000));
        when(paymentWorkflowService.processReturn(request)).thenReturn(callbackResult);

        Model model = new ConcurrentModel();
        String view = paymentController.vnpayReturn(request, model);

        assertEquals("payment/result", view);
        assertTrue((Boolean) model.getAttribute("success"));
        assertEquals("MC-PAY-TEST", model.getAttribute("bookingCode"));
    }
}
