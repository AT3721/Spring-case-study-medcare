package com.codegym.service.payment;

import com.codegym.config.VNPayConfig;
import com.codegym.dto.payment.VNPayCallbackResult;
import com.codegym.model.*;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IPaymentTransactionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VNPayServiceImplTest {

    @Mock
    private VNPayConfig vnPayConfig;

    @Mock
    private IPaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private IAppointmentRepository appointmentRepository;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private VNPayServiceImpl vnPayService;

    private final String secretKey = "XNBCJFAKAZQSGTARRLGCHVZWCIOIGSHN";

    @BeforeEach
    void setUp() {
        lenient().when(vnPayConfig.getPayUrl()).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        lenient().when(vnPayConfig.getReturnUrl()).thenReturn("http://localhost:8080/payment/vnpay-return");
        lenient().when(vnPayConfig.getTmnCode()).thenReturn("CGXZLS0Z");
        lenient().when(vnPayConfig.getHashSecret()).thenReturn(secretKey);
        lenient().when(vnPayConfig.getVersion()).thenReturn("2.1.0");
        lenient().when(vnPayConfig.getCommand()).thenReturn("pay");
    }

    @Test
    void createPaymentUrlGeneratesValidSignedUrlAndSavesTransaction() {
        Appointment appointment = new Appointment();
        appointment.setBookingCode("MC-TEST-001");
        Doctor doctor = new Doctor();
        doctor.setConsultationFee(BigDecimal.valueOf(350000));
        appointment.setDoctor(doctor);
        appointment.setPaymentAmount(BigDecimal.valueOf(350000));

        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String paymentUrl = vnPayService.createPaymentUrl(appointment, request);

        assertNotNull(paymentUrl);
        assertTrue(paymentUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(paymentUrl.contains("vnp_Amount=35000000")); // 350,000 * 100
        assertTrue(paymentUrl.contains("vnp_TmnCode=CGXZLS0Z"));
        assertTrue(paymentUrl.contains("vnp_SecureHash="));

        verify(paymentTransactionRepository, times(1)).save(any(PaymentTransaction.class));
    }

    @Test
    void processReturnWithValidHashAndSuccessCodeMarksAppointmentAsPaidAndConfirmed() {
        String txnRef = "MC-TEST-001_123456789";
        Appointment appointment = new Appointment();
        appointment.setBookingCode("MC-TEST-001");
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setPaymentStatus(PaymentStatus.UNPAID);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTxnRef(txnRef);
        transaction.setAppointment(appointment);
        transaction.setAmount(BigDecimal.valueOf(300000));
        transaction.setStatus(PaymentStatus.UNPAID);

        when(paymentTransactionRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(transaction));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "CGXZLS0Z");
        params.put("vnp_Amount", "30000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan phi kham MedCare");
        params.put("vnp_PayDate", "20261027110000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_TxnRef", txnRef);

        String secureHash = VNPayConfig.hashAllFields(params, secretKey);
        params.put("vnp_SecureHash", secureHash);

        VNPayCallbackResult result = vnPayService.processReturn(params);

        assertTrue(result.isSuccess());
        assertEquals("00", result.getResponseCode());
        assertEquals(PaymentStatus.PAID, appointment.getPaymentStatus());
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        assertEquals(PaymentMethod.VNPAY, appointment.getPaymentMethod());
        assertEquals("14567890", appointment.getPaymentTransactionNo());
        assertEquals(PaymentStatus.PAID, transaction.getStatus());

        verify(appointmentRepository, times(1)).save(appointment);
        verify(paymentTransactionRepository, times(1)).save(transaction);
    }

    @Test
    void processReturnWithFailedCodeMarksTransactionAsFailed() {
        String txnRef = "MC-TEST-002_987654321";
        Appointment appointment = new Appointment();
        appointment.setBookingCode("MC-TEST-002");
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setPaymentStatus(PaymentStatus.UNPAID);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTxnRef(txnRef);
        transaction.setAppointment(appointment);
        transaction.setAmount(BigDecimal.valueOf(300000));

        when(paymentTransactionRepository.findByTxnRef(txnRef)).thenReturn(Optional.of(transaction));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", "CGXZLS0Z");
        params.put("vnp_Amount", "30000000");
        params.put("vnp_ResponseCode", "24"); // Khách hàng hủy giao dịch
        params.put("vnp_TxnRef", txnRef);

        String secureHash = VNPayConfig.hashAllFields(params, secretKey);
        params.put("vnp_SecureHash", secureHash);

        VNPayCallbackResult result = vnPayService.processReturn(params);

        assertFalse(result.isSuccess());
        assertEquals("24", result.getResponseCode());
        assertEquals(PaymentStatus.FAILED, transaction.getStatus());
        assertEquals(PaymentStatus.UNPAID, appointment.getPaymentStatus()); // appointment remains unpaid
        assertTrue(result.getMessage().contains("hủy bởi khách hàng"));

        verify(paymentTransactionRepository, times(1)).save(transaction);
        verify(appointmentRepository, never()).save(appointment);
    }

    @Test
    void processReturnWithInvalidSignatureRejects() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "MC-TEST-003");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "INVALIDSIGNATURE123456");

        VNPayCallbackResult result = vnPayService.processReturn(params);

        assertFalse(result.isSuccess());
        assertEquals("97", result.getResponseCode());
        verify(paymentTransactionRepository, never()).findByTxnRef(anyString());
    }
}
