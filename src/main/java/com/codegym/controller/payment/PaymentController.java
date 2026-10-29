package com.codegym.controller.payment;

import com.codegym.dto.payment.VNPayCallbackResult;
import com.codegym.service.payment.PaymentWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentWorkflowService paymentWorkflowService;

    public PaymentController(PaymentWorkflowService paymentWorkflowService) {
        this.paymentWorkflowService = paymentWorkflowService;
    }

    @GetMapping("/pay/{bookingCode}")
    public String payAppointment(@PathVariable String bookingCode,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            String paymentUrl = paymentWorkflowService.createPaymentUrl(bookingCode, authentication, request);
            return "redirect:" + paymentUrl;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("appointmentMessage", ex.getMessage());
            return "redirect:/patient/appointments";
        }
    }

    @GetMapping("/vnpay-return")
    public String vnpayReturn(HttpServletRequest request, Model model) {
        VNPayCallbackResult result = paymentWorkflowService.processReturn(request);

        model.addAttribute("result", result);
        model.addAttribute("success", result.isSuccess());
        model.addAttribute("message", result.getMessage());
        model.addAttribute("bookingCode", result.getBookingCode());
        model.addAttribute("txnRef", result.getTxnRef());
        model.addAttribute("transactionNo", result.getTransactionNo());
        model.addAttribute("amount", result.getAmount());
        model.addAttribute("bankCode", result.getBankCode());
        model.addAttribute("payDate", result.getPayDate());
        model.addAttribute("appointment", result.getAppointment());

        return "payment/result";
    }

    @GetMapping("/result")
    public String showResult() {
        return "payment/result";
    }
}
