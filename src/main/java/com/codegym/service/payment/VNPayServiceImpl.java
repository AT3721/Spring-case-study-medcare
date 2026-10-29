package com.codegym.service.payment;

import com.codegym.config.VNPayConfig;
import com.codegym.dto.payment.VNPayCallbackResult;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.PaymentMethod;
import com.codegym.model.PaymentStatus;
import com.codegym.model.PaymentTransaction;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.repository.IPaymentTransactionRepository;
import com.codegym.service.auth.IEmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class VNPayServiceImpl implements IVNPayService {

    private final VNPayConfig vnPayConfig;
    private final IPaymentTransactionRepository paymentTransactionRepository;
    private final IAppointmentRepository appointmentRepository;
    private final IEmailService emailService;

    public VNPayServiceImpl(VNPayConfig vnPayConfig,
                            IPaymentTransactionRepository paymentTransactionRepository,
                            IAppointmentRepository appointmentRepository,
                            IEmailService emailService) {
        this.vnPayConfig = vnPayConfig;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.appointmentRepository = appointmentRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public String createPaymentUrl(Appointment appointment, HttpServletRequest request) {
        BigDecimal fee = appointment.getPaymentAmount();
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            if (appointment.getDoctor() != null && appointment.getDoctor().getConsultationFee() != null) {
                fee = appointment.getDoctor().getConsultationFee();
            } else {
                fee = BigDecimal.valueOf(300000);
            }
            appointment.setPaymentAmount(fee);
            appointmentRepository.save(appointment);
        }

        long vnpAmount = fee.multiply(BigDecimal.valueOf(100)).longValue();
        String txnRef = appointment.getBookingCode() + "_" + System.currentTimeMillis();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String vnpCreateDate = now.format(formatter);
        String vnpExpireDate = now.plusMinutes(15).format(formatter);

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", vnPayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", "Thanh toan phi kham MedCare - " + appointment.getBookingCode());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", VNPayConfig.getIpAddress(request));
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                try {
                    hashData.append(fieldName).append('=').append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()))
                            .append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    hashData.append(fieldName).append('=').append(fieldValue);
                    query.append(fieldName).append('=').append(fieldValue);
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnpSecureHash = VNPayConfig.hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setAppointment(appointment);
        transaction.setTxnRef(txnRef);
        transaction.setAmount(fee);
        transaction.setOrderInfo(vnpParams.get("vnp_OrderInfo"));
        transaction.setStatus(PaymentStatus.UNPAID);
        paymentTransactionRepository.save(transaction);

        return vnPayConfig.getPayUrl() + "?" + queryUrl;
    }

    @Override
    @Transactional
    public VNPayCallbackResult processReturn(Map<String, String> params) {
        String vnpSecureHash = params.get("vnp_SecureHash");
        Map<String, String> verifyParams = new HashMap<>(params);
        verifyParams.remove("vnp_SecureHashType");
        verifyParams.remove("vnp_SecureHash");

        String calculatedHash = VNPayConfig.hashAllFields(verifyParams, vnPayConfig.getHashSecret());
        if (vnpSecureHash == null || !vnpSecureHash.equalsIgnoreCase(calculatedHash)) {
            return new VNPayCallbackResult(false, "97", "Chữ ký bảo mật không hợp lệ (Invalid Checksum).");
        }

        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String cardType = params.get("vnp_CardType");
        String payDate = params.get("vnp_PayDate");

        Optional<PaymentTransaction> transactionOpt = paymentTransactionRepository.findByTxnRef(txnRef);
        if (transactionOpt.isEmpty()) {
            return new VNPayCallbackResult(false, "01", "Không tìm thấy giao dịch tương ứng với mã: " + txnRef);
        }

        PaymentTransaction transaction = transactionOpt.get();
        Appointment appointment = transaction.getAppointment();

        transaction.setResponseCode(responseCode);
        transaction.setTransactionNo(transactionNo);
        transaction.setBankCode(bankCode);
        transaction.setCardType(cardType);

        VNPayCallbackResult result = new VNPayCallbackResult();
        result.setTxnRef(txnRef);
        result.setTransactionNo(transactionNo);
        result.setBankCode(bankCode);
        result.setPayDate(payDate);
        result.setAmount(transaction.getAmount());
        result.setAppointment(appointment);
        result.setBookingCode(appointment.getBookingCode());
        result.setResponseCode(responseCode);

        if ("00".equals(responseCode)) {
            transaction.setStatus(PaymentStatus.PAID);
            transaction.setPaymentTime(LocalDateTime.now());

            appointment.setPaymentStatus(PaymentStatus.PAID);
            appointment.setPaymentMethod(PaymentMethod.VNPAY);
            appointment.setPaymentTransactionNo(transactionNo);
            appointment.setPaymentTime(LocalDateTime.now());
            if (appointment.getStatus() == AppointmentStatus.PENDING) {
                appointment.setStatus(AppointmentStatus.CONFIRMED);
            }
            appointmentRepository.save(appointment);
            paymentTransactionRepository.save(transaction);

            // Send confirmation email with QR code
            try {
                emailService.sendAppointmentConfirmationEmail(appointment);
            } catch (Exception ex) {
                // Log and don't fail transaction
            }

            result.setSuccess(true);
            result.setMessage("Giao dịch thanh toán thành công qua VNPay.");
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(transaction);

            result.setSuccess(false);
            result.setMessage(mapResponseCodeToMessage(responseCode));
        }

        return result;
    }

    private String mapResponseCodeToMessage(String responseCode) {
        if (responseCode == null) return "Giao dịch không thành công.";
        return switch (responseCode) {
            case "24" -> "Giao dịch bị hủy bởi khách hàng.";
            case "11" -> "Đã hết hạn chờ thanh toán.";
            case "51" -> "Tài khoản của quý khách không đủ số dư để thực hiện giao dịch.";
            case "65" -> "Tài khoản của quý khách đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Giao dịch không thành công do: Khách hàng nhập sai mật khẩu thanh toán quá số lần quy định.";
            default -> "Giao dịch thanh toán không thành công (Mã lỗi: " + responseCode + ").";
        };
    }
}
