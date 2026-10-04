package org.spring.passhalo.event.dto;

public record EventDashboardResponse(
    Long eventId,
    String eventName,
    int totalTickets,
    long totalBookings,
    long checkedInCount,
    long noShowCount,
    double attendanceRate,
    double estimatedBookingRevenue,
    int walkInCount,
    long totalAttendees,
    double totalRevenue,
    long checkedInCashCount,
    long checkedInCardCount,
    long checkedInUnrecordedCount,
    int walkInCashCount,
    int walkInCardCount,
    int walkInUnrecordedCount
) {
}
