package com.codegym.service.appointment;

public class AppointmentSlotUnavailableException extends RuntimeException {
    public AppointmentSlotUnavailableException() {
        super("Bác sĩ đã có lịch khám vào khung giờ này. Vui lòng chọn ca khác!");
    }
}
