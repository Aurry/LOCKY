package com.example.locky;

import java.util.Date;

public class Booking {

    private Date booked_date;
    private String booker;
    private Boolean collection_status;
    private String locker;
    private String receiver;

    public Booking() {}

    public Booking(Date booked_date, String booker, Boolean collection_status,
                   String locker, String receiver) {
    }

    public Date getBooked_date() {
        return booked_date;
    }

    public String getBooker() {
        return booker;
    }

    public Boolean getCollection_status() {
        return collection_status;
    }

    public String getLocker() {
        return locker;
    }

    public String getReceiver() {
        return receiver;
    }

}